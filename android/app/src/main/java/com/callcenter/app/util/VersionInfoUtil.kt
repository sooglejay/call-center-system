package com.callcenter.app.util

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 版本信息工具类
 * 用于读取 release notes
 */
object VersionInfoUtil {

    /**
     * 读取 release notes 内容
     * @param context 上下文
     * @return release notes 文本内容
     */
    fun readReleaseNotes(context: Context): String {
        return try {
            context.assets.open("RELEASE_NOTES.md").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            // 如果读取失败，返回默认内容
            getDefaultReleaseNotes()
        }
    }

    /**
     * 获取默认的 release notes
     */
    private fun getDefaultReleaseNotes(): String {
        return """
            # 智能呼叫中心

            ## v1.1.0

            ### 新功能
            - 客户列表整合：显示所有任务中的客户 + 分配给自己的客户
            - 自动拨号配置：支持设置拨号间隔、超时时间、每客户拨打次数
            - 客户去重：同一客户存在于多个任务中时自动去重
            - 任务客户编辑：支持编辑客户信息和删除客户

            ### 优化
            - 自动拨号进度保存
            - 客户列表搜索和筛选

            ### 修复
            - 修复客户Tab无数据的问题
        """.trimIndent()
    }
}
