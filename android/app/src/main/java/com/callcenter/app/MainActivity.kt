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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
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
import com.callcenter.app.data.repository.CallRecordRepository
import com.callcenter.app.util.FloatingWindowManager
import com.callcenter.app.util.RootUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var callRecordRepository: CallRecordRepository

    private var pendingDialNumber by mutableStateOf<String?>(null)
    private var pendingOpenDialer by mutableStateOf(false)
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDialNumber = extractDialNumber(intent)
        pendingOpenDialer = shouldOpenDialer(intent)

        // 检查并申请通话/拨号相关权限
        checkAndRequestCallPermissions()
        // 权限检查完成后检查悬浮窗权限
        checkFloatingWindowPermission()

        lifecycleScope.launch {
            tryUploadPendingRecordings()
        }

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

        // 通话录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
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

    private suspend fun tryUploadPendingRecordings() {
        if (!RootUtil.isDeviceRooted()) {
            return
        }

        val result = callRecordRepository.uploadPendingRecordings()
        result.onSuccess { (successCount, failedCount) ->
            if (successCount > 0) {
                Toast.makeText(this, "已自动补传 $successCount 条通话录音", Toast.LENGTH_SHORT).show()
            }
            if (failedCount > 0) {
                android.util.Log.w("MainActivity", "仍有 $failedCount 条录音待补传")
            }
        }.onFailure {
            android.util.Log.e("MainActivity", "补传通话录音失败: ${it.message}")
        }
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
