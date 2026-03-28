package com.callcenter.app.data.model

import java.io.Serializable

/**
 * 通话设置
 */
data class CallSettings(
    /**
     * 自动拨号间隔（秒）
     */
    val autoDialInterval: Int = 10,

    /**
     * 通话超时时间（秒）
     */
    val callTimeout: Int = 30,

    /**
     * 自动重试次数
     */
    val retryCount: Int = 0,

    /**
     * 拨号后自动打开免提
     */
    val autoSpeaker: Boolean = false,

    /**
     * 通话结束后自动添加备注
     */
    val autoAddNote: Boolean = false,

    /**
     * 默认通话备注模板
     */
    val defaultNoteTemplate: String = ""
) : Serializable
