package com.callcenter.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 调试日志工具
 * 将关键日志写入文件，方便排查问题
 * 
 * 特性：
 * - 线程安全
 * - 异步写入，不阻塞主线程
 * - 自动清理旧日志
 * - 支持导出日志
 */
object DebugLogger {
    private const val TAG = "DebugLogger"
    private const val LOG_FILE_NAME = "call_debug.log"
    private const val MAX_LOG_SIZE = 1024 * 1024 * 5L // 5MB
    private const val MAX_LOG_FILES = 3
    
    private var logFile: File? = null
    private var context: Context? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logQueue = ConcurrentLinkedQueue<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DebugLogger-Writer").apply { isDaemon = true }
    }
    
    // 统计信息
    private var totalLogs = 0L
    private var droppedLogs = 0L
    private var startTime = 0L
    
    /**
     * 初始化日志文件
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        startTime = System.currentTimeMillis()
        
        // 清理旧日志文件
        cleanOldLogs()
        
        // 创建新日志文件
        logFile = File(context.filesDir, LOG_FILE_NAME)
        
        // 写入启动日志
        log("========== DebugLogger 初始化 ==========")
        log("设备信息: ${getDeviceInfo()}")
        log("应用版本: ${getAppVersion(context)}")
        log("初始化时间: ${dateFormat.format(Date())}")
        
        // 启动异步写入任务
        executor.scheduleWithFixedDelay({
            flushLogs()
        }, 100, 500, TimeUnit.MILLISECONDS)
        
        Log.d(TAG, "DebugLogger 初始化完成, 日志文件: ${logFile?.absolutePath}")
    }
    
    /**
     * 记录日志
     */
    fun log(message: String) {
        totalLogs++
        
        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name
        val logLine = "[$timestamp] [$threadName] $message"
        
        // 同时输出到 Logcat
        Log.d(TAG, message)
        
        // 添加到队列
        if (logQueue.size < 10000) {
            logQueue.offer(logLine)
        } else {
            droppedLogs++
        }
    }
    
    /**
     * 记录分隔线
     */
    fun logSeparator(title: String = "") {
        val line = if (title.isEmpty()) {
            "=".repeat(60)
        } else {
            "========== $title =========="
        }
        log(line)
    }
    
    /**
     * 记录通话状态检测日志（带详细上下文）
     */
    fun logCallState(
        phase: String,
        callState: Int,
        audioMode: Int,
        isServiceRunning: Boolean,
        currentCustomer: String?,
        extraInfo: Map<String, Any?> = emptyMap()
    ) {
        val callStateStr = when (callState) {
            0 -> "IDLE"
            1 -> "RINGING"
            2 -> "OFFHOOK"
            else -> "UNKNOWN($callState)"
        }
        
        val audioModeStr = when (audioMode) {
            0 -> "NORMAL"
            1 -> "RINGING"
            2 -> "IN_CALL"
            3 -> "IN_COMMUNICATION"
            else -> "UNKNOWN($audioMode)"
        }
        
        val extraStr = if (extraInfo.isNotEmpty()) {
            extraInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else ""
        
        log("[$phase] TelephonyState=$callStateStr($callState), AudioMode=$audioModeStr($audioMode), ServiceRunning=$isServiceRunning, Customer=$currentCustomer${if (extraStr.isNotEmpty()) ", $extraStr" else ""}")
    }
    
    /**
     * 记录API调用日志
     */
    fun logApiCall(api: String, request: String, response: String? = null, error: String? = null) {
        log("[API] $api")
        log("[API] Request: $request")
        response?.let { log("[API] Response: $it") }
        error?.let { log("[API] Error: $it") }
    }
    
    /**
     * 记录错误日志
     */
    fun logError(phase: String, error: Throwable) {
        log("[ERROR] $phase: ${error.message}")
        log("[ERROR] StackTrace: ${error.stackTraceToString().take(500)}")
    }
    
    /**
     * 异步写入日志到文件
     */
    private fun flushLogs() {
        val file = logFile ?: return
        
        try {
            // 检查文件大小
            if (file.length() > MAX_LOG_SIZE) {
                rotateLogFile()
            }
            
            // 批量写入
            val logsToWrite = mutableListOf<String>()
            while (logQueue.isNotEmpty()) {
                logQueue.poll()?.let { logsToWrite.add(it) }
            }
            
            if (logsToWrite.isNotEmpty()) {
                file.appendText(logsToWrite.joinToString("\n") + "\n")
            }
            
            // 定期写入统计信息
            if (totalLogs % 100 == 0L && totalLogs > 0) {
                file.appendText("[STATS] TotalLogs=$totalLogs, DroppedLogs=$droppedLogs, QueueSize=${logQueue.size}\n")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败: ${e.message}")
        }
    }
    
    /**
     * 轮转日志文件
     */
    private fun rotateLogFile() {
        val file = logFile ?: return
        
        try {
            // 重命名当前日志文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val rotatedFile = File(file.parent, "call_debug_$timestamp.log")
            file.renameTo(rotatedFile)
            
            // 创建新日志文件
            logFile = File(context?.filesDir, LOG_FILE_NAME)
            log("========== 日志轮转 ==========")
            
        } catch (e: Exception) {
            Log.e(TAG, "轮转日志文件失败: ${e.message}")
        }
    }
    
    /**
     * 清理旧日志文件
     */
    private fun cleanOldLogs() {
        val dir = context?.filesDir ?: return
        
        try {
            val logFiles = dir.listFiles { file ->
                file.name.startsWith("call_debug") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() }
            
            // 保留最新的 MAX_LOG_FILES 个文件
            logFiles?.drop(MAX_LOG_FILES)?.forEach { it.delete() }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理旧日志失败: ${e.message}")
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    /**
     * 获取所有日志文件
     */
    fun getAllLogFiles(): List<File> {
        val dir = context?.filesDir ?: return emptyList()
        return dir.listFiles { file ->
            file.name.startsWith("call_debug") && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }
    
    /**
     * 获取日志内容
     */
    fun getLogContent(maxLines: Int = 500): String {
        flushLogs() // 先刷新队列中的日志
        
        return try {
            val file = logFile ?: return "日志文件不存在"
            val lines = file.readLines()
            val startLine = maxOf(0, lines.size - maxLines)
            
            buildString {
                append("========== 日志摘要 ==========\n")
                append("总日志数: $totalLogs\n")
                append("丢弃日志: $droppedLogs\n")
                append("运行时长: ${(System.currentTimeMillis() - startTime) / 1000}s\n")
                append("日志文件: ${file.absolutePath}\n")
                append("文件大小: ${file.length() / 1024}KB\n")
                append("========== 最近 $maxLines 条日志 ==========\n\n")
                
                lines.drop(startLine).forEach { appendLine(it) }
            }
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
    
    /**
     * 获取详细诊断信息
     */
    fun getDiagnosticInfo(): String {
        return buildString {
            append("========== 诊断信息 ==========\n")
            append("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("设备: ${getDeviceInfo()}\n")
            append("应用版本: ${context?.let { getAppVersion(it) } ?: "未知"}\n")
            append("日志统计: Total=$totalLogs, Dropped=$droppedLogs\n")
            append("运行时长: ${(System.currentTimeMillis() - startTime) / 1000}s\n")
            append("日志文件: ${logFile?.absolutePath}\n")
            append("日志文件大小: ${logFile?.length()?.div(1024) ?: 0}KB\n")
        }
    }
    
    /**
     * 导出日志到文件
     */
    fun exportLogs(exportFile: File): Boolean {
        flushLogs()
        
        return try {
            val allFiles = getAllLogFiles()
            
            exportFile.printWriter().use { writer ->
                writer.println(getDiagnosticInfo())
                writer.println()
                
                allFiles.reversed().forEach { file ->
                    writer.println("========== ${file.name} ==========")
                    file.forEachLine { writer.println(it) }
                    writer.println()
                }
            }
            
            Log.d(TAG, "日志已导出到: ${exportFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败: ${e.message}")
            false
        }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        logQueue.clear()
        logFile?.delete()
        logFile = File(context?.filesDir, LOG_FILE_NAME)
        log("========== 日志已清空 ==========")
    }
    
    /**
     * 强制刷新
     */
    fun flush() {
        flushLogs()
    }
    
    private fun getDeviceInfo(): String {
        return buildString {
            append("${android.os.Build.MANUFACTURER} ")
            append("${android.os.Build.MODEL} ")
            append("(Android ${android.os.Build.VERSION.RELEASE})")
        }
    }
    
    private fun getAppVersion(context: Context): String {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
        } catch (e: Exception) {
            "未知"
        }
    }
    
    /**
     * 关闭日志系统
     */
    fun shutdown() {
        flushLogs()
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // ignore
        }
    }
}
