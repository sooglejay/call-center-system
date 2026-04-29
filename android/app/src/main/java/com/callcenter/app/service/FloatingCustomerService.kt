package com.callcenter.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.callcenter.app.CallCenterApp
import com.callcenter.app.MainActivity
import com.callcenter.app.R
import com.callcenter.app.data.model.Customer
import com.callcenter.app.ui.theme.CallCenterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通话状态历史记录项
 */
data class CallStateHistoryItem(
    val timestamp: Long = System.currentTimeMillis(),
    val state: String,           // 状态描述
    val number: String?,         // 号码
    val customerName: String?,   // 客户名字
    val duration: Long = 0       // 通话时长（毫秒）
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("MM月dd日 HH:mm:ss", Locale.CHINA)
        return sdf.format(Date(timestamp))
    }
}

/**
 * 通话状态标记回调接口
 */
interface CallStatusCallback {
    /**
     * 标记通话状态
     * @param status 状态值：connected(已接听), voicemail(语音信箱), unanswered(未接听)
     * @param onComplete 完成回调，在状态更新成功后调用
     */
    fun onCallStatusMarked(status: String, onComplete: (() -> Unit)? = null)
}

/**
 * 悬浮窗客户信息服务
 * 在自动拨号时显示可拖拽的悬浮客户信息面板
 */
