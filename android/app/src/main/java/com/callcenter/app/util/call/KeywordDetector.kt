package com.callcenter.app.util.call

import android.content.Context
import android.util.Log
import com.callcenter.app.util.DebugLogger
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
 * Vosk 模型配置
 */
data class VoskModelConfig(
    val name: String,
    val displayName: String,
    val downloadUrl: String,
    val language: String
)

/**
 * AI关键词识别器
 *
 * 主要目标：识别语音信箱
 * - 语音信箱通常是标准录音，口音清晰，识别准确率高
 * - 真人接听口音多样，难以准确识别
 *
 * 识别策略：
 * - 重点识别语音信箱关键词
 * - 如果检测到语音信箱关键词，确定是语音信箱
 * - 如果没有检测到语音信箱关键词，结合时长/音频能量判断
 * - 同时识别 IVR 语音导航场景
 */
class KeywordDetector(
    private val context: Context,
    private val onResult: ((KeywordResult) -> Unit)? = null
) {

    companion object {
        private const val TAG = "KeywordDetector"

        // 支持的模型列表
        val SUPPORTED_MODELS = listOf(
            VoskModelConfig(
                name = "vosk-model-small-cn-0.22",
                displayName = "中文模型",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
                language = "zh"
            ),
            VoskModelConfig(
                name = "vosk-model-small-en-us-0.15",
                displayName = "英文模型",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                language = "en"
            )
        )

        // ==================== 语音信箱关键词（重点识别） ====================
        // 语音信箱通常是标准录音，口音清晰，这些关键词识别准确率高
        private val VOICEMAIL_KEYWORDS = listOf(
            // ===== 高置信度关键词（常见语音信箱提示语）=====
            // 中文 - 留言提示
            "请留言", "请在哔声后留言", "请在滴声后留言", "请在提示音后留言",
            "请留下您的消息", "请留下您的姓名", "请留下您的电话", "请留下您的联系方式",
            "请简短留言", "请留下您的口信", "请在听到哔声后留言",
            
            // 中文 - 语音信箱标识
            "语音信箱", "您拨打的用户已开通语音信箱", "对方已开通语音信箱服务",
            "您拨打的电话已转入语音信箱", "已转接到语音信箱",
            
            // 中文 - 无法接听提示
            "无人接听", "暂时无法接听", "不方便接听", "现在无法接听",
            "您拨打的电话暂时无人接听", "您拨打的用户暂时无法接听",
            "您拨打的号码暂时无人接听", "对方暂时无法接听您的电话",
            
            // 中文 - 录音/留言相关
            "留言", "录音", "哔声后", "滴声后", "提示音后",
            "请说话", "请开始留言", "您可以开始留言",
            
            // 中文 - 移动/联通/电信语音信箱
            "您拨打的电话已关机", "您拨打的电话不在服务区",
            "您拨打的电话正在通话中", "您拨打的用户正忙",
            "请挂机后重新拨打", "被叫用户忙",
            
            // 英文 - 常见语音信箱提示
            "leave a message", "voicemail", "after the tone", "after the beep",
            "please leave", "message after", "not available", "unavailable",
            "unable to answer", "record your message", "please record",
            "at the tone", "at the beep", "leave your message",
            "the person you are calling", "you have reached",
            "is not available", "cannot take your call",
            "please leave a message after", "leave a message after the tone"
        )

        // ==================== IVR语音导航关键词 ====================
        // IVR 也是可以明确识别的场景
        private val IVR_KEYWORDS = listOf(
            // 中文
            "请按", "请输入", "请选择", "按1", "按2", "按0",
            "转人工", "人工服务", "请拨", "请按键",
            "返回", "重听", "上一级", "欢迎致电",
            "请按井号键", "请按星号键", "按井", "按星",
            
            // 英文
            "press", "enter", "dial", "please press",
            "for english", "main menu", "customer service"
        )

        private const val MIN_TEXT_LENGTH = 1
    }

    private val models = mutableMapOf<String, Model>()
    private val isProcessing = AtomicBoolean(false)

    /**
     * 获取模型目录
     */
    private fun getModelDir(modelName: String): File {
        return File(context.filesDir, "vosk_models/$modelName")
    }

    /**
     * 检查指定模型是否可用
     */
    fun isModelAvailable(modelName: String = SUPPORTED_MODELS[0].name): Boolean {
        val modelDir = getModelDir(modelName)
        val amFile = File(modelDir, "am/final.mdl")
        val confFile = File(modelDir, "conf/mfcc.conf")
        return amFile.exists() && confFile.exists()
    }

    /**
     * 检查所有可用模型
     */
    fun getAvailableModels(): List<String> {
        return SUPPORTED_MODELS.map { it.name }.filter { isModelAvailable(it) }
    }

    /**
     * 初始化所有可用模型
     */
    fun init(): Boolean {
        var anySuccess = false
        for (modelConfig in SUPPORTED_MODELS) {
            if (isModelAvailable(modelConfig.name)) {
                if (loadModel(modelConfig.name)) {
                    anySuccess = true
                }
            }
        }
        return anySuccess
    }

    /**
     * 加载指定模型
     */
    private fun loadModel(modelName: String): Boolean {
        if (models.containsKey(modelName)) {
            return true
        }

        val modelDir = getModelDir(modelName)
        if (!isModelAvailable(modelName)) {
            Log.w(TAG, "Vosk 模型不存在: ${modelDir.absolutePath}")
            return false
        }

        return try {
            models[modelName] = Model(modelDir.absolutePath)
            Log.d(TAG, "Vosk 模型加载成功: $modelName")
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
     * 从音频文件识别关键词（使用所有可用模型）
     *
     * @param audioFilePath PCM 音频文件路径（16kHz, 16bit, mono）
     * @return 识别结果
     */
    fun recognizeFromFile(audioFilePath: String): KeywordResult {
        DebugLogger.logSeparator("关键词识别")
        Log.d(TAG, "========== 开始关键词识别 ==========")
        Log.d(TAG, "音频文件: $audioFilePath")
        DebugLogger.log("[KeywordDetect] 音频文件: $audioFilePath")
        
        if (!isProcessing.compareAndSet(false, true)) {
            Log.w(TAG, "正在处理中，跳过")
            DebugLogger.log("[KeywordDetect] ✗ 正在处理中，跳过")
            return createEmptyResult("正在处理中")
        }

        try {
            // 初始化所有可用模型
            val initSuccess = init()
            DebugLogger.log("[KeywordDetect] 模型初始化结果: $initSuccess")
            
            if (!initSuccess) {
                Log.e(TAG, "没有可用的模型")
                DebugLogger.log("[KeywordDetect] ✗ 没有可用的模型")
                isProcessing.set(false)
                return createEmptyResult("没有可用的Vosk模型，请先下载模型")
            }

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "音频文件不存在: $audioFilePath")
                DebugLogger.log("[KeywordDetect] ✗ 音频文件不存在: $audioFilePath")
                isProcessing.set(false)
                return createEmptyResult("音频文件不存在")
            }
            
            val fileSize = audioFile.length()
            val durationMs = fileSize / 32  // 16kHz * 2bytes = 32 bytes/ms
            Log.d(TAG, "音频文件大小: ${fileSize}bytes, 预估时长: ${durationMs}ms")
            DebugLogger.log("[KeywordDetect] 音频文件大小: ${fileSize}bytes")
            DebugLogger.log("[KeywordDetect] 预估时长: ${durationMs}ms")

            // 使用所有可用模型进行识别
            val allResults = mutableListOf<Pair<String, KeywordResult>>()
            
            DebugLogger.log("[KeywordDetect] 可用模型数量: ${models.size}")
            
            for (modelConfig in SUPPORTED_MODELS) {
                val model = models[modelConfig.name]
                if (model != null) {
                    Log.d(TAG, "使用模型: ${modelConfig.displayName}")
                    DebugLogger.log("[KeywordDetect] 开始使用模型: ${modelConfig.displayName} (${modelConfig.language})")
                    val result = recognizeWithModel(model, audioFile, modelConfig.language)
                    DebugLogger.log("[KeywordDetect] 模型 ${modelConfig.displayName} 识别文本: ${result.fullText.take(200)}")
                    allResults.add(modelConfig.language to result)
                } else {
                    DebugLogger.log("[KeywordDetect] 模型 ${modelConfig.displayName} 未加载，跳过")
                }
            }

            isProcessing.set(false)

            // 合并所有模型的识别结果
            DebugLogger.log("[KeywordDetect] 合并 ${allResults.size} 个模型的识别结果")
            return mergeResults(allResults)

        } catch (e: Exception) {
            Log.e(TAG, "识别失败: ${e.message}", e)
            DebugLogger.log("[KeywordDetect] ✗ 识别异常: ${e.message}")
            DebugLogger.log("[KeywordDetect] 异常堆栈: ${e.stackTraceToString().take(500)}")
            isProcessing.set(false)
            return createEmptyResult("识别失败: ${e.message}")
        }
    }

    /**
     * 使用单个模型进行识别
     */
    private fun recognizeWithModel(model: Model, audioFile: File, language: String): KeywordResult {
        val recognizer = Recognizer(model, 16000f)
        val accumulatedText = StringBuilder()
        var processedBytes = 0L
        var lastLogTime = System.currentTimeMillis()

        try {
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
                        }
                    }
                    
                    // 每5秒打印一次进度
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 5000) {
                        val progress = (processedBytes * 100 / audioFile.length()).toInt()
                        Log.d(TAG, "[$language] 识别进度: $progress%")
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
        } finally {
            recognizer.close()
        }

        val fullText = accumulatedText.toString().trim()
        Log.d(TAG, "[$language] 识别文本: $fullText")

        // 返回包含文本的中间结果，后续会合并分析
        return KeywordResult(
            detected = false,
            keywords = emptyList(),
            fullText = fullText,
            confidence = 0f,
            callType = KeywordCallType.UNKNOWN,
            reason = ""
        )
    }

    /**
     * 合并多个模型的识别结果
     */
    private fun mergeResults(results: List<Pair<String, KeywordResult>>): KeywordResult {
        if (results.isEmpty()) {
            DebugLogger.log("[KeywordDetect] ✗ 没有识别结果")
            return createEmptyResult("没有识别结果")
        }

        // 合并所有文本
        val allText = results.map { it.second.fullText }.filter { it.isNotEmpty() }.joinToString(" ")

        Log.d(TAG, "========== 合并识别结果 ==========")
        Log.d(TAG, "合并文本: $allText")
        DebugLogger.log("[KeywordDetect] 合并后文本长度: ${allText.length}")
        DebugLogger.log("[KeywordDetect] 合并后文本: ${allText.take(500)}")

        // 对合并后的文本进行关键词分析
        return analyzeKeywords(allText)
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
     * 
     * 简化策略：只识别语音信箱和IVR，不识别真人接听
     * - 语音信箱：标准录音，口音清晰，识别准确率高
     * - 真人接听：口音多样，难以准确识别，交给时长/音频能量判断
     */
    private fun analyzeKeywords(fullText: String): KeywordResult {
        DebugLogger.log("[KeywordDetect] 开始分析关键词，文本长度: ${fullText.length}")
        
        if (fullText.length < MIN_TEXT_LENGTH) {
            DebugLogger.log("[KeywordDetect] ✗ 识别文本过短: ${fullText.length} < $MIN_TEXT_LENGTH")
            return KeywordResult(
                detected = false,
                keywords = emptyList(),
                fullText = fullText,
                confidence = 0f,
                callType = KeywordCallType.UNKNOWN,
                reason = "识别文本过短"
            )
        }

        // 优先检测语音信箱关键词（重点目标）
        val voicemailFound = findKeywords(fullText, VOICEMAIL_KEYWORDS)
        
        DebugLogger.log("[KeywordDetect] ========== 关键词分析 ==========")
        DebugLogger.log("[KeywordDetect] 语音信箱关键词: $voicemailFound")
        
        // 如果检测到语音信箱关键词，直接返回
        if (voicemailFound.isNotEmpty()) {
            val confidence = calculateConfidence(voicemailFound, fullText)
            DebugLogger.log("[KeywordDetect] ✓ 检测到语音信箱关键词，置信度: $confidence")
            DebugLogger.log("[KeywordDetect] 最终结果: VOICEMAIL, detected=true, keywords=$voicemailFound")
            
            return KeywordResult(
                detected = true,
                keywords = voicemailFound,
                fullText = fullText,
                confidence = confidence,
                callType = KeywordCallType.VOICEMAIL,
                reason = "检测到语音信箱关键词: ${voicemailFound.joinToString()}"
            )
        }

        // 检测 IVR 语音导航关键词
        val ivrFound = findKeywords(fullText, IVR_KEYWORDS)
        DebugLogger.log("[KeywordDetect] IVR关键词: $ivrFound")
        
        if (ivrFound.isNotEmpty()) {
            val confidence = calculateConfidence(ivrFound, fullText)
            DebugLogger.log("[KeywordDetect] ✓ 检测到IVR关键词，置信度: $confidence")
            DebugLogger.log("[KeywordDetect] 最终结果: IVR, detected=true, keywords=$ivrFound")
            
            return KeywordResult(
                detected = true,
                keywords = ivrFound,
                fullText = fullText,
                confidence = confidence,
                callType = KeywordCallType.IVR,
                reason = "检测到IVR语音导航关键词: ${ivrFound.joinToString()}"
            )
        }

        // 未检测到关键词，返回 UNKNOWN
        // 让其他判断方式（时长、音频能量）来决定是否是真人接听
        DebugLogger.log("[KeywordDetect] 未检测到语音信箱或IVR关键词")
        DebugLogger.log("[KeywordDetect] 最终结果: UNKNOWN，将由时长/音频能量判断")
        
        return KeywordResult(
            detected = false,
            keywords = emptyList(),
            fullText = fullText,
            confidence = 0.3f,
            callType = KeywordCallType.UNKNOWN,
            reason = "未检测到语音信箱关键词，可能是真人接听"
        )
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
            models.values.forEach { model ->
                try {
                    model.close()
                } catch (e: Exception) {
                    Log.e(TAG, "释放模型资源时出错: ${e.message}")
                }
            }
            models.clear()
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错: ${e.message}")
        }
    }
}

