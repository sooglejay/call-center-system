package com.callcenter.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 调试日志工具
 * 将关键日志写入文件，方便排查问题
 */
object DebugLogger {
    private const val TAG = "DebugLogger"
    private const val LOG_FILE_NAME = "debug_speaker.log"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 初始化日志文件
     */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        logFile?.delete() // 每次启动清空旧日志
        log("=== DebugLogger 初始化 ===")
    }
    
    /**
     * 记录日志
     */
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message\n"
        
        // 同时输出到 Logcat
        Log.d(TAG, message)
        
        // 写入文件
        try {
            logFile?.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败: ${e.message}")
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    /**
     * 获取日志内容
     */
    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "日志文件不存在"
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
}