@AndroidEntryPoint
class FloatingCustomerService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_SHOW = "com.callcenter.app.action.FLOATING_SHOW"
        const val ACTION_HIDE = "com.callcenter.app.action.FLOATING_HIDE"
        const val ACTION_UPDATE_CUSTOMER = "com.callcenter.app.action.UPDATE_CUSTOMER"
        const val ACTION_UPDATE_PROGRESS = "com.callcenter.app.action.UPDATE_PROGRESS"

        const val EXTRA_CUSTOMER = "customer"
        const val EXTRA_DIALED_COUNT = "dialed_count"
        const val EXTRA_TOTAL_COUNT = "total_count"
        const val EXTRA_IS_CALLING = "is_calling"

        private const val NOTIFICATION_ID = 1003
        private const val TAG = "FloatingCustomerService"

        // 状态流供外部观察
        private val _isShowing = MutableStateFlow(false)
        val isShowing: StateFlow<Boolean> = _isShowing

        private val _currentCustomer = MutableStateFlow<Customer?>(null)
        val currentCustomer: StateFlow<Customer?> = _currentCustomer

        private val _dialProgress = MutableStateFlow<Pair<Int, Int>?>(null)
        val dialProgress: StateFlow<Pair<Int, Int>?> = _dialProgress

        // 通话状态标记回调
        var callStatusCallback: CallStatusCallback? = null

        // 是否折叠状态
        private val _isCollapsed = MutableStateFlow(false)
        val isCollapsed: StateFlow<Boolean> = _isCollapsed

        fun setCollapsed(isCollapsed: Boolean) {
            _isCollapsed.value = isCollapsed
        }

        // 状态文本
        private val _statusText = MutableStateFlow("")
        val statusText: StateFlow<String> = _statusText

        // 通话状态历史记录
        private val _callStateHistory = MutableStateFlow<List<CallStateHistoryItem>>(emptyList())
        val callStateHistory: StateFlow<List<CallStateHistoryItem>> = _callStateHistory

        /**
         * 添加通话状态历史记录
         */
        fun addCallStateHistory(state: String, number: String? = null, customerName: String? = null, duration: Long = 0) {
            val newItem = CallStateHistoryItem(
                state = state,
                number = number,
                customerName = customerName,
                duration = duration
            )
            _callStateHistory.value = _callStateHistory.value + newItem
        }

        /**
         * 清空通话状态历史记录
         */
        fun clearCallStateHistory() {
            _callStateHistory.value = emptyList()
        }

        /**
         * 更新状态文本
         */
        fun updateStatus(status: String) {
            _statusText.value = status
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var lastCustomer: Customer? = null
    private var lastDialedCount: Int = 0
    private var lastTotalCount: Int = 0
    private var isCalling: Boolean = false

    // 触摸事件相关
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private val CLICK_DRAG_THRESHOLD = 15
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME = 300L

    inner class LocalBinder : Binder() {
        fun getService(): FloatingCustomerService = this@FloatingCustomerService
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val customer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_CUSTOMER, Customer::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_CUSTOMER) as? Customer
                }
                val dialedCount = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)
                val totalCount = intent.getIntExtra(EXTRA_TOTAL_COUNT, 0)
                val calling = intent.getBooleanExtra(EXTRA_IS_CALLING, false)
                showFloatingWindow(customer, dialedCount, totalCount, calling)
            }
            ACTION_HIDE -> {
                hideFloatingWindow()
            }
            ACTION_UPDATE_CUSTOMER -> {
                val customer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_CUSTOMER, Customer::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_CUSTOMER) as? Customer
                }
                val calling = intent.getBooleanExtra(EXTRA_IS_CALLING, false)
                updateCustomer(customer, calling)
            }
            ACTION_UPDATE_PROGRESS -> {
                val dialedCount = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)
                val totalCount = intent.getIntExtra(EXTRA_TOTAL_COUNT, 0)
                updateProgress(dialedCount, totalCount)
            }
        }
        return START_STICKY
    }

    private fun showFloatingWindow(
        customer: Customer?,
        dialedCount: Int,
        totalCount: Int,
        calling: Boolean
    ) {
        if (floatingView != null) {
            // 已经显示，更新内容
            updateCustomer(customer, calling)
            updateProgress(dialedCount, totalCount)
            return
        }

        lastCustomer = customer
        lastDialedCount = dialedCount
        lastTotalCount = totalCount
        isCalling = calling

        // 创建前台服务通知
        startForeground()

        // 创建悬浮窗参数
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // 创建ComposeView
        val composeView = ComposeView(this).apply {
            setContent {
                CallCenterTheme {
                    val isCollapsed by FloatingCustomerService.isCollapsed.collectAsState()
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        isCollapsed = isCollapsed,
                        onClose = { hideFloatingWindow() },
                        onToggleCollapse = { FloatingCustomerService.setCollapsed(!isCollapsed) },
                        onCallStatusMarked = { status, onComplete ->
                            callStatusCallback?.onCallStatusMarked(status, onComplete)
                        }
                    )
                }
            }
        }

        // 创建一个透明的容器View来处理触摸事件
        val containerView = object : FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                return handleTouchEvent(event) || super.onTouchEvent(event)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                // 拦截触摸事件以便处理拖拽
                handleTouchEvent(ev)
                return isDragging
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // 设置 LifecycleOwner 到容器
            setViewTreeLifecycleOwner(this@FloatingCustomerService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCustomerService)
            addView(composeView)
        }

        floatingView = containerView

        try {
            windowManager?.addView(floatingView, params)
            _isShowing.value = true
            _currentCustomer.value = customer
            _dialProgress.value = dialedCount to totalCount
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Log.d(TAG, "悬浮窗显示成功")
        } catch (e: android.view.WindowManager.BadTokenException) {
            Log.e(TAG, "显示悬浮窗失败: BadTokenException - ${e.message}")
            // 清理资源
            floatingView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (ignored: Exception) {}
            }
            floatingView = null
            _isShowing.value = false
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败: ${e.message}")
            stopSelf()
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val params = this.params ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()

                // 判断是否开始拖拽
                if (!isDragging && (kotlin.math.abs(deltaX) > CLICK_DRAG_THRESHOLD ||
                    kotlin.math.abs(deltaY) > CLICK_DRAG_THRESHOLD)) {
                    isDragging = true
                }

                // 只有在拖拽状态下才更新位置
                if (isDragging) {
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val currentTime = System.currentTimeMillis()
                val wasDragging = isDragging
                isDragging = false

                // 如果不是拖拽操作，则视为点击
                if (!wasDragging) {
                    // 处理点击事件（可以添加点击穿透或其他逻辑）
                    Log.d(TAG, "悬浮窗被点击")
                }

                lastClickTime = currentTime
                return wasDragging
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    private var isShowingWindow = false

    private fun updateCustomer(customer: Customer?, calling: Boolean) {
        lastCustomer = customer
        isCalling = calling
        _currentCustomer.value = customer

        // 如果悬浮窗未显示，先显示它
        if (floatingView == null && !isShowingWindow) {
            Log.w(TAG, "悬浮窗未显示，尝试重新显示")
            isShowingWindow = true
            showFloatingWindow(customer, lastDialedCount, lastTotalCount, calling)
            isShowingWindow = false
            return
        }

        // 刷新Compose内容
        (floatingView as? FrameLayout)?.getChildAt(0)?.let { childView ->
            (childView as? ComposeView)?.setContent {
                CallCenterTheme {
                    val isCollapsed by FloatingCustomerService.isCollapsed.collectAsState()
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        isCollapsed = isCollapsed,
                        onClose = { hideFloatingWindow() },
                        onToggleCollapse = { FloatingCustomerService.setCollapsed(!isCollapsed) },
                        onCallStatusMarked = { status, onComplete ->
                            callStatusCallback?.onCallStatusMarked(status, onComplete)
                        }
                    )
                }
            }
        }

        // 更新通知
        updateNotification()
    }

    private fun updateProgress(dialedCount: Int, totalCount: Int) {
        lastDialedCount = dialedCount
        lastTotalCount = totalCount
        _dialProgress.value = dialedCount to totalCount

        // 如果悬浮窗未显示，不执行更新
        if (floatingView == null) {
            Log.w(TAG, "悬浮窗未显示，跳过进度更新")
            return
        }

        // 刷新Compose内容
        (floatingView as? FrameLayout)?.getChildAt(0)?.let { childView ->
            (childView as? ComposeView)?.setContent {
                CallCenterTheme {
                    val isCollapsed by FloatingCustomerService.isCollapsed.collectAsState()
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        isCollapsed = isCollapsed,
                        onClose = { hideFloatingWindow() },
                        onToggleCollapse = { FloatingCustomerService.setCollapsed(!isCollapsed) },
                        onCallStatusMarked = { status, onComplete ->
                            callStatusCallback?.onCallStatusMarked(status, onComplete)
                        }
                    )
                }
            }
        }
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败: ${e.message}")
            }
        }
        floatingView = null
        _isShowing.value = false
        _currentCustomer.value = null
        _dialProgress.value = null

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForeground() {
        val notification = createNotification()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val customerInfo = lastCustomer?.let {
            "${it.name} | ${it.phone}"
        } ?: "准备中..."

        return NotificationCompat.Builder(this, CallCenterApp.CHANNEL_ID_AUTO_DIAL)
            .setContentTitle("自动拨号中 - $customerInfo")
            .setContentText("进度: $lastDialedCount/$lastTotalCount")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * 悬浮客户信息面板Compose组件
 * 支持折叠/展开两种状态
 */
@Composable
private fun FloatingCustomerPanel(
    customer: Customer?,
    dialedCount: Int,
    totalCount: Int,
    isCalling: Boolean,
    isCollapsed: Boolean,
    onClose: () -> Unit,
    onToggleCollapse: () -> Unit,
    onCallStatusMarked: (String, (() -> Unit)?) -> Unit
) {
    // 根据折叠状态显示不同的UI
    if (isCollapsed) {
        // 折叠状态：显示圆形 Floating Button
        CollapsedFloatingButton(
            isCalling = isCalling,
            onToggleCollapse = onToggleCollapse
        )
    } else {
        // 展开状态：显示完整的悬浮窗
        ExpandedFloatingPanel(
            customer = customer,
            dialedCount = dialedCount,
            totalCount = totalCount,
            isCalling = isCalling,
            onClose = onClose,
            onToggleCollapse = onToggleCollapse
        )
    }
}

/**
 * 折叠状态的圆形 Floating Button
 */
@Composable
private fun CollapsedFloatingButton(
    isCalling: Boolean,
    onToggleCollapse: () -> Unit
) {
    Surface(
        onClick = onToggleCollapse,
        shape = CircleShape,
        color = when {
            isCalling -> Color(0xFF4CAF50)
            else -> MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.size(56.dp),
        shadowElevation = 4.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "展开",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * 展开状态的完整悬浮窗面板
 */
@Composable
private fun ExpandedFloatingPanel(
    customer: Customer?,
    dialedCount: Int,
    totalCount: Int,
    isCalling: Boolean,
    onClose: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    val nameLine = customer?.let { formatFloatingCustomerNameLine(it) } ?: "未命名"
    val primaryName = customer?.let { getPrimaryCustomerName(it) } ?: "?"
    val phoneLine = customer?.phone?.takeIf { it.isNotBlank() } ?: "暂无电话号码"
    val emailLine = customer?.email?.takeIf { it.isNotBlank() } ?: "暂无邮箱地址"

    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题栏：模式标识、进度和关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：状态指示器 + 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态指示器
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCalling -> Color(0xFF4CAF50)
                                    else -> Color(0xFFFFA726)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "自动拨号",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$dialedCount / $totalCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 右侧：折叠按钮和停止拨号按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 折叠按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "折叠",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "折叠",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 停止拨号按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = {
                                // 停止拨号服务
                                val intent = Intent(context, AutoDialService::class.java).apply {
                                    action = AutoDialService.ACTION_STOP
                                }
                                context.startService(intent)
                                onClose()  // 同时关闭悬浮窗
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "停止",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // 进度条
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            // 客户信息卡片
            Spacer(modifier = Modifier.height(12.dp))
            if (customer != null) {
                // 正在拨号的客户信息提示
                if (isCalling) {
                    Text(
                        text = "正在拨号的客户信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 客户信息行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 客户头像
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = primaryName.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 客户信息
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nameLine,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = phoneLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = emailLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 通话状态标记按钮（已隐藏，完全依靠程序自动识别打标）
                // 自动识别逻辑在 AutoDialService.setupCallStateListener() 中实现
            } else {
                // 等待状态
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "准备拨打下一个客户...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFloatingCustomerNameLine(customer: Customer): String {
    val rawName = customer.name?.trim().orEmpty()
    if (rawName.isBlank()) return "未命名"

    val chineseName = rawName.filter { isChineseChar(it) }.trim()
    val englishName = rawName
        .replace(Regex("[\u4E00-\u9FFF]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return when {
        chineseName.isNotBlank() && englishName.isNotBlank() -> "$chineseName $englishName"
        else -> rawName
    }
}

private fun getPrimaryCustomerName(customer: Customer): String {
    val rawName = customer.name?.trim().orEmpty()
    if (rawName.isBlank()) return "?"

    val chineseName = rawName.filter { isChineseChar(it) }.trim()
    return if (chineseName.isNotBlank()) chineseName else rawName
}

private fun isChineseChar(char: Char): Boolean {
    return char in '\u4E00'..'\u9FFF'
}

