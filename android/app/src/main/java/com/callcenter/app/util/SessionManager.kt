package com.callcenter.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话状态事件
 */
sealed class SessionEvent {
    /**
     * Token过期，需要重新登录
     */
    data object TokenExpired : SessionEvent()

    /**
     * Token无效
     */
    data object TokenInvalid : SessionEvent()

    /**
     * 被踢出（其他设备登录）
     */
    data class KickedOut(val reason: String) : SessionEvent()
}

/**
 * 会话管理器
 *
 * 用于管理用户会话状态，处理token过期等事件
 *
 * 使用方式：
 * 1. 在网络拦截器中检测到401时，调用 [notifyTokenExpired]
 * 2. 在UI层监听 [sessionEvent] Flow，收到事件后跳转到登录页
 */
@Singleton
class SessionManager @Inject constructor() {

    private val _sessionEvent = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1)
    val sessionEvent: SharedFlow<SessionEvent> = _sessionEvent.asSharedFlow()

    /**
     * 通知Token过期
     */
    fun notifyTokenExpired() {
        _sessionEvent.tryEmit(SessionEvent.TokenExpired)
    }

    /**
     * 通知Token无效
     */
    fun notifyTokenInvalid() {
        _sessionEvent.tryEmit(SessionEvent.TokenInvalid)
    }

    /**
     * 通知被踢出
     */
    fun notifyKickedOut(reason: String = "您的账号已在其他设备登录") {
        _sessionEvent.tryEmit(SessionEvent.KickedOut(reason))
    }
}
