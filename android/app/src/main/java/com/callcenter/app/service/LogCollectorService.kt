package com.callcenter.app.service

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logcat 日志收集服务
 *
 * 功能：
 * - 收集通话相关的日志
 * - 使用 LRU 缓存，最多存储 10000 条
 * - 支持启动/停止
 * - 支持导出日志到文件
 */
@AndroidEntryPoint
class LogCollectorService : android.app.Service() {

    companion object {
        private const val TAG = "LogCollector"
        private var MAX_LOG_COUNT = 10000  // 最大日志数量（可动态调整）

        const val ACTION_START_COLLECTING = "com.callcenter.app.action.START_COLLECTING"
        const val ACTION_STOP_COLLECTING = "com.callcenter.app.action.STOP_COLLECTING"
        const val ACTION_EXPORT_LOGS = "com.callcenter.app.action.EXPORT_LOGS"
        const val ACTION_SET_MAX_SIZE = "com.callcenter.app.action.SET_MAX_SIZE"

        const val EXTRA_LOG_OUTPUT_PATH = "log_output_path"
        const val EXTRA_MAX_SIZE = "max_size"

        fun startCollecting(context: Context) {
            val intent = Intent(context, LogCollectorService::class.java).apply {
                action = ACTION_START_COLLECTING
            }
            context.startService(intent)
        }

        fun stopCollecting(context: Context) {
            val intent = Intent(context, LogCollectorService::class.java).apply {
                action = ACTION_STOP_COLLECTING
            }
            context.startService(intent)
        }

        fun exportLogs(context: Context, outputPath: String) {
            val intent = Intent(context, LogCollectorService::class.java).apply {
                action = ACTION_EXPORT_LOGS
                putExtra(EXTRA_LOG_OUTPUT_PATH, outputPath)
            }
            context.startService(intent)
        }

        fun setMaxSize(context: Context, maxSize: Int) {
            val intent = Intent(context, LogCollectorService::class.java).apply {
                action = ACTION_SET_MAX_SIZE
                putExtra(EXTRA_MAX_SIZE, maxSize)
            }
            context.startService(intent)
        }
    }

    // 关键字过滤：只收集与通话相关的日志
    private val LOG_KEYWORDS = listOf(
        "CallStateMonitor",
        "CallHelper",
        "AutoDial",
        "CallSpeaker",
        "CallAudioState"
    )

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null

