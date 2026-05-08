package com.callcenter.app.util.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.callcenter.app.BuildConfig
import com.callcenter.app.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

/**
 * 音频能量分析结果
 */
data class AudioEnergyResult(
    /** 平均能量值 */
    val averageEnergy: Float,
    /** 能量标准差 */
    val energyStdDev: Float,
    /** 能量变化次数 */
    val energyChanges: Int,
    /** 分析的音频时长（毫秒） */
    val durationMs: Long,
    /** 检测到的模式 */
    val pattern: AudioEnergyPattern,
    /** 置信度 */
    val confidence: Float,
    /** 详细说明 */
    val reason: String,
    /** 音频文件路径（用于后续关键词识别） */
    val audioFilePath: String? = null
)

/**
 * 音频能量分析器
 *
 * 通过分析通话过程中的音频能量模式，判断是语音信箱还是真人接听：
 * - 语音信箱特征：能量平稳，单向播放
 * - 真人接听特征：能量波动，双向对话
 *
 * ## 重要说明：Android 通话录音限制
 *
 * Android 10+ 完全禁止第三方应用访问通话音频流（VOICE_CALL、VOICE_DOWNLINK 等）。
 * 这些音频源需要 CAPTURE_AUDIO_OUTPUT 权限，仅系统应用可用。
 *
 * **唯一可行的方案：MIC + 免提模式**
 * - 必须开启免提（扬声器）模式
 * - 使用 MIC 音频源捕获从扬声器播放的声音
 * - 录音质量取决于免提音量和环境噪音
 *
 * 备注：部分设备/ROM 在通话中使用 `VOICE_COMMUNICATION` 能获得更稳定的近似“通话场景”音频（含 AEC 等处理）。
 *
 * @see <a href="https://developer.android.com/guide/topics/media/mediarecorder">MediaRecorder</a>
 */
class AudioEnergyAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "AudioEnergyAnalyzer"

        // 音频配置
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2

        // 分析配置
        private const val ANALYSIS_INTERVAL_MS = 100L  // 每100ms采样一次能量
        private const val MIN_ANALYSIS_DURATION_MS = 2000L  // 最少分析2秒
        private const val MAX_ENERGY_SAMPLES = 600  // 最多600个样本（60秒）

        // 能量阈值（针对 MIC 免提模式调整）
        private const val SILENCE_THRESHOLD = 20f  // 大幅降低静音阈值，MIC 免提模式能量很低
        private const val SPEECH_THRESHOLD = 1000f
        private const val ENERGY_CHANGE_THRESHOLD = 0.25f
        private const val STEADY_PATTERN_THRESHOLD = 0.18f  // 语音信箱更平稳
        private const val FLUCTUATING_PATTERN_THRESHOLD = 0.30f

        // AMD（Answering Machine Detection）配置：哔声 + 连续语音
        private const val AMD_BEEP_MIN_CHECK_MS = 1500L
        private const val AMD_BEEP_CONSECUTIVE_FRAMES = 2
        private const val AMD_BEEP_RATIO_THRESHOLD = 0.35
        private const val AMD_BEEP_MIN_ENERGY = 25f
        private val AMD_BEEP_FREQS_HZ = intArrayOf(880, 900, 950, 1000, 1050)

        private const val AMD_CONTINUOUS_SPEECH_MS_THRESHOLD = 2500L
        private const val AMD_SPEECH_MIN_ENERGY = 60f

        // AMD 详细日志（仅 Debug 构建启用）
        private const val AMD_VERBOSE_LOG_INTERVAL_MS = 1000L
        private const val AMD_BEEP_CANDIDATE_LOG_THRESHOLD = 0.20
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)

    // 能量样本列表
    private val energySamples = mutableListOf<Float>()
    private var startTimeMs: Long = 0

    // 音频数据保存
    private var audioFile: File? = null
    private var fileOutputStream: FileOutputStream? = null
    private var saveAudioData: Boolean = false

    // 使用的音频源
    private var usedAudioSource: String = "无"

    // 日志前缀（用于把客户信息打到 analyzer 日志里）
    @Volatile private var logPrefix: String? = null

    // AMD 详细日志节流
    private var amdLastVerboseLogMs: Long = 0L

    /**
     * AMD（答录机检测）提示
     */
    data class AmdHint(
        val type: KeywordCallType,
        val confidence: Float,
        val reason: String
    )

    @Volatile private var amdHint: AmdHint? = null
    @Volatile private var amdBeepDetected: Boolean = false
    @Volatile private var amdBeepFrequencyHz: Int = 0
    @Volatile private var amdMaxBeepRatio: Double = 0.0
    @Volatile private var amdContinuousSpeechMs: Long = 0L
    private var amdBeepConsecutiveFrames: Int = 0
    private var amdStartTimeMs: Long = 0L

    /**
     * 设置日志前缀（建议传入类似："[客户=张三,电话=138****]"）。
     */
    fun setLogPrefix(prefix: String?) {
        logPrefix = prefix?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun dlog(message: String) {
        val p = logPrefix
        if (p.isNullOrBlank()) {
            DebugLogger.log(message)
        } else {
            DebugLogger.log("$p $message")
        }
    }

    /**
     * 检查是否有录音权限
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording.get()

    /**
     * 检查免提是否开启
     */
    fun isSpeakerphoneOn(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isSpeakerphoneOn
    }

    /**
     * 设置是否保存音频数据（用于关键词识别）
     */
    fun setSaveAudioData(save: Boolean) {
        saveAudioData = save
    }

    /**
     * 开始音频能量采集
     *
     * **重要**：必须先开启免提模式！否则无法捕获对方声音。
     *
     * @return 是否成功开始
     */
    suspend fun start(): Boolean {
        dlog("[AudioEnergy] start() 被调用")

        if (!hasRecordPermission()) {
            Log.w(TAG, "没有录音权限，无法进行音频能量分析")
            dlog("[AudioEnergy] ✗ 没有录音权限")
            return false
        }

        // 检查免提状态
        val speakerOn = isSpeakerphoneOn()
        dlog("[AudioEnergy] 免提状态: $speakerOn")

        if (!speakerOn) {
            Log.w(TAG, "免提未开启，录音效果可能不佳")
            dlog("[AudioEnergy] ⚠ 免提未开启，建议开启免提以获得更好的录音效果")
            // 不直接返回 false，允许继续尝试（某些设备可能仍然可以录音）
        }

        if (isRecording.getAndSet(true)) {
            Log.w(TAG, "音频能量采集已在进行中")
            dlog("[AudioEnergy] 音频采集已在进行中")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                ) * BUFFER_SIZE_FACTOR

                DebugLogger.log("[AudioEnergy] 缓冲区大小: $bufferSize bytes")

                // Android 10+ 禁止第三方应用访问 VOICE_CALL/VOICE_DOWNLINK 等通话音频流（需 CAPTURE_AUDIO_OUTPUT 系统权限）
                // 注意：VOICE_COMMUNICATION 可能启用 AEC（回声消除），会把“扬声器播放的远端声音”当作回声抑制掉，
                // 导致我们用于 AMD 的“抓远端声音”反而变弱。
                val audioSources = listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",  // 语音识别优化，推荐
                    MediaRecorder.AudioSource.MIC to "MIC",  // 基本麦克风
                    MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",  // 摄像机模式，某些设备可用
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",  // 通话场景优化（可能含 AEC）
                )

                dlog("[AudioEnergy] 开始尝试录音音源...")
                dlog("[AudioEnergy] 注意: Android 10+ 禁止使用 VOICE_CALL/VOICE_DOWNLINK")

                for ((source, name) in audioSources) {
                    try {
                        audioRecord = createAudioRecord(bufferSize, source)
                        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                            Log.d(TAG, "成功使用音源: $name")
                            dlog("[AudioEnergy] ✓ 成功使用音源: $name")
                            usedAudioSource = name
                            break
                        } else {
                            Log.d(TAG, "音源 $name 不可用")
                            dlog("[AudioEnergy] ✗ 音源 $name 不可用")
                            audioRecord?.release()
                            audioRecord = null
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "音源 $name 创建失败: ${e.message}")
                        dlog("[AudioEnergy] ✗ 音源 $name 创建失败: ${e.message}")
                        audioRecord = null
                    }
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 初始化失败，所有 MIC 音源都不可用")
                    dlog("[AudioEnergy] ✗ AudioRecord 初始化失败")
                    dlog("[AudioEnergy] 可能原因: 1.麦克风被其他应用占用 2.权限未授予 3.设备不支持")
                    isRecording.set(false)
                    return@withContext false
                }

                // 关闭可能影响“抓远端声音”的音频效果（尤其是 AEC 会压制扬声器回声=远端语音）
                runCatching {
                    val sessionId = audioRecord?.audioSessionId ?: 0
                    if (sessionId != 0) {
                        val aec = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId) else null
                        val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null
                        val agc = if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId) else null

                        aec?.enabled = false
                        ns?.enabled = false
                        agc?.enabled = false

                        dlog(
                            "[AudioEnergy] 音效: AEC=${aec?.enabled ?: "NA"}, NS=${ns?.enabled ?: "NA"}, AGC=${agc?.enabled ?: "NA"}"
                        )
                    }
                }

                // 清空之前的样本
                energySamples.clear()
                startTimeMs = System.currentTimeMillis()
                amdStartTimeMs = startTimeMs
                amdHint = null
                amdBeepDetected = false
                amdBeepFrequencyHz = 0
                amdMaxBeepRatio = 0.0
                amdContinuousSpeechMs = 0L
                amdBeepConsecutiveFrames = 0

                // 初始化音频文件（如果需要保存）
                if (saveAudioData) {
                    initAudioFile()
                    dlog("[AudioEnergy] 音频文件路径: ${audioFile?.absolutePath}")
                } else {
                    dlog("[AudioEnergy] 不保存音频数据 (saveAudioData=false)")
                }

                audioRecord?.startRecording()
                Log.d(TAG, "音频能量采集已开始，音源: $usedAudioSource，免提: $speakerOn")
                dlog("[AudioEnergy] ✓ 音频采集已开始")
                dlog("[AudioEnergy] 音源: $usedAudioSource, 免提: $speakerOn, 采样率: ${SAMPLE_RATE}Hz")

                if (BuildConfig.DEBUG) {
                    dlog(
                        "[AMDCore] 配置: minCheck=${AMD_BEEP_MIN_CHECK_MS}ms, consec=${AMD_BEEP_CONSECUTIVE_FRAMES}, " +
                            "ratioTh=${AMD_BEEP_RATIO_THRESHOLD}, minEnergy=${AMD_BEEP_MIN_ENERGY}, freqs=${AMD_BEEP_FREQS_HZ.joinToString()}, " +
                            "contSpeechTh=${AMD_CONTINUOUS_SPEECH_MS_THRESHOLD}ms, speechMinEnergy=${AMD_SPEECH_MIN_ENERGY}, silenceTh=${SILENCE_THRESHOLD}"
                    )
                }

                // 开始采集循环
                startSamplingLoop()

                true
            } catch (e: SecurityException) {
                Log.e(TAG, "录音权限被拒绝: ${e.message}")
                dlog("[AudioEnergy] ✗ SecurityException: ${e.message}")
                isRecording.set(false)
                false
            } catch (e: kotlinx.coroutines.CancellationException) {
                // CancellationException 是协程取消的正常情况（如语音信箱检测后挂断电话）
                Log.d(TAG, "音频采集被取消")
                dlog("[AudioEnergy] 音频采集被取消（正常情况）")
                isRecording.set(false)
                throw e  // 重新抛出，保持协程取消机制
            } catch (e: Exception) {
                Log.e(TAG, "启动音频采集失败: ${e.message}")
                dlog("[AudioEnergy] ✗ 启动异常: ${e.message}")
                isRecording.set(false)
                false
            }
        }
    }

    /**
     * 创建 AudioRecord
     */
    private fun createAudioRecord(bufferSize: Int, audioSource: Int): AudioRecord? {
        return try {
            AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "创建 AudioRecord 失败 (音源=$audioSource): ${e.message}")
            null
        }
    }

    /**
     * 初始化音频文件
     */
    private fun initAudioFile() {
        try {
            val audioDir = File(context.cacheDir, "call_audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            audioFile = File(audioDir, "call_${System.currentTimeMillis()}.pcm")
            fileOutputStream = FileOutputStream(audioFile)
            Log.d(TAG, "音频文件已创建: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "创建音频文件失败: ${e.message}")
            audioFile = null
            fileOutputStream = null
        }
    }

    /**
     * 获取当前已录制的 PCM 数据（用于实时识别）
     * 
     * @return 当前已录制的 PCM 数据，如果文件不存在或无法读取返回 null
     */
    fun getCurrentPcmData(): ByteArray? {
        if (!saveAudioData || audioFile == null || !audioFile!!.exists()) {
            return null
        }

        return try {
            // 读取文件当前内容
            audioFile!!.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "读取当前 PCM 数据失败: ${e.message}")
            null
        }
    }

    /**
     * 获取音频文件路径
     */
    fun getAudioFilePath(): String? = audioFile?.absolutePath

    /**
     * 采样循环
     */
    private suspend fun startSamplingLoop() {
        val buffer = ShortArray(SAMPLE_RATE / 10)  // 100ms 的数据
        var sampleCount = 0
        var totalReadCount = 0L
        var lastLogTime = System.currentTimeMillis()
        var lastFrameTime = System.currentTimeMillis()

        Log.d(TAG, "开始采样循环: bufferSize=${buffer.size}, saveAudioData=$saveAudioData")

        withContext(Dispatchers.IO) {
            while (isRecording.get() && isActive) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                totalReadCount++

                if (readCount > 0) {
                    // 计算 RMS 能量
                    val energy = calculateRmsEnergy(buffer, readCount)

                    // 估算本帧时长（避免依赖固定 sleep 或 buffer 时长）
                    val frameNow = System.currentTimeMillis()
                    val frameDurationMs = (frameNow - lastFrameTime).coerceIn(50L, 200L)
                    lastFrameTime = frameNow

                    // AMD：哔声 + 连续语音检测（提前给出 HUMAN/VOICEMAIL 提示）
                    updateAmdState(buffer, readCount, energy, frameDurationMs)

                    // 添加到样本列表
                    if (energySamples.size < MAX_ENERGY_SAMPLES) {
                        energySamples.add(energy)
                        sampleCount++
                    }

                    // 保存音频数据（如果需要）
                    if (saveAudioData && fileOutputStream != null) {
                        try {
                            // 将 Short 数组转换为 Byte 数组
                            val byteBuffer = ByteArray(readCount * 2)
                            for (i in 0 until readCount) {
                                byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                            }
                            fileOutputStream?.write(byteBuffer)
                        } catch (e: Exception) {
                            Log.e(TAG, "写入音频数据失败: ${e.message}")
                        }
                    }

                    // 每5秒打印一次状态
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 5000) {
                        Log.d(TAG, "采样状态: samples=$sampleCount, lastEnergy=$energy, totalReads=$totalReadCount, duration=${getDurationMs()}ms")
                        if (BuildConfig.DEBUG) {
                            val hint = amdHint
                            dlog(
                                "[AudioEnergy] 采样状态: samples=$sampleCount, lastEnergy=${String.format("%.1f", energy)}, reads=$totalReadCount, " +
                                    "duration=${getDurationMs()}ms, amdHint=${hint?.type ?: "null"}, beepDetected=$amdBeepDetected, contSpeechMs=$amdContinuousSpeechMs"
                            )
                        }
                        lastLogTime = now
                    }
                } else if (readCount < 0) {
                    Log.w(TAG, "AudioRecord.read 返回错误: $readCount")
                }

                // 短暂休眠
                Thread.sleep(ANALYSIS_INTERVAL_MS)
            }

            Log.d(TAG, "采样循环结束: totalSamples=$sampleCount, totalReads=$totalReadCount")
        }
    }

    /**
     * 计算 RMS 能量
     */
    private fun calculateRmsEnergy(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            sum += samples[i].toDouble() * samples[i].toDouble()
        }
        return Math.sqrt(sum / count).toFloat()
    }

    /**
     * 获取 AMD 提示（可能为 null）。
     *
     * 说明：该提示用于实时阶段快速区分“真人/语音信箱”，不会替代最终的能量模式分析。
     */
    fun getAmdHint(): AmdHint? = amdHint

    private fun updateAmdState(samples: ShortArray, count: Int, rmsEnergy: Float, frameDurationMs: Long) {
        // 已有最终结果时不再更新（VOICEMAIL 视为强信号锁定）
        val current = amdHint
        if (current?.type == KeywordCallType.VOICEMAIL) return

        val elapsedMs = System.currentTimeMillis() - amdStartTimeMs

        val now = System.currentTimeMillis()
        val shouldVerbose = BuildConfig.DEBUG && (now - amdLastVerboseLogMs >= AMD_VERBOSE_LOG_INTERVAL_MS)
        if (shouldVerbose) {
            amdLastVerboseLogMs = now
        }

        val beep = detectBeep(samples, count, rmsEnergy, verbose = shouldVerbose)
        if (beep != null && elapsedMs >= AMD_BEEP_MIN_CHECK_MS) {
            amdBeepConsecutiveFrames++
            if (beep.ratio > amdMaxBeepRatio) {
                amdMaxBeepRatio = beep.ratio
                amdBeepFrequencyHz = beep.frequencyHz
            }

            if (BuildConfig.DEBUG && (shouldVerbose || beep.ratio >= AMD_BEEP_CANDIDATE_LOG_THRESHOLD)) {
                dlog(
                    "[AMDCore] beepCandidate: elapsed=${elapsedMs}ms, freq=${beep.frequencyHz}Hz, ratio=${String.format("%.3f", beep.ratio)}, " +
                        "consec=${amdBeepConsecutiveFrames}/${AMD_BEEP_CONSECUTIVE_FRAMES}, maxRatio=${String.format("%.3f", amdMaxBeepRatio)}"
                )
            }

            if (!amdBeepDetected && amdBeepConsecutiveFrames >= AMD_BEEP_CONSECUTIVE_FRAMES) {
                amdBeepDetected = true
                amdHint = AmdHint(
                    type = KeywordCallType.VOICEMAIL,
                    confidence = 0.90f,
                    reason = "AMD(VOICE_COMMUNICATION)检测到哔声: ${amdBeepFrequencyHz}Hz ratio=${String.format("%.2f", amdMaxBeepRatio)}"
                )
                dlog("[AMD] ✓ ${amdHint?.reason}")
                return
            }
        } else {
            if (amdBeepConsecutiveFrames != 0 && BuildConfig.DEBUG && shouldVerbose) {
                dlog("[AMDCore] beepReset: elapsed=${elapsedMs}ms, reason=${if (beep == null) "noCandidate" else "minCheckNotReached"}")
            }
            amdBeepConsecutiveFrames = 0
        }

        // 连续语音：在未检测到哔声前，用连续说话时长作为“真人”强信号
        val speechEnergyThreshold = maxOf(AMD_SPEECH_MIN_ENERGY, SILENCE_THRESHOLD * 3f)
        val inSpeech = rmsEnergy >= speechEnergyThreshold
        if (inSpeech && !amdBeepDetected) {
            amdContinuousSpeechMs += frameDurationMs
            if (amdContinuousSpeechMs >= AMD_CONTINUOUS_SPEECH_MS_THRESHOLD) {
                amdHint = AmdHint(
                    type = KeywordCallType.HUMAN,
                    confidence = 0.80f,
                    reason = "AMD检测到连续语音>2.5s(${amdContinuousSpeechMs}ms)"
                )
                dlog("[AMD] ✓ ${amdHint?.reason}")
            }
        } else {
            // 遇到静音时重置连续计时（允许对话型波动重新累计）
            if (amdContinuousSpeechMs != 0L && BuildConfig.DEBUG && shouldVerbose) {
                dlog(
                    "[AMDCore] speechReset: elapsed=${elapsedMs}ms, rms=${String.format("%.1f", rmsEnergy)}, " +
                        "th=${String.format("%.1f", speechEnergyThreshold)}, beepDetected=$amdBeepDetected"
                )
            }
            amdContinuousSpeechMs = 0L
        }

        if (BuildConfig.DEBUG && shouldVerbose) {
            val hint = amdHint
            dlog(
                "[AMDCore] tick: elapsed=${elapsedMs}ms, frame=${frameDurationMs}ms, read=$count, " +
                    "rms=${String.format("%.1f", rmsEnergy)}, speechTh=${String.format("%.1f", speechEnergyThreshold)}, " +
                    "inSpeech=$inSpeech, contSpeechMs=$amdContinuousSpeechMs, beepDetected=$amdBeepDetected, " +
                    "beepConsec=$amdBeepConsecutiveFrames, hint=${hint?.type ?: "null"}"
            )
        }
    }

    private data class BeepCandidate(val frequencyHz: Int, val ratio: Double)

    private fun detectBeep(samples: ShortArray, count: Int, rmsEnergy: Float, verbose: Boolean): BeepCandidate? {
        // 太静时不做哔声判断，避免噪音/底噪误触
        if (rmsEnergy < AMD_BEEP_MIN_ENERGY) {
            if (BuildConfig.DEBUG && verbose) {
                dlog(
                    "[AMDCore] beepSkip: rms=${String.format("%.1f", rmsEnergy)} < minEnergy=${AMD_BEEP_MIN_ENERGY}"
                )
            }
            return null
        }

        // 总能量（用于归一化）
        var totalPower = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble()
            totalPower += v * v
        }
        if (totalPower <= 0.0) {
            if (BuildConfig.DEBUG && verbose) {
                dlog("[AMDCore] beepSkip: totalPower<=0")
            }
            return null
        }

        var bestFreq = 0
        var bestRatio = 0.0

        val ratios = if (BuildConfig.DEBUG && verbose) LinkedHashMap<Int, Double>() else null

        for (f in AMD_BEEP_FREQS_HZ) {
            val p = goertzelPower(samples, count, f.toDouble(), SAMPLE_RATE)
            val ratio = p / totalPower
            ratios?.put(f, ratio)
            if (ratio > bestRatio) {
                bestRatio = ratio
                bestFreq = f
            }
        }

        if (BuildConfig.DEBUG && verbose && ratios != null) {
            val top = ratios.entries.sortedByDescending { it.value }.take(3)
            dlog(
                "[AMDCore] beepScan: rms=${String.format("%.1f", rmsEnergy)}, best=${bestFreq}Hz/${String.format("%.3f", bestRatio)}, " +
                    "ratioTh=${AMD_BEEP_RATIO_THRESHOLD}, top=${top.joinToString { "${it.key}Hz=${String.format("%.3f", it.value)}" }}"
            )
        }

        return if (bestRatio >= AMD_BEEP_RATIO_THRESHOLD) {
            BeepCandidate(bestFreq, bestRatio)
        } else {
            null
        }
    }

    /**
     * Goertzel 算法：计算目标频点能量（功率）。
     */
    private fun goertzelPower(samples: ShortArray, count: Int, targetFreqHz: Double, sampleRate: Int): Double {
        val k = (0.5 + (count * targetFreqHz / sampleRate)).toInt()
        val w = 2.0 * Math.PI * k / count
        val cosine = cos(w)
        val sine = sin(w)
        val coeff = 2.0 * cosine

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until count) {
            q0 = coeff * q1 - q2 + samples[i].toDouble()
            q2 = q1
            q1 = q0
        }

        val real = q1 - q2 * cosine
        val imag = q2 * sine
        return real * real + imag * imag
    }

    /**
     * 停止采集并分析结果
     */
    suspend fun stopAndAnalyze(): AudioEnergyResult? {
        dlog("[AudioEnergy] stopAndAnalyze() 被调用")
        isRecording.set(false)

        return withContext(Dispatchers.IO) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                dlog("[AudioEnergy] AudioRecord 已停止并释放")
            } catch (e: Exception) {
                Log.e(TAG, "停止音频采集时出错: ${e.message}")
                dlog("[AudioEnergy] 停止音频采集出错: ${e.message}")
            }

            // 关闭文件
            try {
                fileOutputStream?.flush()
                fileOutputStream?.close()
                fileOutputStream = null
                dlog("[AudioEnergy] 音频文件已关闭")
            } catch (e: Exception) {
                Log.e(TAG, "关闭音频文件时出错: ${e.message}")
                dlog("[AudioEnergy] 关闭音频文件出错: ${e.message}")
            }

            val durationMs = System.currentTimeMillis() - startTimeMs
            dlog("[AudioEnergy] 采集时长: ${durationMs}ms")
            dlog("[AudioEnergy] 采集样本数: ${energySamples.size}")
            dlog("[AudioEnergy] 使用音源: $usedAudioSource")

            // 检查是否有足够的样本
            if (energySamples.isEmpty()) {
                Log.w(TAG, "没有采集到音频样本")
                dlog("[AudioEnergy] ✗ 没有采集到音频样本")
                return@withContext AudioEnergyResult(
                    averageEnergy = 0f,
                    energyStdDev = 0f,
                    energyChanges = 0,
                    durationMs = durationMs,
                    pattern = AudioEnergyPattern.UNKNOWN,
                    confidence = 0f,
                    reason = "没有采集到音频样本（音源: $usedAudioSource）",
                    audioFilePath = audioFile?.absolutePath
                )
            }

            if (durationMs < MIN_ANALYSIS_DURATION_MS) {
                Log.w(TAG, "分析时长不足: ${durationMs}ms < ${MIN_ANALYSIS_DURATION_MS}ms")
                dlog("[AudioEnergy] ✗ 分析时长不足: ${durationMs}ms < ${MIN_ANALYSIS_DURATION_MS}ms")
                return@withContext AudioEnergyResult(
                    averageEnergy = 0f,
                    energyStdDev = 0f,
                    energyChanges = 0,
                    durationMs = durationMs,
                    pattern = AudioEnergyPattern.UNKNOWN,
                    confidence = 0f,
                    reason = "分析时长不足（${durationMs}ms）",
                    audioFilePath = audioFile?.absolutePath
                )
            }

            analyzeEnergyPattern(durationMs)
        }
    }

    /**
     * 分析能量模式
     */
    private fun analyzeEnergyPattern(durationMs: Long): AudioEnergyResult {
        dlog("[AudioEnergy] 开始分析能量模式...")

        val samples = energySamples.toList()

        // 计算平均能量
        val averageEnergy = samples.average().toFloat()

        // 计算标准差
        val variance = samples.map { (it - averageEnergy) * (it - averageEnergy) }.average()
        val stdDev = Math.sqrt(variance).toFloat()

        // 计算归一化标准差（变异系数）
        val normalizedStdDev = if (averageEnergy > 0) stdDev / averageEnergy else 0f

        // 计算能量变化次数
        var energyChanges = 0
        for (i in 1 until samples.size) {
            val change = Math.abs(samples[i] - samples[i - 1]) / averageEnergy
            if (change > ENERGY_CHANGE_THRESHOLD) {
                energyChanges++
            }
        }

        // 计算变化率（每秒变化次数）
        val changeRate = energyChanges.toFloat() / (durationMs / 1000f)

        // 记录计算过程
        dlog("[AudioEnergy] 平均能量: $averageEnergy")
        dlog("[AudioEnergy] 标准差: $stdDev")
        dlog("[AudioEnergy] 归一化标准差: $normalizedStdDev")
        dlog("[AudioEnergy] 能量变化次数: $energyChanges")
        dlog("[AudioEnergy] 变化率: ${changeRate}次/秒")

        // 判断模式
        val (pattern, confidence, reason) = determinePattern(
            averageEnergy = averageEnergy,
            normalizedStdDev = normalizedStdDev,
            changeRate = changeRate,
            durationMs = durationMs,
            energyChanges = energyChanges,
            samples = samples
        )

        DebugLogger.log("[AudioEnergy] 模式判断结果: $pattern")
        DebugLogger.log("[AudioEnergy] 置信度: $confidence")
        DebugLogger.log("[AudioEnergy] 原因: $reason")

        Log.d(TAG, """
            音频能量分析结果:
            - 音源: $usedAudioSource
            - 平均能量: $averageEnergy
            - 标准差: $stdDev
            - 归一化标准差: $normalizedStdDev
            - 能量变化次数: $energyChanges
            - 变化率(次/秒): $changeRate
            - 模式: $pattern
            - 置信度: $confidence
            - 原因: $reason
            - 音频文件: ${audioFile?.absolutePath}
        """.trimIndent())

        return AudioEnergyResult(
            averageEnergy = averageEnergy,
            energyStdDev = stdDev,
            energyChanges = energyChanges,
            durationMs = durationMs,
            pattern = pattern,
            confidence = confidence,
            reason = reason,
            audioFilePath = audioFile?.absolutePath
        )
    }

    /**
     * 判断音频能量模式
     */
    private fun determinePattern(
        averageEnergy: Float,
        normalizedStdDev: Float,
        changeRate: Float,
        durationMs: Long,
        energyChanges: Int,
        samples: List<Float>
    ): Triple<AudioEnergyPattern, Float, String> {
        // 静音情况（针对 MIC 免提模式降低了阈值）
        if (averageEnergy < SILENCE_THRESHOLD) {
            return Triple(
                AudioEnergyPattern.UNKNOWN,
                0.3f,
                "音频能量过低（$averageEnergy < $SILENCE_THRESHOLD），可能是静音、免提未开或录音失败"
            )
        }

        // 平稳模式（语音信箱特征）
        // 语音信箱是单向播放，能量相对平稳
        if (normalizedStdDev < STEADY_PATTERN_THRESHOLD) {
            val confidence = when {
                normalizedStdDev < 0.10f -> 0.95f
                normalizedStdDev < 0.14f -> 0.85f
                else -> 0.75f
            }
            return Triple(
                AudioEnergyPattern.STEADY,
                confidence,
                "能量平稳（归一化标准差=${String.format("%.2f", normalizedStdDev)}），单向播放特征，可能是语音信箱"
            )
        }

        // 【新增】间歇性模式（真人应答等待回应）
        // 特征：单方说话 + 沉默等待，能量变化次数少但有明显的说话段
        if (energyChanges in 2..6 && changeRate < 1.5f && normalizedStdDev in 0.15f..0.35f) {
            // 检查是否有明显的说话段（连续高能量）
            val hasSpeechSegments = checkSpeechSegments(samples, averageEnergy)

            if (hasSpeechSegments) {
                val confidence = when {
                    energyChanges in 2..4 -> 0.70f  // 少量变化，可能是真人应答
                    energyChanges in 5..6 -> 0.65f  // 稍多变化
                    else -> 0.60f
                }
                return Triple(
                    AudioEnergyPattern.INTERMITTENT,
                    confidence,
                    "间歇性说话模式（变化次数=$energyChanges，变化率=${String.format("%.1f", changeRate)}/秒），单方说话特征，可能是真人接听等待回应"
                )
            }
        }

        // 波动模式（对话特征）
        // 真人对话有明显的能量波动（说话-静音-说话）
        if (normalizedStdDev > FLUCTUATING_PATTERN_THRESHOLD && changeRate > 1.5f) {
            val confidence = when {
                normalizedStdDev > 0.45f && changeRate > 2.5f -> 0.95f
                normalizedStdDev > 0.35f && changeRate > 2f -> 0.85f
                else -> 0.75f
            }
            return Triple(
                AudioEnergyPattern.FLUCTUATING,
                confidence,
                "能量波动明显（归一化标准差=${String.format("%.2f", normalizedStdDev)}，变化率=${String.format("%.1f", changeRate)}/秒），双向对话特征，可能是真人接听"
            )
        }

        // 中等波动
        return Triple(
            AudioEnergyPattern.MODERATE,
            0.5f,
            "能量中等波动（归一化标准差=${String.format("%.2f", normalizedStdDev)}），需结合其他因素判断"
        )
    }

    /**
     * 检查是否有明显的说话段
     * 说话段特征：连续多个样本能量较高
     */
    private fun checkSpeechSegments(samples: List<Float>, averageEnergy: Float): Boolean {
        if (samples.isEmpty()) return false

        val speechThreshold = averageEnergy * 1.5f  // 说话阈值：平均能量的1.5倍
        var consecutiveHighEnergy = 0
        var speechSegmentCount = 0

        for (sample in samples) {
            if (sample > speechThreshold) {
                consecutiveHighEnergy++
                // 连续3个高能量样本（约300ms）认为是说话段
                if (consecutiveHighEnergy >= 3) {
                    speechSegmentCount++
                    consecutiveHighEnergy = 0
                }
            } else {
                consecutiveHighEnergy = 0
            }
        }

        // 至少有1-2个明显的说话段
        return speechSegmentCount in 1..3
    }

    /**
     * 强制停止采集（不分析）
     */
    fun stop() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "强制停止音频采集时出错: ${e.message}")
        }
        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
            fileOutputStream = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭音频文件时出错: ${e.message}")
        }
    }

    /**
     * 获取当前采集的样本数量
     */
    fun getSampleCount(): Int = energySamples.size

    /**
     * 获取当前采集时长（毫秒）
     */
    fun getDurationMs(): Long {
        return if (startTimeMs > 0) {
            System.currentTimeMillis() - startTimeMs
        } else {
            0
        }
    }

    /**
     * 清理音频文件
     */
    fun cleanupAudioFile() {
        try {
            audioFile?.delete()
            audioFile = null
        } catch (e: Exception) {
            Log.e(TAG, "清理音频文件失败: ${e.message}")
        }
    }
}
