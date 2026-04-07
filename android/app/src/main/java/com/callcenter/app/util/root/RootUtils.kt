package com.callcenter.app.util.root

import android.util.Log
import java.io.DataOutputStream
import java.io.File

/**
 * Root 权限检测和工具类
 */
object RootUtils {

    private const val TAG = "RootUtils"

    /**
     * 检查设备是否已 Root
     */
    fun isDeviceRooted(): Boolean {
        // 检查常见的 Root 文件
        val rootFiles = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/system/app/Superuser.apk"
        )

        for (file in rootFiles) {
            if (File(file).exists()) {
                Log.d(TAG, "找到 Root 文件: $file")
                return true
            }
        }

        // 检查是否可以执行 su 命令
        return canExecuteSu()
    }

    /**
     * 检查是否有 Root 权限（可以执行 su）
     */
    fun hasRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "检查 Root 权限失败: ${e.message}")
            false
        }
    }

    /**
     * 尝试获取 Root 权限
     */
    fun requestRootPermission(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "请求 Root 权限失败: ${e.message}")
            false
        }
    }

    /**
     * 执行 Root 命令
     * @param command 要执行的命令
     * @return 命令输出
     */
    fun executeRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c $command")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()

            if (error.isNotBlank()) {
                Log.e(TAG, "命令执行错误: $error")
            }

            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "执行 Root 命令失败: ${e.message}")
            ""
        }
    }

    /**
     * 执行 Root 命令（带超时）
     * @param command 要执行的命令
     * @param timeoutMs 超时时间（毫秒）
     * @return 命令输出
     */
    fun executeRootCommand(command: String, timeoutMs: Long): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c $command")

            // 使用线程读取输出
            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            val outputThread = Thread {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.append(line).append("\n")
                    }
                }
            }

            val errorThread = Thread {
                process.errorStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorBuilder.append(line).append("\n")
                    }
                }
            }

            outputThread.start()
            errorThread.start()

            // 等待进程完成或超时
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG, "命令执行超时: $command")
                return ""
            }

            outputThread.join(1000)
            errorThread.join(1000)

            val error = errorBuilder.toString()
            if (error.isNotBlank()) {
                Log.e(TAG, "命令执行错误: $error")
            }

            outputBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "执行 Root 命令失败: ${e.message}")
            ""
        }
    }

    /**
     * 检查是否可以执行 su 命令
     */
    private fun canExecuteSu(): Boolean {
        return try {
            Runtime.getRuntime().exec("su --version")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取 Root 管理器类型
     */
    fun getRootManagerType(): String {
        return when {
            File("/data/adb/magisk").exists() -> "Magisk"
            File("/system/xbin/su").exists() -> "SuperSU"
            File("/system/bin/su").exists() -> "系统 Root"
            else -> "未知"
        }
    }

    /**
     * 清除 logcat 缓冲区
     */
    fun clearLogcatBuffer(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c logcat -c")
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "清除 logcat 失败: ${e.message}")
            false
        }
    }
}
