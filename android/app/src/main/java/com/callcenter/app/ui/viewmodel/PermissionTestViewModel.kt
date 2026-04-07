package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 权限测试页面 ViewModel
 */
@HiltViewModel
class PermissionTestViewModel @Inject constructor() : ViewModel() {
    // 可以在这里添加一些状态管理逻辑
    // 目前主要逻辑都在 Composable 中处理
}
