package com.callcenter.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logcat 日志收集器
 *
 * 功能：
 * - 收集设备 logcat 日志
 * - 限制日志文件大小（默认 8MB）
 * - 使用循环缓冲区，新日志追加到文件头部
 */
object LogcatCollector {
    private const val TAG = "LogcatCollector"
    
    // 最大日志文件大小（8MB）
    private const val MAX_LOG_SIZE = 8 * 1024 * 1024L
    
    // 日志文件名
    private const val LOG_FILE_NAME = "device_logs.txt"
    
    // 时间格式
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 收集 logcat 日志并保存到文件
     *
     * @param context 上下文
     * @return 日志文件路径，失败返回 null
     */
    suspend fun collectLogs(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val logFile = File(context.cacheDir, LOG_FILE_NAME)

            // 收集新的 logcat 日志（仅本应用）
            val newLogs = collectLogcat(context)
            
            if (newLogs.isNullOrEmpty()) {
                Log.w(TAG, "没有收集到日志")
                return@withContext null
            }
            
            // 读取现有日志
            val existingLogs = if (logFile.exists()) {
                logFile.readText()
            } else {
                ""
            }
            
            // 合并日志：新日志在前，旧日志在后
            val combinedLogs = buildString {
                append("========== 日志上传时间: ${dateFormat.format(Date())} ==========\n")
                append("设备信息: ${getDeviceInfo()}\n")
                append("应用版本: ${getAppVersion(context)}\n")
                append("=" .repeat(50)).append("\n\n")
                append(newLogs)
                append("\n")
                append(existingLogs)
            }
            
            // 如果超过大小限制，截断旧日志
            val finalLogs = if (combinedLogs.length > MAX_LOG_SIZE) {
                combinedLogs.take(MAX_LOG_SIZE.toInt())
            } else {
                combinedLogs
            }
            
            // 写入文件
            logFile.writeText(finalLogs)
            
            Log.d(TAG, "日志收集完成，文件大小: ${finalLogs.length} 字节")
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "收集日志失败: ${e.message}")
            null
        }
    }
    
    /**
     * 收集 logcat 日志（仅收集本应用的日志）
     */
    private fun collectLogcat(context: Context): String? {
        return try {
            // 获取应用的 PID
            val pid = android.os.Process.myPid()

            // 使用 --pid 参数只收集当前进程的日志，减少噪声
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",           // dump 模式，不阻塞
                    "-v", "time",   // 时间格式
                    "-t", "5000",   // 最近 5000 行
                    "--pid", pid.toString()  // 只收集当前进程的日志
                )
            )

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            reader.close()
            process.waitFor()

            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "执行 logcat 命令失败: ${e.message}")
            null
        }
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return buildString {
            append("厂商: ${android.os.Build.MANUFACTURER}, ")
            append("型号: ${android.os.Build.MODEL}, ")
            append("系统: Android ${android.os.Build.VERSION.RELEASE}, ")
            append("SDK: ${android.os.Build.VERSION.SDK_INT}")
        }
    }
    
    /**
     * 获取应用版本
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "未知版本"
        }
    }
    
    /**
     * 获取日志文件大小
     */
    fun getLogFileSize(context: Context): Long {
        val logFile = File(context.cacheDir, LOG_FILE_NAME)
        return if (logFile.exists()) logFile.length() else 0L
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
    
    /**
     * 清除日志文件
     */
    fun clearLogs(context: Context) {
        val logFile = File(context.cacheDir, LOG_FILE_NAME)
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}
