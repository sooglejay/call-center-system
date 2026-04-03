package com.callcenter.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.model.VersionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App版本更新管理器
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {
    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadId: Long = -1
    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                handleDownloadComplete()
            }
        }
    }

    init {
        // 注册下载完成广播接收器
        // Android 14+ 需要使用 ContextCompat 并指定 RECEIVER_EXPORTED 标志
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /**
     * 检查版本更新
     * @return 如果有更新返回VersionInfo，否则返回null
     */
    suspend fun checkForUpdate(): VersionInfo? {
        return try {
            val currentVersionCode = getCurrentVersionCode()
            val response = apiService.checkVersion(currentVersionCode)
            
            if (response.isSuccessful && response.body() != null) {
                val versionInfo = response.body()!!
                // 如果服务器返回的版本号大于当前版本，说明有更新
                if (versionInfo.versionCode > currentVersionCode) {
                    versionInfo
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 开始下载APK
     */
    fun startDownload(versionInfo: VersionInfo) {
        try {
            _updateState.value = UpdateState.Downloading(0)

            // 创建下载请求
            val request = DownloadManager.Request(Uri.parse(versionInfo.apkUrl)).apply {
                // 设置网络要求
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                // 设置通知显示
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // 设置标题
                setTitle("正在下载更新")
                setDescription("版本 ${versionInfo.versionName}")
                
                // 设置下载位置 - 使用应用私有目录，不需要存储权限
                val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                    "callcenter-${versionInfo.versionCode}.apk")
                setDestinationUri(Uri.fromFile(apkFile))
                
                // 允许漫游时下载
                setAllowedOverRoaming(true)
            }

            // 开始下载
            downloadId = downloadManager.enqueue(request)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "下载失败")
        }
    }

    /**
     * 处理下载完成
     */
    private fun handleDownloadComplete() {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = cursor.getString(uriIndex)
                    cursor.close()
                    
                    _updateState.value = UpdateState.DownloadComplete
                    // 自动安装
                    installApk(Uri.parse(localUri))
                } else {
                    cursor.close()
                    _updateState.value = UpdateState.Error("下载失败")
                }
            } else {
                cursor.close()
                _updateState.value = UpdateState.Error("下载失败")
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "安装失败")
        }
    }

    /**
     * 安装APK
     */
    fun installApk(uri: Uri) {
        try {
            _updateState.value = UpdateState.Installing

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 使用FileProvider
                    // 从 content:// URI 获取文件路径
                    val filePath = uri.path?.replace("/external_files/", "")?.replace("/external/", "")
                    val file = if (filePath != null) {
                        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                            filePath.substringAfterLast("/"))
                    } else {
                        File(uri.path!!)
                    }
                    
                    if (!file.exists()) {
                        _updateState.value = UpdateState.Error("APK文件不存在")
                        return
                    }
                    
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    setDataAndType(contentUri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    setDataAndType(uri, APK_MIME_TYPE)
                }
            }
            
            context.startActivity(installIntent)
            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "安装失败")
        }
    }

    /**
     * 获取当前版本号
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            // 兼容 API 26-27，使用 versionCode 而不是 longVersionCode
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 获取当前版本名称
     */
    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // 忽略
        }
    }
}

/**
 * 更新状态
 */
sealed class UpdateState {
    object Idle : UpdateState()
    data class Checking(val message: String = "正在检查更新...") : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object DownloadComplete : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}
