package com.callcenter.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.ui.viewmodel.PermissionTestViewModel
import com.callcenter.app.ui.viewmodel.SettingsViewModel
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.util.root.RootCallState
import com.callcenter.app.util.root.RootCallStateDetector
import com.callcenter.app.util.root.RootPhoneStateListener
import com.callcenter.app.util.root.RootUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 权限测试页面
 * 检测 Root、电话、通讯录、通话状态、录音等权限
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionTestScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeatureToggles: () -> Unit = {},
    viewModel: PermissionTestViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 权限状态
    var rootStatus by remember { mutableStateOf(PermissionStatus.UNKNOWN) }
    var phoneStatus by remember { mutableStateOf(PermissionStatus.UNKNOWN) }
    var callLogStatus by remember { mutableStateOf(PermissionStatus.UNKNOWN) }
    var recordAudioStatus by remember { mutableStateOf(PermissionStatus.UNKNOWN) }
    var callStateStatus by remember { mutableStateOf(PermissionStatus.UNKNOWN) }

    // Root 详细信息
    var rootDetails by remember { mutableStateOf("") }

    // 实时通话状态
    var currentCallState by remember { mutableStateOf(RootCallState.IDLE) }
    var callNumber by remember { mutableStateOf<String?>(null) }
    var callDuration by remember { mutableStateOf(0L) }
    var isMonitoring by remember { mutableStateOf(false) }

    // 检测所有权限的函数定义（放在前面供后面使用）
    fun checkAllPermissions() {
        // 检测 Root 权限
        rootStatus = if (RootUtils.hasRootPermission()) {
            rootDetails = "Root 管理器: ${RootUtils.getRootManagerType()}"
            PermissionStatus.GRANTED
        } else if (RootUtils.isDeviceRooted()) {
            rootDetails = "设备已 Root，但应用未获得 Root 权限"
            PermissionStatus.PARTIAL
        } else {
            rootDetails = "设备未 Root"
            PermissionStatus.DENIED
        }

        // 检测电话权限
        phoneStatus = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED

        // 检测通话记录权限
        callLogStatus = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED

        // 检测录音权限
        recordAudioStatus = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 进一步检测是否可以实际录音
            if (canRecordAudio()) PermissionStatus.GRANTED else PermissionStatus.PARTIAL
        } else PermissionStatus.DENIED

        // 检测通话状态权限
        callStateStatus = if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限请求完成后重新检测
        checkAllPermissions()
        // 显示提示
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "权限申请成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "部分权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    // 请求单个权限
    fun requestPermission(permission: String) {
        permissionLauncher.launch(arrayOf(permission))
    }

    // 请求多个权限
    fun requestPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    // 通话状态检测器
    val callStateDetector = remember { RootCallStateDetector() }

    // 启动通话状态监控
    fun startCallMonitoring() {
        if (!RootUtils.hasRootPermission()) {
            scope.launch {
                // 使用普通方式监控
                monitorNormalCallState(context) { state, number, duration ->
                    currentCallState = state
                    callNumber = number
                    callDuration = duration
                }
            }
        } else {
            // 使用 Root 方式监控
            callStateDetector.addListener(object : RootPhoneStateListener {
                override fun onDialing(number: String?) {
                    currentCallState = RootCallState.DIALING
                    callNumber = number
                    callDuration = 0
                }

                override fun onAlerting(number: String?) {
                    currentCallState = RootCallState.ALERTING
                    callNumber = number
                }

                override fun onActive(number: String?, setupTime: Long) {
                    currentCallState = RootCallState.ACTIVE
                    callNumber = number
                }

                override fun onIdle(duration: Long) {
                    currentCallState = RootCallState.IDLE
                    callDuration = duration
                }
            })
            callStateDetector.startDetection()
        }
        isMonitoring = true

        // 启动时长更新
        scope.launch {
            while (isMonitoring) {
                if (currentCallState == RootCallState.ACTIVE) {
                    callDuration = callStateDetector.getCurrentCallDuration()
                }
                delay(1000)
            }
        }
    }

    // 停止监控
    fun stopCallMonitoring() {
        callStateDetector.stopDetection()
        isMonitoring = false
    }

    // 初始化
    LaunchedEffect(Unit) {
        checkAllPermissions()
        if (callStateStatus == PermissionStatus.GRANTED || rootStatus == PermissionStatus.GRANTED) {
            startCallMonitoring()
        }
        // 检查 Vosk 模型状态
        settingsViewModel.checkVoskModelState(context)
    }

    // 获取 Vosk 模型状态
    val voskModelState by settingsViewModel.voskModelState.collectAsState()

    // 清理
    DisposableEffect(Unit) {
        onDispose {
            stopCallMonitoring()
            callStateDetector.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "权限测试")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Text(
                text = "设备权限检测",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "检测设备是否具备呼叫中心所需的各项权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Root 权限
            PermissionCard(
                title = "Root 权限",
                status = rootStatus,
                icon = Icons.Default.Security,
                details = rootDetails,
                onCheck = { checkAllPermissions() },
                onClick = {
                    Toast.makeText(context, "Root 权限需要手动在 Root 管理器中授予", Toast.LENGTH_LONG).show()
                }
            )

            // 电话权限
            PermissionCard(
                title = "电话拨打权限",
                status = phoneStatus,
                icon = Icons.Default.Phone,
                details = "允许应用拨打电话",
                onClick = { requestPermission(Manifest.permission.CALL_PHONE) }
            )

            // 通话记录权限
            PermissionCard(
                title = "通话记录权限",
                status = callLogStatus,
                icon = Icons.Default.History,
                details = "允许读取通话记录",
                onClick = { requestPermission(Manifest.permission.READ_CALL_LOG) }
            )

            // 录音权限
            PermissionCard(
                title = "录音权限",
                status = recordAudioStatus,
                icon = Icons.Default.Mic,
                details = "允许录制音频",
                onClick = { requestPermission(Manifest.permission.RECORD_AUDIO) }
            )

            // 通话状态权限
            PermissionCard(
                title = "通话状态权限",
                status = callStateStatus,
                icon = Icons.Default.PhoneInTalk,
                details = "允许监听通话状态",
                onClick = { requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 实时通话状态
            Text(
                text = "实时通话状态",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            CallStateCard(
                state = currentCallState,
                number = callNumber,
                duration = callDuration,
                isRootMode = rootStatus == PermissionStatus.GRANTED
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 重新检测按钮
            Button(
                onClick = { checkAllPermissions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.padding(end = 8.dp))
                Text("重新检测权限")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== 功能开关 ====================
            Text(
                text = "功能设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 功能开关入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToFeatureToggles)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ToggleOn,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "功能开关",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "NEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "管理实验性功能和新特性开关",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== 语音识别模型 ====================
            Text(
                text = "语音识别模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 语音识别模型标题卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (voskModelState.hasAnyModel)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            null,
                            tint = if (voskModelState.hasAnyModel)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "离线语音识别",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (voskModelState.allModelsReady) {
                                    Surface(
                                        color = Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "全部就绪",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "总大小: ${if (voskModelState.totalSize.isNotEmpty()) voskModelState.totalSize else "0 KB"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!voskModelState.allModelsReady && voskModelState.models.isNotEmpty()) {
                                Text(
                                    text = "支持中英文双语识别",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (voskModelState.isDownloadingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (!voskModelState.allModelsReady && voskModelState.models.isNotEmpty()) {
                            TextButton(onClick = { settingsViewModel.downloadAllVoskModels(context) }) {
                                Text("下载全部")
                            }
                        }
                    }
                }
            }

            // 显示每个模型的状态
            voskModelState.models.forEach { modelStatus ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !modelStatus.isDownloading && !voskModelState.isDownloadingAll,
                            onClick = {
                                if (modelStatus.isReady) {
                                    settingsViewModel.deleteVoskModel(context, modelStatus.name)
                                } else {
                                    settingsViewModel.downloadVoskModel(context, modelStatus.name)
                                }
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelStatus.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (modelStatus.isReady)
                                        Color(0xFF4CAF50)
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (modelStatus.isReady) "已下载" else "未下载",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (modelStatus.isReady)
                                            Color.White
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            when {
                                modelStatus.isDownloading -> {
                                    Text(
                                        text = when (modelStatus.downloadProgress) {
                                            -1 -> "正在解压模型..."
                                            else -> "下载中... ${modelStatus.downloadProgress}%"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (modelStatus.downloadProgress >= 0) {
                                        LinearProgressIndicator(
                                            progress = modelStatus.downloadProgress / 100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                        )
                                    }
                                }
                                modelStatus.error != null -> {
                                    Text(
                                        text = "错误: ${modelStatus.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                modelStatus.isReady -> {
                                    Text(
                                        text = "大小: ${modelStatus.size} | 点击删除",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "离线语音识别模型（约50MB）| 点击下载",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        when {
                            modelStatus.isDownloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            modelStatus.isReady -> {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.Download,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 设备信息
            Spacer(modifier = Modifier.height(16.dp))
            DeviceInfoCard()
        }
    }
}

/**
 * 权限状态卡片
 */
@Composable
private fun PermissionCard(
    title: String,
    status: PermissionStatus,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    details: String = "",
    onCheck: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val (statusColor, statusText) = when (status) {
        PermissionStatus.GRANTED -> Color(0xFF4CAF50) to "已授权"
        PermissionStatus.PARTIAL -> Color(0xFFFF9800) to "部分可用"
        PermissionStatus.DENIED -> Color(0xFFE91E63) to "未授权"
        PermissionStatus.UNKNOWN -> Color(0xFF9E9E9E) to "未知"
    }

    // 是否可以点击申请权限（未授权或部分授权时可以点击）
    val isClickable = onClick != null && status != PermissionStatus.GRANTED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable { onClick?.invoke() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 显示点击提示
                if (isClickable) {
                    Text(
                        text = "点击申请权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * 通话状态卡片
 */
@Composable
private fun CallStateCard(
    state: RootCallState,
    number: String?,
    duration: Long,
    isRootMode: Boolean
) {
    val (stateText, stateColor) = when (state) {
        RootCallState.IDLE -> "空闲" to Color(0xFF9E9E9E)
        RootCallState.DIALING -> "拨号中" to Color(0xFF2196F3)
        RootCallState.ALERTING -> "对方响铃" to Color(0xFFFF9800)
        RootCallState.ACTIVE -> "通话中" to Color(0xFF4CAF50)
        RootCallState.HOLDING -> "保持中" to Color(0xFF9C27B0)
        RootCallState.DISCONNECTING -> "正在挂断" to Color(0xFFE91E63)
        RootCallState.DISCONNECTED -> "已挂断" to Color(0xFF9E9E9E)
        RootCallState.CONNECTED -> "已接听" to Color(0xFF4CAF50)
        RootCallState.MISSED -> "未接听" to Color(0xFFFF5722)
        RootCallState.BUSY -> "对方忙线" to Color(0xFFFF9800)
        RootCallState.NO_ANSWER -> "无人接听" to Color(0xFFFF9800)
        RootCallState.REJECTED -> "对方拒接" to Color(0xFFE91E63)
        RootCallState.NETWORK_UNAVAILABLE -> "网络不可用" to Color(0xFF795548)
        RootCallState.OUT_OF_SERVICE -> "无服务" to Color(0xFF795548)
        RootCallState.SIGNAL_WEAK -> "信号弱" to Color(0xFFFFC107)
        RootCallState.INVALID_NUMBER -> "号码无效" to Color(0xFF607D8B)
        RootCallState.NUMBER_NOT_IN_SERVICE -> "号码已停机" to Color(0xFF607D8B)
        RootCallState.POWER_OFF -> "对方关机" to Color(0xFF607D8B)
        RootCallState.VOICEMAIL -> "语音信箱" to Color(0xFF2196F3)
        RootCallState.CALL_FORWARDING -> "呼叫转移" to Color(0xFF2196F3)
        RootCallState.IVR_PROMPT -> "IVR语音" to Color(0xFF00BCD4)
        RootCallState.ONHOLD -> "通话保持" to Color(0xFF9C27B0)
        RootCallState.CONFERENCE -> "会议通话" to Color(0xFF673AB7)
        RootCallState.MUTED -> "静音中" to Color(0xFF9E9E9E)
        RootCallState.ERROR -> "呼叫错误" to Color(0xFFF44336)
        RootCallState.TIMEOUT -> "呼叫超时" to Color(0xFFFF5722)
        RootCallState.CALL_FAILED -> "呼叫失败" to Color(0xFFF44336)
        RootCallState.UNKNOWN -> "未知状态" to Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, stateColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 状态标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stateColor
                ) {
                    Text(
                        text = stateText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isRootMode) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4CAF50)
                    ) {
                        Text(
                            text = "Root 模式",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 号码
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Phone,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = number ?: "无号码",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 时长
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 设备信息卡片
 */
@Composable
private fun DeviceInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow("设备型号", Build.MODEL)
            InfoRow("Android 版本", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            InfoRow("厂商", Build.MANUFACTURER)
            InfoRow("品牌", Build.BRAND)
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 权限状态枚举
 */
enum class PermissionStatus {
    UNKNOWN,    // 未知
    GRANTED,    // 已授权
    PARTIAL,    // 部分可用
    DENIED      // 未授权
}

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
}

/**
 * 检测是否可以录音
 */
private fun canRecordAudio(): Boolean {
    return try {
        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val canRecord = audioRecord.state == AudioRecord.STATE_INITIALIZED
        audioRecord.release()
        canRecord
    } catch (e: Exception) {
        false
    }
}

/**
 * 普通方式监控通话状态
 */
private suspend fun monitorNormalCallState(
    context: android.content.Context,
    onStateChange: (RootCallState, String?, Long) -> Unit
) {
    val telephonyManager = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
    var lastState = TelephonyManager.CALL_STATE_IDLE

    while (true) {
        try {
            val currentState = telephonyManager.callState
            if (currentState != lastState) {
                val rootState = when (currentState) {
                    TelephonyManager.CALL_STATE_IDLE -> RootCallState.IDLE
                    TelephonyManager.CALL_STATE_RINGING -> RootCallState.ALERTING
                    TelephonyManager.CALL_STATE_OFFHOOK -> RootCallState.ACTIVE
                    else -> RootCallState.IDLE
                }
                onStateChange(rootState, null, 0)
                lastState = currentState
            }
        } catch (e: SecurityException) {
            // 权限不足
            Toast.makeText(context, "权限不足, 无法获取通话权限", Toast.LENGTH_SHORT).show()
        }
        delay(1000)
    }
}
