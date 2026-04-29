package com.callcenter.app.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 防抖动工具类
 *
 * 用于防止按钮多次快速点击，避免重复提交或重复打开页面
 */

/**
 * 默认防抖间隔时间（毫秒）
 */
const val DEFAULT_DEBOUNCE_INTERVAL = 500L

/**
 * 防抖点击状态管理
 * 使用单例模式确保全局只有一个实例
 */
object DebounceState {
    private val mutex = Mutex()
    private var lastClickTime = 0L

    /**
     * 检查是否可以执行点击
     * @param interval 防抖间隔时间（毫秒）
     * @return 是否可以执行点击
     */
    suspend fun canClick(interval: Long = DEFAULT_DEBOUNCE_INTERVAL): Boolean {
        return mutex.withLock {
            val currentTime = System.currentTimeMillis()
            val canClick = currentTime - lastClickTime >= interval

            if (canClick) {
                lastClickTime = currentTime
            }
            canClick
        }
    }

    /**
     * 重置状态
     */
    suspend fun reset() {
        mutex.withLock {
            lastClickTime = 0
        }
    }
}

/**
 * 防抖点击 Modifier
 *
 * 使用方式：
 * ```kotlin
 * Button(
 *     modifier = Modifier.debounceClick { /* 点击处理 */ },
 *     onClick = {}
 * )
 * ```
 *
 * 或者对于 Card、ListItem 等组件：
 * ```kotlin
 * Card(
 *     modifier = Modifier.debounceClick { /* 点击处理 */ }
 * ) { ... }
 * ```
 *
 * @param enabled 是否启用点击
 * @param interval 防抖间隔时间（毫秒）
 * @param onClick 点击回调
 */
fun Modifier.debounceClick(
    enabled: Boolean = true,
    interval: Long = DEFAULT_DEBOUNCE_INTERVAL,
    onClick: () -> Unit
): Modifier = composed {
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }

    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) {
        coroutineScope.launch {
            if (DebounceState.canClick(interval)) {
                onClick()
            }
        }
    }
}

/**
 * 防抖点击回调生成器
 *
 * 用于 Button、TextButton、OutlinedButton 等组件的 onClick 参数
 *
 * 使用方式：
 * ```kotlin
 * Button(
 *     onClick = rememberDebounceOnClick { /* 点击处理 */ }
 * ) { ... }
 * ```
 *
 * @param interval 防抖间隔时间（毫秒）
 * @param onClick 点击回调
 */
@Composable
fun rememberDebounceOnClick(
    interval: Long = DEFAULT_DEBOUNCE_INTERVAL,
    onClick: () -> Unit
): () -> Unit {
    val coroutineScope = remember { CoroutineScope(Dispatchers.Main) }

    return remember(interval, onClick) {
        {
            coroutineScope.launch {
                if (DebounceState.canClick(interval)) {
                    onClick()
                }
            }
        }
    }
}

/**
 * 扩展函数：为普通 onClick 添加防抖功能
 *
 * 使用方式：
 * ```kotlin
 * Button(
 *     onClick = { /* 点击处理 */ }.withDebounce()
 * ) { ... }
 * ```
 */
fun (() -> Unit).withDebounce(
    interval: Long = DEFAULT_DEBOUNCE_INTERVAL
): () -> Unit {
    val originalClick = this
    return {
        CoroutineScope(Dispatchers.Main).launch {
            if (DebounceState.canClick(interval)) {
                originalClick()
            }
        }
    }
}
