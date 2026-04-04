package com.callcenter.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.callcenter.app.data.model.Customer
import com.callcenter.app.service.AutoDialService
import com.callcenter.app.service.CallStatusCallback
import com.callcenter.app.service.FloatingCustomerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗管理器
 * 管理悬浮窗客户信息面板的显示/隐藏
 */
object FloatingWindowManager {

    private const val TAG = "FloatingWindowManager"

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    /**
     * 设置通话状态标记回调
     */
    fun setCallStatusCallback(callback: CallStatusCallback) {
        FloatingCustomerService.callStatusCallback = callback
    }

    /**
     * 清除通话状态标记回调
     */
    fun clearCallStatusCallback() {
        FloatingCustomerService.callStatusCallback = null
    }

    /**
     * 检查是否有悬浮窗权限
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    /**
     * 显示悬浮窗客户信息面板
     */
    fun showFloatingWindow(
        context: Context,
        customer: Customer?,
        dialedCount: Int = 0,
        totalCount: Int = 0,
        isCalling: Boolean = false
    ) {
        if (!canDrawOverlays(context)) {
            Log.w(TAG, "没有悬浮窗权限，无法显示悬浮窗")
            return
        }

        val intent = Intent(context, FloatingCustomerService::class.java).apply {
            action = FloatingCustomerService.ACTION_SHOW
            putExtra(FloatingCustomerService.EXTRA_CUSTOMER, customer)
            putExtra(FloatingCustomerService.EXTRA_DIALED_COUNT, dialedCount)
            putExtra(FloatingCustomerService.EXTRA_TOTAL_COUNT, totalCount)
            putExtra(FloatingCustomerService.EXTRA_IS_CALLING, isCalling)
        }
        context.startService(intent)
        _isShowing.value = true
    }

    /**
     * 隐藏悬浮窗客户信息面板
     */
    fun hideFloatingWindow(context: Context) {
        val intent = Intent(context, FloatingCustomerService::class.java).apply {
            action = FloatingCustomerService.ACTION_HIDE
        }
        context.startService(intent)
        _isShowing.value = false
    }

    /**
     * 更新悬浮窗中的客户信息
     */
    fun updateCustomer(
        context: Context,
        customer: Customer?,
        isCalling: Boolean = false
    ) {
        val intent = Intent(context, FloatingCustomerService::class.java).apply {
            action = FloatingCustomerService.ACTION_UPDATE_CUSTOMER
            putExtra(FloatingCustomerService.EXTRA_CUSTOMER, customer)
            putExtra(FloatingCustomerService.EXTRA_IS_CALLING, isCalling)
        }
        context.startService(intent)
    }

    /**
     * 更新悬浮窗中的进度信息
     */
    fun updateProgress(
        context: Context,
        dialedCount: Int,
        totalCount: Int
    ) {
        val intent = Intent(context, FloatingCustomerService::class.java).apply {
            action = FloatingCustomerService.ACTION_UPDATE_PROGRESS
            putExtra(FloatingCustomerService.EXTRA_DIALED_COUNT, dialedCount)
            putExtra(FloatingCustomerService.EXTRA_TOTAL_COUNT, totalCount)
        }
        context.startService(intent)
    }
}

/**
 * Composable函数：自动管理悬浮窗显示
 * 在自动拨号过程中自动显示/更新/隐藏悬浮窗
 */
@Composable
fun AutoFloatingWindow(
    enabled: Boolean = true,
    onCallStatusMarked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    // 收集自动拨号服务的状态
    val isRunning by AutoDialService.isRunning.collectAsState()
    val currentCustomer by AutoDialService.currentCustomer.collectAsState()
    val dialedCount by AutoDialService.dialedCount.collectAsState()

    // 设置通话状态标记回调
    LaunchedEffect(onCallStatusMarked) {
        onCallStatusMarked?.let { callback ->
            FloatingWindowManager.setCallStatusCallback(object : CallStatusCallback {
                override fun onCallStatusMarked(status: String) {
                    callback(status)
                }
            })
        } ?: run {
            FloatingWindowManager.clearCallStatusCallback()
        }
    }

    // 监听自动拨号状态变化，控制悬浮窗
    LaunchedEffect(isRunning, currentCustomer, dialedCount, enabled) {
        if (!enabled) {
            if (FloatingWindowManager.isShowing.value) {
                FloatingWindowManager.hideFloatingWindow(context)
            }
            return@LaunchedEffect
        }

        if (isRunning) {
            // 自动拨号进行中，显示/更新悬浮窗
            if (!FloatingWindowManager.canDrawOverlays(context)) {
                return@LaunchedEffect
            }

            FloatingWindowManager.showFloatingWindow(
                context = context,
                customer = currentCustomer,
                dialedCount = dialedCount,
                totalCount = currentCustomer?.let { dialedCount + 1 } ?: dialedCount,
                isCalling = currentCustomer != null
            )
        } else {
            // 自动拨号停止，隐藏悬浮窗
            if (FloatingWindowManager.isShowing.value) {
                FloatingWindowManager.hideFloatingWindow(context)
            }
        }
    }

    // 组件销毁时隐藏悬浮窗
    DisposableEffect(Unit) {
        onDispose {
            if (FloatingWindowManager.isShowing.value) {
                FloatingWindowManager.hideFloatingWindow(context)
            }
            FloatingWindowManager.clearCallStatusCallback()
        }
    }
}
