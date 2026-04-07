package com.callcenter.app.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用生命周期管理器
 * 跟踪应用是否在前台运行
 */
object AppLifecycleManager {

    private const val TAG = "AppLifecycleManager"

    // 应用是否在前台
    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    // 当前活跃的 Activity 数量
    private var activeActivityCount = 0

    // 当前处于 onResume 状态的 Activity
    private var resumedActivityCount = 0

    /**
     * 初始化应用生命周期监听
     * 在 Application.onCreate() 中调用
     */
    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                activeActivityCount++
                updateForegroundState()
                Log.d(TAG, "Activity started: ${activity.javaClass.simpleName}, activeCount=$activeActivityCount")
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivityCount++
                updateForegroundState()
                Log.d(TAG, "Activity resumed: ${activity.javaClass.simpleName}, resumedCount=$resumedActivityCount")
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivityCount--
                updateForegroundState()
                Log.d(TAG, "Activity paused: ${activity.javaClass.simpleName}, resumedCount=$resumedActivityCount")
            }

            override fun onActivityStopped(activity: Activity) {
                activeActivityCount--
                updateForegroundState()
                Log.d(TAG, "Activity stopped: ${activity.javaClass.simpleName}, activeCount=$activeActivityCount")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * 更新前台状态
     * 严格定义：至少有一个 Activity 处于 onResume 状态才算前台
     */
    private fun updateForegroundState() {
        // 严格模式：必须有 Activity 处于 onResume 状态才算前台
        val isInForeground = resumedActivityCount > 0
        if (_isAppInForeground.value != isInForeground) {
            _isAppInForeground.value = isInForeground
            Log.d(TAG, "应用前台状态变更: $isInForeground (resumed=$resumedActivityCount, active=$activeActivityCount)")
        }
    }

    /**
     * 检查应用是否在前台（严格模式）
     * 要求至少有一个 Activity 处于 onResume 状态
     */
    fun isAppInForeground(): Boolean {
        return _isAppInForeground.value
    }

    /**
     * 检查应用是否活跃（宽松模式）
     * 只要有 Activity 处于可见状态（onStart 但未 onStop）
     */
    fun isAppActive(): Boolean {
        return activeActivityCount > 0
    }
}
