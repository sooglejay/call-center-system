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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
import com.callcenter.app.data.local.preferences.AutoDialSettingsManager
import com.callcenter.app.data.model.Customer
import com.callcenter.app.ui.theme.CallCenterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * 通话状态标记回调接口
 */
interface CallStatusCallback {
    fun onCallStatusMarked(status: String)
}

/**
 * 手动拨号回调接口
 */
interface ManualDialCallback {
    fun onDialNext()
    fun onAutoDialModeChanged(isAutoMode: Boolean)
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

        // 手动拨号回调
        var manualDialCallback: ManualDialCallback? = null

        // 是否自动拨号模式（默认手动模式，更安全）
        private val _isAutoDialMode = MutableStateFlow(false)
        val isAutoDialMode: StateFlow<Boolean> = _isAutoDialMode

        fun setAutoDialMode(isAuto: Boolean) {
            _isAutoDialMode.value = isAuto
            // 保存到本地
            val context = CallCenterApp.instance?.applicationContext
            context?.let {
                kotlinx.coroutines.GlobalScope.launch {
                    AutoDialSettingsManager(it).setAutoDialMode(isAuto)
                }
            }
        }
    }

    private lateinit var settingsManager: AutoDialSettingsManager
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

        // 初始化设置管理器并加载保存的设置
        settingsManager = AutoDialSettingsManager(this)
        serviceScope.launch {
            settingsManager.isAutoDialModeFlow.collect { isAutoMode ->
                _isAutoDialMode.value = isAutoMode
            }
        }
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
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        onClose = { hideFloatingWindow() },
                        onCallStatusMarked = { status ->
                            callStatusCallback?.onCallStatusMarked(status)
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

    private fun updateCustomer(customer: Customer?, calling: Boolean) {
        lastCustomer = customer
        isCalling = calling
        _currentCustomer.value = customer

        // 刷新Compose内容
        (floatingView as? FrameLayout)?.getChildAt(0)?.let { childView ->
            (childView as? ComposeView)?.setContent {
                CallCenterTheme {
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        onClose = { hideFloatingWindow() },
                        onCallStatusMarked = { status ->
                            callStatusCallback?.onCallStatusMarked(status)
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

        // 刷新Compose内容
        (floatingView as? FrameLayout)?.getChildAt(0)?.let { childView ->
            (childView as? ComposeView)?.setContent {
                CallCenterTheme {
                    FloatingCustomerPanel(
                        customer = lastCustomer,
                        dialedCount = lastDialedCount,
                        totalCount = lastTotalCount,
                        isCalling = isCalling,
                        onClose = { hideFloatingWindow() },
                        onCallStatusMarked = { status ->
                            callStatusCallback?.onCallStatusMarked(status)
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
 */
@Composable
private fun FloatingCustomerPanel(
    customer: Customer?,
    dialedCount: Int,
    totalCount: Int,
    isCalling: Boolean,
    onClose: () -> Unit,
    onCallStatusMarked: (String) -> Unit
) {
    // 使用 remember 来保持状态，避免重组时丢失
    var isAutoMode by remember { mutableStateOf(FloatingCustomerService.isAutoDialMode.value) }

    Card(
        modifier = Modifier
            .width(340.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
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
                                    isAutoMode -> MaterialTheme.colorScheme.primary
                                    else -> Color(0xFFFFA726)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isAutoMode) "自动拨号" else "手动拨号",
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

                // 右侧：自动开关 + 关闭按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 自动/手动切换开关
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "自动",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAutoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = isAutoMode,
                            onCheckedChange = { checked ->
                                isAutoMode = checked
                                FloatingCustomerService.setAutoDialMode(checked)
                                FloatingCustomerService.manualDialCallback?.onAutoDialModeChanged(checked)
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 进度条
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            // 客户信息卡片
            Spacer(modifier = Modifier.height(16.dp))
            if (customer != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 头像
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = if (isCalling) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCalling) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = customer.name?.firstOrNull()?.toString() ?: "?",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 客户信息
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.name ?: "未知客户",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = customer.phone ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                            if (!customer.company.isNullOrBlank()) {
                                Text(
                                    text = customer.company,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 右侧：拨打下一个按钮（手动模式下显示）
                        if (!isAutoMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    FloatingCustomerService.manualDialCallback?.onDialNext()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "拨打下一个",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // 通话状态标记按钮（只在通话中显示）
                if (isCalling) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "标记通话结果：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 已接听按钮
                        StatusButton(
                            text = "已接听",
                            color = Color(0xFF4CAF50),
                            onClick = { onCallStatusMarked("connected") },
                            modifier = Modifier.weight(1f)
                        )
                        // 语音信箱按钮
                        StatusButton(
                            text = "语音信箱",
                            color = Color(0xFF2196F3),
                            onClick = { onCallStatusMarked("voicemail") },
                            modifier = Modifier.weight(1f)
                        )
                        // 未接听按钮
                        StatusButton(
                            text = "未接听",
                            color = Color(0xFFFF9800),
                            onClick = { onCallStatusMarked("unanswered") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 手动模式下显示"拨打下一个"按钮（当不在通话中时）
                if (!isAutoMode && !isCalling) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NextDialButton(
                        onClick = {
                            FloatingCustomerService.manualDialCallback?.onDialNext()
                        }
                    )
                }
            } else {
                // 无客户信息状态 - 等待中
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isAutoMode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "准备拨打下一个客户...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "等待拨打下一个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 手动模式下显示"拨打下一个"按钮
                if (!isAutoMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NextDialButton(
                        onClick = {
                            FloatingCustomerService.manualDialCallback?.onDialNext()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 状态标记按钮组件
 */
@Composable
private fun StatusButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * 拨打下一个按钮组件
 */
@Composable
private fun NextDialButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "拨打下一个",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
