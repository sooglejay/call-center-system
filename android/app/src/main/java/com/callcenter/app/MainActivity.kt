package com.callcenter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.ui.components.UpdateDialog
import com.callcenter.app.ui.navigation.AppNavigation
import com.callcenter.app.ui.theme.CallCenterTheme
import com.callcenter.app.ui.viewmodel.UpdateViewModel
import com.callcenter.app.util.FloatingWindowManager
import com.callcenter.app.util.RootUtil
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDialNumber by mutableStateOf<String?>(null)
    private var pendingOpenDialer by mutableStateOf(false)
    private var hasRetriedDefaultDialerOnResume = false
    private var isRequestingDefaultDialer = false
    private var pendingAutoDefaultDialerRequest = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // 权限申请回调
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            // 有权限被拒绝
            Toast.makeText(
                this,
                "部分通话权限被拒绝，可能影响自动拨号功能",
                Toast.LENGTH_LONG
            ).show()
        }
        // 标记为在 onResume 中自动申请，避免冷启动阶段过早拉起系统角色页
        pendingAutoDefaultDialerRequest = true
    }

    private val defaultDialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isRequestingDefaultDialer = false
        pendingAutoDefaultDialerRequest = false
        if (isDefaultDialer()) {
            Toast.makeText(this, "已启用默认拨号应用，自动外放能力已增强", Toast.LENGTH_SHORT).show()
        }

        // 默认拨号申请结束后，再检查悬浮窗权限
        checkFloatingWindowPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDialNumber = extractDialNumber(intent)
        pendingOpenDialer = shouldOpenDialer(intent)
        hasRetriedDefaultDialerOnResume = false

        // 检查并申请通话/拨号相关权限
        checkAndRequestCallPermissions()

        setContent {
            CallCenterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 版本更新检查
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsState()
                    val versionInfo by updateViewModel.versionInfo.collectAsState()
                    val updateState by updateViewModel.updateState.collectAsState()

                    // 启动时检查更新
                    LaunchedEffect(Unit) {
                        updateViewModel.checkForUpdate(showDialog = true)
                    }

                    // 显示更新对话框
                    if (showUpdateDialog) {
                        UpdateDialog(
                            versionInfo = versionInfo,
                            updateState = updateState,
                            onDismiss = { updateViewModel.dismissUpdateDialog() },
                            onConfirm = { updateViewModel.startUpdate() },
                            onRetry = { updateViewModel.retryDownload() }
                        )
                    }

                    AppNavigation(
                        pendingOpenDialer = pendingOpenDialer,
                        pendingDialNumber = pendingDialNumber,
                        onDialIntentConsumed = {
                            pendingDialNumber = null
                            pendingOpenDialer = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDialNumber = extractDialNumber(intent)
        pendingOpenDialer = shouldOpenDialer(intent)
    }

    override fun onResume() {
        super.onResume()

        if (pendingAutoDefaultDialerRequest && !isRequestingDefaultDialer && !isDefaultDialer()) {
            pendingAutoDefaultDialerRequest = false
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed && !isRequestingDefaultDialer && !isDefaultDialer()) {
                    checkAndRequestDefaultDialerRole()
                }
            }, 500)
        } else if (!hasRetriedDefaultDialerOnResume && !isRequestingDefaultDialer && !isDefaultDialer()) {
            hasRetriedDefaultDialerOnResume = true
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed && !isRequestingDefaultDialer && !isDefaultDialer()) {
                    checkAndRequestDefaultDialerRole()
                }
            }, 500)
        }
    }

    /**
     * 检查并申请通话/拨号相关权限
     */
    private fun checkAndRequestCallPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 拨打电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }

        // 读取电话状态权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        // 读取通话记录权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALL_LOG)
        }

        // 写入通话记录权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_CALL_LOG)
        }

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 如果有权限需要申请
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // 所有权限都已授予，等 Activity 进入前台后再自动申请默认拨号角色
            pendingAutoDefaultDialerRequest = true
        }
    }

    private fun checkAndRequestDefaultDialerRole() {
        if (isDefaultDialer()) {
            isRequestingDefaultDialer = false
            checkFloatingWindowPermission()
            return
        }

        if (isRequestingDefaultDialer) {
            return
        }

        try {
            if (RootUtil.isDeviceRooted()) {
                val rootSuccess = RootUtil.setDefaultDialerPackage(packageName)
                if (rootSuccess && isDefaultDialer()) {
                    isRequestingDefaultDialer = false
                    Toast.makeText(this, "已通过 Root 自动设置默认拨号应用", Toast.LENGTH_SHORT).show()
                    checkFloatingWindowPermission()
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    isRequestingDefaultDialer = true
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    defaultDialerRoleLauncher.launch(intent)
                    return
                }
            }

            isRequestingDefaultDialer = true
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            defaultDialerRoleLauncher.launch(intent)
        } catch (e: Exception) {
            isRequestingDefaultDialer = false
            Toast.makeText(this, "请求默认拨号应用失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isDefaultDialer(): Boolean {
        return try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            telecomManager?.defaultDialerPackage == packageName
        } catch (_: Exception) {
            false
        }
    }

    private fun extractDialNumber(intent: Intent?): String? {
        if (intent == null) return null

        val isDialIntent = intent.action == Intent.ACTION_DIAL ||
            intent.action == Intent.ACTION_VIEW ||
            intent.action == Intent.ACTION_CALL_BUTTON

        if (!isDialIntent) return null

        val number = intent.data?.schemeSpecificPart?.trim().orEmpty()
        return number.takeIf { it.isNotBlank() }
    }

    private fun shouldOpenDialer(intent: Intent?): Boolean {
        if (intent == null) return false

        return intent.getBooleanExtra(DialerEntryActivity.EXTRA_OPEN_DIALER, false) ||
            intent.action == Intent.ACTION_DIAL ||
            intent.action == Intent.ACTION_VIEW ||
            intent.action == Intent.ACTION_CALL_BUTTON
    }

    /**
     * 检查悬浮窗权限，如果没有则引导用户开启
     */
    private fun checkFloatingWindowPermission() {
        if (!FloatingWindowManager.canDrawOverlays(this)) {
            // 没有悬浮窗权限，显示提示并引导用户开启
            Toast.makeText(
                this,
                "请开启悬浮窗权限，以便在拨号时显示客户信息",
                Toast.LENGTH_LONG
            ).show()

            // 跳转到设置页面申请权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }
}