/**
 * Vosk 模型管理器
 *
 * 负责下载和解压 Vosk 模型，支持中英文多模型
 */
class VoskModelManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelManager"
    }

    /**
     * 获取所有支持的模型配置
     */
    fun getSupportedModels(): List<VoskModelConfig> = KeywordDetector.SUPPORTED_MODELS

    /**
     * 检查指定模型是否已下载
     */
    fun isModelReady(modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name): Boolean {
        val modelDir = getModelDir(modelName)
        return File(modelDir, "am/final.mdl").exists() &&
               File(modelDir, "conf/mfcc.conf").exists()
    }

    /**
     * 获取所有已下载的模型
     */
    fun getDownloadedModels(): List<String> {
        return KeywordDetector.SUPPORTED_MODELS.map { it.name }.filter { isModelReady(it) }
    }

    /**
     * 获取模型路径
     */
    fun getModelPath(modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name): String =
        getModelDir(modelName).absolutePath

    private fun getModelDir(modelName: String): File =
        File(context.filesDir, "vosk_models/$modelName")

    /**
     * 下载指定模型
     *
     * @param modelName 模型名称
     * @param progressCallback 进度回调 (0-100, -1表示解压中)
     */
    suspend fun downloadModel(
        modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val modelConfig = KeywordDetector.SUPPORTED_MODELS.find { it.name == modelName }
                ?: return@withContext Result.failure(IllegalArgumentException("未知的模型: $modelName"))

            if (isModelReady(modelName)) {
                Log.d(TAG, "模型已存在: $modelName")
                progressCallback?.invoke(100)
                return@withContext Result.success(true)
            }

            val tempFile = File(context.cacheDir, "$modelName.zip")
            Log.d(TAG, "开始下载模型: ${modelConfig.displayName}")
            Log.d(TAG, "下载地址: ${modelConfig.downloadUrl}")
            progressCallback?.invoke(0)

            // 下载
            val url = URL(modelConfig.downloadUrl)
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
            unzipModel(tempFile, getModelDir(modelName), modelName)
            tempFile.delete()

            Log.d(TAG, "模型解压完成: $modelName")
            progressCallback?.invoke(100)
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "下载模型失败: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 下载所有模型
     *
     * @param progressCallback 进度回调 (模型名, 进度)
     */
    suspend fun downloadAllModels(
        progressCallback: ((String, Int) -> Unit)? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var allSuccess = true
        for (modelConfig in KeywordDetector.SUPPORTED_MODELS) {
            if (!isModelReady(modelConfig.name)) {
                val result = downloadModel(modelConfig.name) { progress ->
                    progressCallback?.invoke(modelConfig.name, progress)
                }
                if (result.isFailure) {
                    allSuccess = false
                    Log.e(TAG, "下载模型失败: ${modelConfig.name}")
                }
            }
        }
        if (allSuccess) Result.success(true) else Result.failure(Exception("部分模型下载失败"))
    }

    /**
     * 解压模型
     */
    private fun unzipModel(zipFile: File, targetDir: File, modelName: String) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                // 跳过顶层目录（模型zip文件通常包含一个顶层目录）
                val relativePath = extractRelativePath(entryName, modelName)

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
     * 提取相对路径（处理zip文件中的顶层目录）
     */
    private fun extractRelativePath(entryName: String, modelName: String): String {
        // 尝试匹配模型名称作为顶层目录
        val prefixPatterns = listOf(
            "$modelName/",
            modelName.split("-").first() // 有时顶层目录名称可能不完全匹配
        )

        for (prefix in prefixPatterns) {
            if (entryName.startsWith(prefix)) {
                return entryName.removePrefix(prefix)
            }
        }

        // 如果没有顶层目录匹配，返回原路径（跳过以 / 开头的）
        return if (entryName.startsWith("/")) entryName.substring(1) else entryName
    }

    /**
     * 删除指定模型
     */
    fun deleteModel(modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name): Boolean {
        return try {
            getModelDir(modelName).deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "删除模型失败: ${e.message}")
            false
        }
    }

    /**
     * 获取模型大小
     */
    fun getModelSize(modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name): Long {
        val modelDir = getModelDir(modelName)
        if (!modelDir.exists()) return 0
        return modelDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * 获取模型大小描述
     */
    fun getModelSizeDescription(modelName: String = KeywordDetector.SUPPORTED_MODELS[0].name): String {
        val size = getModelSize(modelName)
        return formatSize(size)
    }

    /**
     * 获取所有已下载模型的总大小
     */
    fun getTotalSize(): Long {
        return KeywordDetector.SUPPORTED_MODELS.map { getModelSize(it.name) }.sum()
    }

    /**
     * 获取总大小描述
     */
    fun getTotalSizeDescription(): String = formatSize(getTotalSize())

    /**
     * 格式化文件大小
     */
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}
