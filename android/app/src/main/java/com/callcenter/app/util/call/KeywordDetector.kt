package com.callcenter.app.util.call

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 关键词识别结果
 */
data class KeywordResult(
    /** 是否检测到关键词 */
    val detected: Boolean,
    /** 检测到的关键词列表 */
    val keywords: List<String>,
    /** 识别到的完整文本 */
    val fullText: String,
    /** 置信度 */
    val confidence: Float,
    /** 判断结果 */
    val callType: KeywordCallType,
    /** 详细说明 */
    val reason: String
)

/**
 * 关键词判断的通话类型
 */
enum class KeywordCallType {
    VOICEMAIL,      // 语音信箱
    HUMAN,          // 真人接听
    IVR,            // IVR语音导航
    UNKNOWN         // 无法判断
}

/**
 * AI关键词识别器
 *
 * 通过语音识别检测关键词，判断通话类型：
 * - 语音信箱关键词："请留言"、"语音信箱"等
 * - 真人关键词："喂"、"你好"、"您好"、"哪位"等
 * - IVR关键词："请按1"、"转人工"等
 *
 * 使用 Vosk 离线语音识别引擎
 */
class KeywordDetector(
    private val context: Context,
    private val onResult: ((KeywordResult) -> Unit)? = null
) {

    companion object {
        private const val TAG = "KeywordDetector"

        // 模型配置
        const val MODEL_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_DOWNLOAD_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"

        // 语音信箱关键词
        private val VOICEMAIL_KEYWORDS = listOf(
            "请留言", "语音信箱", "请留下您的消息", "请在哔声后留言",
            "请留下您的姓名", "请留下您的电话", "请简短留言",
            "留言", "录音", "哔声后", "请说话",
            "无人接听", "暂时无法接听", "不方便接听",
            "leave a message", "voicemail", "after the tone"
        )

        // 真人接听关键词
        private val HUMAN_KEYWORDS = listOf(
            "喂", "你好", "您好", "哪位", "谁呀", "请问",
            "什么事", "有什么事", "请讲", "说吧", "在吗",
            "我是", "我是谁", "找谁", "找哪位",
            "你好哪位", "您好哪位", "请问哪位",
            "hello", "hi", "who is this"
        )

        // IVR语音导航关键词
        private val IVR_KEYWORDS = listOf(
            "请按", "请输入", "请选择", "按1", "按2", "按0",
            "转人工", "人工服务", "请拨", "请按键",
            "返回", "重听", "上一级", "欢迎致电",
            "press", "enter", "dial"
        )

        private const val MIN_TEXT_LENGTH = 1
    }

    private var model: Model? = null
    private val isProcessing = AtomicBoolean(false)

    /**
     * 获取模型目录
     */
    private fun getModelDir(): File {
        return File(context.filesDir, "vosk_models/$MODEL_NAME")
    }

    /**
     * 检查 Vosk 模型是否可用
     */
    fun isModelAvailable(): Boolean {
        val modelDir = getModelDir()
        val amFile = File(modelDir, "am/final.mdl")
        val confFile = File(modelDir, "conf/mfcc.conf")
        return amFile.exists() && confFile.exists()
    }

    /**
     * 初始化 Vosk 模型
     */
    fun init(): Boolean {
        if (model != null) {
            return true
        }

        val modelDir = getModelDir()
        if (!isModelAvailable()) {
            Log.w(TAG, "Vosk 模型不存在: ${modelDir.absolutePath}")
            return false
        }

        return try {
            model = Model(modelDir.absolutePath)
            Log.d(TAG, "Vosk 模型加载成功")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Vosk 模型加载失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Vosk 模型初始化异常: ${e.message}")
            false
        }
    }

    /**
     * 从音频文件识别关键词
     *
     * @param audioFilePath PCM 音频文件路径（16kHz, 16bit, mono）
     * @return 识别结果
     */
    fun recognizeFromFile(audioFilePath: String): KeywordResult {
        Log.d(TAG, "========== 开始关键词识别 ==========")
        Log.d(TAG, "音频文件: $audioFilePath")
        
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "正在处理中，跳过")
            return createEmptyResult("正在处理中")
        }

        try {
            val model = model
            if (model == null) {
                Log.e(TAG, "模型未初始化")
                isProcessing.set(false)
                return createEmptyResult("模型未初始化，请先下载 Vosk 模型")
            }

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "音频文件不存在: $audioFilePath")
                isProcessing.set(false)
                return createEmptyResult("音频文件不存在")
            }
            
            val fileSize = audioFile.length()
            val durationMs = fileSize / 32  // 16kHz * 2bytes = 32 bytes/ms
            Log.d(TAG, "音频文件大小: ${fileSize}bytes, 预估时长: ${durationMs}ms")

            // 创建识别器（16kHz 采样率）
            Log.d(TAG, "创建 Vosk 识别器 (16kHz)")
            val recognizer = Recognizer(model, 16000f)
            val accumulatedText = StringBuilder()
            var processedBytes = 0L
            var lastLogTime = System.currentTimeMillis()

            // 读取音频文件并识别
            FileInputStream(audioFile).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    processedBytes += bytesRead
                    
                    // 将 byte 数组转换为 short 数组（16bit PCM）
                    val shortBuffer = ShortArray(bytesRead / 2)
                    ByteBuffer.wrap(buffer)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shortBuffer)

                    // 进行识别
                    if (recognizer.acceptWaveForm(shortBuffer, shortBuffer.size)) {
                        val result = recognizer.result
                        val text = parseResultText(result)
                        if (text.isNotEmpty()) {
                            accumulatedText.append(text).append(" ")
                            Log.d(TAG, "中间结果: $text")
                        }
                    }
                    
                    // 每3秒打印一次进度
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 3000) {
                        val progress = (processedBytes * 100 / fileSize).toInt()
                        Log.d(TAG, "识别进度: $progress%, 已处理: ${processedBytes}bytes")
                        lastLogTime = now
                    }
                }

                // 获取最终结果
                val finalResult = recognizer.finalResult
                val text = parseResultText(finalResult)
                if (text.isNotEmpty()) {
                    accumulatedText.append(text)
                }
            }

            recognizer.close()
            isProcessing.set(false)

            val fullText = accumulatedText.toString().trim()
            Log.d(TAG, "========== 识别完成 ==========")
            Log.d(TAG, "完整识别文本: $fullText")

            return analyzeKeywords(fullText)

        } catch (e: Exception) {
            Log.e(TAG, "识别失败: ${e.message}", e)
            isProcessing.set(false)
            return createEmptyResult("识别失败: ${e.message}")
        }
    }

    /**
     * 解析识别结果 JSON
     */
    private fun parseResultText(jsonResult: String): String {
        return try {
            val json = JSONObject(jsonResult)
            json.optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 创建空结果
     */
    private fun createEmptyResult(reason: String): KeywordResult {
        return KeywordResult(
            detected = false,
            keywords = emptyList(),
            fullText = "",
            confidence = 0f,
            callType = KeywordCallType.UNKNOWN,
            reason = reason
        )
    }

    /**
     * 分析关键词并返回结果
     */
    private fun analyzeKeywords(fullText: String): KeywordResult {
        if (fullText.length < MIN_TEXT_LENGTH) {
            return KeywordResult(
                detected = false,
                keywords = emptyList(),
                fullText = fullText,
                confidence = 0f,
                callType = KeywordCallType.UNKNOWN,
                reason = "识别文本过短"
            )
        }

        // 检测各类关键词
        val voicemailFound = findKeywords(fullText, VOICEMAIL_KEYWORDS)
        val humanFound = findKeywords(fullText, HUMAN_KEYWORDS)
        val ivrFound = findKeywords(fullText, IVR_KEYWORDS)

        Log.d(TAG, """
            关键词分析:
            - 文本: $fullText
            - 语音信箱: $voicemailFound
            - 真人: $humanFound
            - IVR: $ivrFound
        """.trimIndent())

        // 根据关键词组合判断通话类型
        return when {
            // 语音信箱关键词优先
            voicemailFound.isNotEmpty() && humanFound.isEmpty() -> KeywordResult(
                detected = true,
                keywords = voicemailFound,
                fullText = fullText,
                confidence = calculateConfidence(voicemailFound, fullText),
                callType = KeywordCallType.VOICEMAIL,
                reason = "检测到语音信箱关键词: ${voicemailFound.joinToString()}"
            )

            // 混合关键词
            voicemailFound.isNotEmpty() && humanFound.isNotEmpty() -> {
                val voicemailScore = voicemailFound.size * 2
                val humanScore = humanFound.size
                if (voicemailScore > humanScore) {
                    KeywordResult(true, voicemailFound + humanFound, fullText, 0.6f,
                        KeywordCallType.VOICEMAIL, "倾向于语音信箱: ${voicemailFound.joinToString()}")
                } else {
                    KeywordResult(true, humanFound + voicemailFound, fullText, 0.6f,
                        KeywordCallType.HUMAN, "倾向于真人: ${humanFound.joinToString()}")
                }
            }

            // 只检测到真人关键词
            humanFound.isNotEmpty() -> KeywordResult(
                detected = true,
                keywords = humanFound,
                fullText = fullText,
                confidence = calculateConfidence(humanFound, fullText),
                callType = KeywordCallType.HUMAN,
                reason = "检测到真人接听关键词: ${humanFound.joinToString()}"
            )

            // 只检测到IVR关键词
            ivrFound.isNotEmpty() -> KeywordResult(
                detected = true,
                keywords = ivrFound,
                fullText = fullText,
                confidence = calculateConfidence(ivrFound, fullText),
                callType = KeywordCallType.IVR,
                reason = "检测到IVR语音导航关键词: ${ivrFound.joinToString()}"
            )

            // 未检测到关键词
            else -> KeywordResult(
                detected = false,
                keywords = emptyList(),
                fullText = fullText,
                confidence = 0.3f,
                callType = KeywordCallType.UNKNOWN,
                reason = "未检测到有效关键词"
            )
        }
    }

    /**
     * 在文本中查找关键词
     */
    private fun findKeywords(text: String, keywords: List<String>): List<String> {
        val found = mutableListOf<String>()
        val lowerText = text.lowercase()
        for (keyword in keywords) {
            if (lowerText.contains(keyword.lowercase())) {
                found.add(keyword)
            }
        }
        return found
    }

    /**
     * 计算置信度
     */
    private fun calculateConfidence(keywords: List<String>, text: String): Float {
        if (keywords.isEmpty()) return 0f
        val keywordRatio = keywords.size.toFloat() / (text.length / 10f + 1)
        return minOf(0.95f, 0.5f + keywordRatio * 0.3f)
    }

    /**
     * 释放资源
     */
    fun release() {
        isProcessing.set(false)
        try {
            model?.close()
            model = null
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错: ${e.message}")
        }
    }
}

/**
 * Vosk 模型管理器
 *
 * 负责下载和解压 Vosk 中文模型
 */
class VoskModelManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelManager"
        const val MODEL_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_DOWNLOAD_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    }

    /**
     * 检查模型是否已下载
     */
    fun isModelReady(): Boolean {
        val modelDir = getModelDir()
        return File(modelDir, "am/final.mdl").exists() &&
               File(modelDir, "conf/mfcc.conf").exists()
    }

    /**
     * 获取模型路径
     */
    fun getModelPath(): String = getModelDir().absolutePath

    private fun getModelDir(): File = File(context.filesDir, "vosk_models/$MODEL_NAME")

    /**
     * 下载并解压模型
     *
     * @param progressCallback 进度回调 (0-100, -1表示解压中)
     */
    suspend fun downloadModel(progressCallback: ((Int) -> Unit)? = null): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                if (isModelReady()) {
                    Log.d(TAG, "模型已存在")
                    progressCallback?.invoke(100)
                    return@withContext Result.success(true)
                }

                val tempFile = File(context.cacheDir, "$MODEL_NAME.zip")
                Log.d(TAG, "开始下载模型: $MODEL_DOWNLOAD_URL")
                progressCallback?.invoke(0)

                // 下载
                val url = URL(MODEL_DOWNLOAD_URL)
                val connection = url.openConnection()
                connection.connect()
                val fileLength = connection.contentLengthLong
                var downloaded = 0L

                url.openStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (fileLength > 0) {
                                progressCallback?.invoke((downloaded * 100 / fileLength).toInt())
                            }
                        }
                    }
                }

                Log.d(TAG, "下载完成，开始解压")
                progressCallback?.invoke(-1)

                // 解压
                unzipModel(tempFile, getModelDir())
                tempFile.delete()

                Log.d(TAG, "模型解压完成")
                progressCallback?.invoke(100)
                Result.success(true)

            } catch (e: Exception) {
                Log.e(TAG, "下载模型失败: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * 解压模型
     */
    private fun unzipModel(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                // 跳过顶层目录
                val relativePath = if (entryName.startsWith("$MODEL_NAME/")) {
                    entryName.removePrefix("$MODEL_NAME/")
                } else {
                    entryName
                }

                if (relativePath.isNotEmpty()) {
                    val outputFile = File(targetDir, relativePath)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos -> zis.copyTo(fos) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * 删除模型
     */
    fun deleteModel(): Boolean {
        return try {
            getModelDir().deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "删除模型失败: ${e.message}")
            false
        }
    }

    /**
     * 获取模型大小
     */
    fun getModelSize(): Long {
        val modelDir = getModelDir()
        if (!modelDir.exists()) return 0
        return modelDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * 获取模型大小描述
     */
    fun getModelSizeDescription(): String {
        val size = getModelSize()
        return when {
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}
