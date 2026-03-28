package com.callcenter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.ui.components.UpdateDialog
import com.callcenter.app.ui.navigation.AppNavigation
import com.callcenter.app.ui.theme.CallCenterTheme
import com.callcenter.app.ui.viewmodel.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

                    AppNavigation()
                }
            }
        }
    }
}
