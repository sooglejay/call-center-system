package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.callcenter.app.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 会话管理ViewModel
 *
 * 用于在Compose中监听会话过期事件
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    val sessionManager: SessionManager
) : ViewModel()