    // 使用 LinkedHashMap 实现 LRU 缓存（accessOrder = true 表示按访问顺序排序）
    private val logCache = LinkedHashMap<String, String>(MAX_LOG_COUNT, 0.75f, true)
    private val isCollecting = AtomicBoolean(false)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "日志收集服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COLLECTING -> {
                startCollecting()
            }
            ACTION_STOP_COLLECTING -> {
                stopCollecting()
            }
            ACTION_EXPORT_LOGS -> {
                val outputPath = intent.getStringExtra(EXTRA_LOG_OUTPUT_PATH)
                if (outputPath != null) {
                    serviceScope.launch {
                        exportLogs(outputPath)
                    }
                }
            }
            ACTION_SET_MAX_SIZE -> {
                val maxSize = intent.getIntExtra(EXTRA_MAX_SIZE, 10000)
                MAX_LOG_COUNT = maxSize
                Log.d(TAG, "设置最大缓存大小: $MAX_LOG_COUNT")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCollecting()
        Log.d(TAG, "日志收集服务已销毁")
    }

    /**
     * 启动日志收集
     */
    private fun startCollecting() {
        if (isCollecting.get()) {
            Log.d(TAG, "日志收集已在运行")
            return
        }

        isCollecting.set(true)
        Log.d(TAG, "开始收集日志（关键字: ${LOG_KEYWORDS.joinToString(", ")}）")

        collectJob = serviceScope.launch {
            collectLogcat()
        }
    }

    /**
     * 停止日志收集
     */
    private fun stopCollecting() {
        if (!isCollecting.get()) {
            return
        }

        isCollecting.set(false)

        // 停止 collectJob
        collectJob?.cancel()
        collectJob = null

        // 销毁 logcat 进程
        logcatProcess?.destroy()
        logcatProcess = null

        Log.d(TAG, "停止收集日志（已收集 ${logCache.size} 条）")
    }

    /**
     * 收集 logcat 日志
     */
    private suspend fun collectLogcat() {
        try {
            // 清空之前的 logcat 缓冲区
            Runtime.getRuntime().exec("logcat -c")

            // 启动 logcat 进程
            val pb = ProcessBuilder("logcat", "-v", "time")
            pb.redirectErrorStream(true)
            logcatProcess = pb.start()

            val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))

            // 逐行读取
            while (isCollecting.get() && !collectJob?.isCancelled!!) {
                val line = reader.readLine() ?: continue

                // 检查是否包含关键字
                if (shouldCollect(line)) {
                    addLogToCache(line)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "收集日志失败: ${e.message}")
        }
    }

    /**
     * 判断是否应该收集这条日志
     */
    private fun shouldCollect(logLine: String): Boolean {
        return LOG_KEYWORDS.any { keyword ->
            logLine.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 添加日志到缓存（LRU 策略）
     */
    private fun addLogToCache(logLine: String) {
        synchronized(logCache) {
            // 如果缓存已满，移除最旧的条目
            if (logCache.size >= MAX_LOG_COUNT) {
                val oldestKey = logCache.keys.first()
                logCache.remove(oldestKey)
            }

            // 添加新日志（使用时间戳作为键）
            val key = "${System.currentTimeMillis()}_${logCache.size}"
            logCache[key] = logLine

            // 定期输出统计信息（每 1000 条）
            if (logCache.size % 1000 == 0) {
                Log.d(TAG, "缓存大小: ${logCache.size} / $MAX_LOG_COUNT")
            }
        }
    }

    /**
     * 导出日志到文件
     */
    private suspend fun exportLogs(outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "导出日志到: $outputPath")

            val logs = synchronized(logCache) {
                logCache.values.toList()
            }

            if (logs.isEmpty()) {
                Log.w(TAG, "没有日志可导出")
                return@withContext false
            }

            // 写入文件
            val file = java.io.File(outputPath)
            file.parentFile?.mkdirs()

            java.io.FileWriter(file).use { writer ->
                // 写入文件头
                writer.write("# Logcat 日志导出\n")
                writer.write("# 导出时间: ${dateFormat.format(Date())}\n")
                writer.write("# 日志数量: ${logs.size}\n")
                writer.write("# 关键字: ${LOG_KEYWORDS.joinToString(", ")}\n")
                writer.write("# " + "=".repeat(80) + "\n\n")

                // 写入日志
                logs.forEach { log ->
                    writer.write(log)
                    writer.write("\n")
                }
            }

            Log.d(TAG, "日志导出成功: ${file.absolutePath} (${logs.size} 条)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败: ${e.message}")
            false
        }
    }

    /**
     * 导出日志到文件（同步版本，供外部调用）
     */
    suspend fun exportLogsSync(outputPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "导出日志到: $outputPath")

            val logs = synchronized(logCache) {
                logCache.values.toList()
            }

            if (logs.isEmpty()) {
                Log.w(TAG, "没有日志可导出")
                return@withContext Result.failure(Exception("没有日志可导出"))
            }

            // 写入文件
            val file = java.io.File(outputPath)
            file.parentFile?.mkdirs()

            java.io.FileWriter(file).use { writer ->
                // 写入文件头
                writer.write("# Logcat 日志导出\n")
                writer.write("# 导出时间: ${dateFormat.format(Date())}\n")
                writer.write("# 日志数量: ${logs.size}\n")
                writer.write("# 关键字: ${LOG_KEYWORDS.joinToString(", ")}\n")
                writer.write("# " + "=".repeat(80) + "\n\n")

                // 写入日志
                logs.forEach { log ->
                    writer.write(log)
                    writer.write("\n")
                }
            }

            Log.d(TAG, "日志导出成功: ${file.absolutePath} (${logs.size} 条)")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取所有缓存的日志
     */
    fun getCachedLogs(): List<String> {
        return synchronized(logCache) {
            logCache.values.toList()
        }
    }

    /**
     * 获取缓存的日志数量
     */
    fun getLogCount(): Int {
        return synchronized(logCache) {
            logCache.size
        }
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        synchronized(logCache) {
            logCache.clear()
        }
        Log.d(TAG, "日志缓存已清空")
    }

    /**
     * 是否正在收集
     */
    fun isCollecting(): Boolean = isCollecting.get()
}
