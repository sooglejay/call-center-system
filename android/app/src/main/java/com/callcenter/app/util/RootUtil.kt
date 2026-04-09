package com.callcenter.app.util

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root 权限工具类
 * 用于在已 root 的设备上执行 shell 命令
 */
object RootUtil {

    private const val TAG = "RootUtil"

    /**
     * 检查设备是否已获取 root 权限
     */
    fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "检查 root 权限失败: ${e.message}")
            false
        }
    }

    /**
     * 使用 root 权限执行 shell 命令
     * @param command 要执行的命令
     * @return 执行结果
     */
    fun executeWithRoot(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = StringBuilder()
            val error = StringBuilder()

            // 读取标准输出
            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // 读取错误输出
            while (errorStream.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }

            val exitCode = process.waitFor()

            inputStream.close()
            errorStream.close()
            outputStream.close()

            CommandResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行 root 命令失败: ${e.message}")
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }

    /**
     * 使用 root 权限执行多条 shell 命令
     * @param commands 命令列表
     * @return 执行结果
     */
    fun executeMultipleWithRoot(commands: List<String>): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            commands.forEach { command ->
                outputStream.writeBytes("$command\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = StringBuilder()
            val error = StringBuilder()

            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            while (errorStream.readLine().also { line = it } != null) {
                error.append(line).append("\n")
            }

            val exitCode = process.waitFor()

            inputStream.close()
            errorStream.close()
            outputStream.close()

            CommandResult(
                success = exitCode == 0,
                output = output.toString().trim(),
                error = error.toString().trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行多条 root 命令失败: ${e.message}")
            CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }

    /**
     * 开启免提（使用 AudioManager 方式）
     * 在 root 设备上更可靠
     */
    fun enableSpeakerphoneWithRoot(): Boolean {
        // 方法1: 使用 service call audio 命令
        val result1 = executeWithRoot("service call audio 24 i32 1 i32 0")
        if (result1.success) {
            Log.d(TAG, "免提已开启 (service call audio)")
            return true
        }

        // 方法2: 使用 am 命令设置音频路由
        val result2 = executeWithRoot("am broadcast -a android.media.ACTION_SCO_AUDIO_STATE_UPDATED --ei android.media.extra.SCO_AUDIO_STATE 1")

        // 方法3: 直接修改系统设置
        val result3 = executeWithRoot("settings put global speakerphone_on 1")

        Log.d(TAG, "尝试开启免提: service=$result1.success, broadcast=$result2.success, settings=$result3.success")
        return result1.success || result2.success || result3.success
    }

    /**
     * 通过修改音频路由强制开启扬声器
     */
    fun forceEnableSpeaker(): Boolean {
        val commands = listOf(
            // 尝试切到通信模式
            "cmd audio set-mode communication",
            "cmd audio set-mode in_communication",
            // 某些系统可直接设置免提
            "cmd audio set-speakerphone-on true",
            // 兼容旧版 service call audio
            "service call audio 2 i32 3",
            "service call audio 2 i32 2",
            "service call audio 24 i32 1 i32 0",
            "service call audio 28 i32 1",
            // 系统设置兜底
            "settings put system mode_ringer_streams_affected 0",
            "settings put global speakerphone_on 1"
        )

        val result = executeMultipleWithRoot(commands)
        Log.d(TAG, "强制开启扬声器结果: ${result.success}, output=${result.output}, error=${result.error}")
        return result.success
    }
}

/**
 * 命令执行结果
 */
data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String,
    val exitCode: Int
)
