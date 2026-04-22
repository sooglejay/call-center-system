package com.callcenter.app.util.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

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
 * 同时保存音频数据用于后续关键词识别
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

        // 能量阈值
        private const val SILENCE_THRESHOLD = 500f
        private const val SPEECH_THRESHOLD = 2000f
        private const val ENERGY_CHANGE_THRESHOLD = 0.3f
        private const val STEADY_PATTERN_THRESHOLD = 0.15f
        private const val FLUCTUATING_PATTERN_THRESHOLD = 0.35f
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
     * 设置是否保存音频数据（用于关键词识别）
     */
    fun setSaveAudioData(save: Boolean) {
        saveAudioData = save
    }

    /**
     * 开始音频能量采集
     *
     * @return 是否成功开始
     */
    suspend fun start(): Boolean {
        if (!hasRecordPermission()) {
            Log.w(TAG, "没有录音权限，无法进行音频能量分析")
            return false
        }

        if (isRecording.getAndSet(true)) {
            Log.w(TAG, "音频能量采集已在进行中")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                ) * BUFFER_SIZE_FACTOR

                // 尝试多种音源，按优先级排列
                val audioSources = listOf(
                    MediaRecorder.AudioSource.VOICE_DOWNLINK to "VOICE_DOWNLINK",
                    MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
                    MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
                    MediaRecorder.AudioSource.MIC to "MIC",
                    MediaRecorder.AudioSource.DEFAULT to "DEFAULT"
                )

                for ((source, name) in audioSources) {
                    try {
                        audioRecord = createAudioRecord(bufferSize, source)
                        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                            Log.d(TAG, "成功使用音源: $name")
                            break
                        } else {
                            Log.d(TAG, "音源 $name 不可用")
                            audioRecord?.release()
                            audioRecord = null
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "音源 $name 创建失败: ${e.message}")
                        audioRecord = null
                    }
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 初始化失败，所有音源都不可用")
                    Log.e(TAG, "注意: VOICE_DOWNLINK/VOICE_CALL 需要 CAPTURE_AUDIO_OUTPUT 权限（仅系统应用）")
                    Log.e(TAG, "注意: 通话期间 MIC 可能被电话应用独占")
                    isRecording.set(false)
                    return@withContext false
                }

                // 清空之前的样本
                energySamples.clear()
                startTimeMs = System.currentTimeMillis()

                // 初始化音频文件（如果需要保存）
                if (saveAudioData) {
                    initAudioFile()
                }

                audioRecord?.startRecording()
                Log.d(TAG, "音频能量采集已开始，音源: ${getAudioSourceName()}")

                // 开始采集循环
                startSamplingLoop()

                true
            } catch (e: SecurityException) {
                Log.e(TAG, "录音权限被拒绝: ${e.message}")
                isRecording.set(false)
                false
            } catch (e: Exception) {
                Log.e(TAG, "启动音频采集失败: ${e.message}")
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
     * 获取当前使用的音源名称
     */
    private fun getAudioSourceName(): String {
        return when {
            audioRecord == null -> "无"
            else -> try {
                // 通过反射获取音源
                val field = AudioRecord::class.java.getDeclaredField("mAudioSource")
                field.isAccessible = true
                val source = field.getInt(audioRecord)
                when (source) {
                    MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
                    MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
                    MediaRecorder.AudioSource.MIC -> "MIC"
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                    else -> "UNKNOWN($source)"
                }
            } catch (e: Exception) {
                "UNKNOWN"
            }
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
     * 采样循环
     */
    private suspend fun startSamplingLoop() {
        val buffer = ShortArray(SAMPLE_RATE / 10)  // 100ms 的数据
        var sampleCount = 0
        var totalReadCount = 0L
        var lastLogTime = System.currentTimeMillis()

        Log.d(TAG, "开始采样循环: bufferSize=${buffer.size}, saveAudioData=$saveAudioData")

        withContext(Dispatchers.IO) {
            while (isRecording.get() && isActive) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                totalReadCount++

                if (readCount > 0) {
                    // 计算 RMS 能量
                    val energy = calculateRmsEnergy(buffer, readCount)

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
     * 停止采集并分析结果
     */
    suspend fun stopAndAnalyze(): AudioEnergyResult? {
        isRecording.set(false)

        return withContext(Dispatchers.IO) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            } catch (e: Exception) {
                Log.e(TAG, "停止音频采集时出错: ${e.message}")
            }

            // 关闭文件
            try {
                fileOutputStream?.flush()
                fileOutputStream?.close()
                fileOutputStream = null
            } catch (e: Exception) {
                Log.e(TAG, "关闭音频文件时出错: ${e.message}")
            }

            val durationMs = System.currentTimeMillis() - startTimeMs

            // 检查是否有足够的样本
            if (energySamples.isEmpty()) {
                Log.w(TAG, "没有采集到音频样本")
                return@withContext AudioEnergyResult(
                    averageEnergy = 0f,
                    energyStdDev = 0f,
                    energyChanges = 0,
                    durationMs = durationMs,
                    pattern = AudioEnergyPattern.UNKNOWN,
                    confidence = 0f,
                    reason = "没有采集到音频样本",
                    audioFilePath = audioFile?.absolutePath
                )
            }

            if (durationMs < MIN_ANALYSIS_DURATION_MS) {
                Log.w(TAG, "分析时长不足: ${durationMs}ms < ${MIN_ANALYSIS_DURATION_MS}ms")
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

        // 判断模式
        val (pattern, confidence, reason) = determinePattern(
            averageEnergy = averageEnergy,
            normalizedStdDev = normalizedStdDev,
            changeRate = changeRate,
            durationMs = durationMs
        )

        Log.d(TAG, """
            音频能量分析结果:
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
        durationMs: Long
    ): Triple<AudioEnergyPattern, Float, String> {
        // 静音情况
        if (averageEnergy < SILENCE_THRESHOLD) {
            return Triple(
                AudioEnergyPattern.UNKNOWN,
                0.3f,
                "音频能量过低，可能是静音或录音失败"
            )
        }

        // 平稳模式（语音信箱特征）
        if (normalizedStdDev < STEADY_PATTERN_THRESHOLD) {
            val confidence = when {
                normalizedStdDev < 0.08f -> 0.95f
                normalizedStdDev < 0.12f -> 0.85f
                else -> 0.75f
            }
            return Triple(
                AudioEnergyPattern.STEADY,
                confidence,
                "能量平稳（归一化标准差=${String.format("%.2f", normalizedStdDev)}），单向播放特征，可能是语音信箱"
            )
        }

        // 波动模式（对话特征）
        if (normalizedStdDev > FLUCTUATING_PATTERN_THRESHOLD && changeRate > 2f) {
            val confidence = when {
                normalizedStdDev > 0.5f && changeRate > 3f -> 0.95f
                normalizedStdDev > 0.4f && changeRate > 2.5f -> 0.85f
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
