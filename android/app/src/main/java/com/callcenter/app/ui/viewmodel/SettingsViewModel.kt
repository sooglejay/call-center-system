package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.model.CallSettings
import com.callcenter.app.data.model.Stats
import com.callcenter.app.data.model.User
import com.callcenter.app.data.model.VersionInfo
import com.callcenter.app.data.repository.AuthRepository
import com.callcenter.app.data.repository.StatsRepository
import com.callcenter.app.util.AppUpdateManager
import com.callcenter.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val statsRepository: StatsRepository,
    private val tokenManager: TokenManager,
    private val callSettingsManager: CallSettingsManager,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _callSettings = MutableStateFlow(CallSettings())
    val callSettings: StateFlow<CallSettings> = _callSettings.asStateFlow()

    // 版本更新相关
    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()

    private val _latestVersionInfo = MutableStateFlow<VersionInfo?>(null)
    val latestVersionInfo: StateFlow<VersionInfo?> = _latestVersionInfo.asStateFlow()

    private val _currentVersionCode = MutableStateFlow(0)
    val currentVersionCode: StateFlow<Int> = _currentVersionCode.asStateFlow()

    // 日志收集相关
    private val _isExportingLogs = MutableStateFlow(false)
    val isExportingLogs: StateFlow<Boolean> = _isExportingLogs.asStateFlow()

    private val _logCollectorStatus = MutableStateFlow(LogCollectorStatus())
    val logCollectorStatus: StateFlow<LogCollectorStatus> = _logCollectorStatus.asStateFlow()

    data class LogCollectorStatus(
        val isCollecting: Boolean = false,
        val cacheSize: Int = 0,
        val maxCacheSize: Int = 10000
    )

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true

            // 加载当前用户
            _currentUser.value = authRepository.currentUser.value

            // 加载服务器地址
            _serverUrl.value = tokenManager.getServerUrl() ?: Constants.DEFAULT_SERVER_URL

            // 加载统计数据
            loadStats()

            // 加载通话设置
            callSettingsManager.callSettingsFlow.collect { settings ->
                _callSettings.value = settings
            }

            _isLoading.value = false
        }
    }

    /**
     * 检查版本更新
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _currentVersionCode.value = appUpdateManager.getCurrentVersionCode()
            val newVersion = appUpdateManager.checkForUpdate()
            if (newVersion != null) {
                _hasUpdate.value = true
                _latestVersionInfo.value = newVersion
            } else {
                _hasUpdate.value = false
                _latestVersionInfo.value = null
            }
        }
    }

    private suspend fun loadStats() {
        val result = statsRepository.getMyStats()
        result.fold(
            onSuccess = { stats ->
                _stats.value = stats
            },
            onFailure = {
                // 忽略错误
            }
        )
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            // 确保服务器地址以 /api/ 结尾
            val normalizedUrl = if (url.endsWith("/api/")) url else "$url/api/"
            tokenManager.saveServerUrl(normalizedUrl)
            _serverUrl.value = normalizedUrl
        }
    }

    fun updateAutoDialInterval(seconds: Int) {
        viewModelScope.launch {
            callSettingsManager.saveAutoDialInterval(seconds)
        }
    }

    fun updateCallTimeout(seconds: Int) {
        viewModelScope.launch {
            callSettingsManager.saveCallTimeout(seconds)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * 更新日志收集设置
     */
    fun updateCollectLogcat(enabled: Boolean) {
        viewModelScope.launch {
            callSettingsManager.saveCollectLogcat(enabled)

            // 更新状态
            _logCollectorStatus.value = _logCollectorStatus.value.copy(isCollecting = enabled)
        }
    }

    /**
     * 更新最大缓存大小
     */
    fun updateLogcatMaxCacheSize(size: Int) {
        viewModelScope.launch {
            callSettingsManager.saveLogcatMaxCacheSize(size)

            // 更新服务配置
            _logCollectorStatus.value = _logCollectorStatus.value.copy(maxCacheSize = size)
        }
    }

    /**
     * 开始收集日志
     */
    fun startLogCollection(context: android.content.Context) {
        com.callcenter.app.service.LogCollectorService.startCollecting(context)
        _logCollectorStatus.value = _logCollectorStatus.value.copy(isCollecting = true)
    }

    /**
     * 停止收集日志
     */
    fun stopLogCollection(context: android.content.Context) {
        com.callcenter.app.service.LogCollectorService.stopCollecting(context)
        _logCollectorStatus.value = _logCollectorStatus.value.copy(isCollecting = false)
    }

    /**
     * 设置最大缓存大小
     */
    fun setLogMaxSize(context: android.content.Context, size: Int) {
        com.callcenter.app.service.LogCollectorService.setMaxSize(context, size)
        _logCollectorStatus.value = _logCollectorStatus.value.copy(maxCacheSize = size)
    }

    /**
     * 更新缓存数量（由服务或外部更新）
     */
    fun updateCacheSize(size: Int) {
        _logCollectorStatus.value = _logCollectorStatus.value.copy(cacheSize = size)
    }

    /**
     * 导出日志到文件
     */
    fun exportLogs(context: android.content.Context): java.io.File? {
        _isExportingLogs.value = true

        return try {
            val fileName = "callcenter_log_${System.currentTimeMillis()}.txt"
            val file = java.io.File(context.getExternalFilesDir(null), fileName)

            // 调用服务导出日志
            com.callcenter.app.service.LogCollectorService.exportLogs(context, file.absolutePath)

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            _isExportingLogs.value = false
        }
    }

    /**
     * 导出日志到指定 Uri（异步，推荐使用）
     */
    fun exportLogsToUri(
        context: android.content.Context,
        uri: android.net.Uri,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isExportingLogs.value = true

            try {
                // 关键字过滤
                val keywords = listOf(
                    "AutoSpeakerInCall",
                    "CallStateMonitor",
                    "CallHelper",
                    "AudioManager",
                    "AudioSystem",
                    "setSpeakerphoneOn",
                    "setCommunicationDevice",
                    "setForceUse",
                    "setAudioRoute",
                    "CallAudioState"
                )

                // 读取 logcat
                val logs = mutableListOf<String>()
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (keywords.any { keyword -> line!!.contains(keyword, ignoreCase = true) }) {
                        logs.add(line!!)
                    }
                }
                reader.close()
                process.destroy()

                if (logs.isEmpty()) {
                    onResult(false, "没有找到相关日志")
                    return@launch
                }

                // 写入到 Uri
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.PrintWriter(java.io.OutputStreamWriter(outputStream)).use { writer ->
                        writer.println("# Logcat 日志导出")
                        writer.println("# 导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                        writer.println("# 日志数量: ${logs.size}")
                        writer.println("# 关键字: ${keywords.joinToString(", ")}")
                        writer.println("# " + "=".repeat(80))
                        writer.println()

                        logs.forEach { log ->
                            writer.println(log)
                        }
                    }
                }

                onResult(true, "成功导出 ${logs.size} 条日志")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "导出失败: ${e.message}")
            } finally {
                _isExportingLogs.value = false
            }
        }
    }

    /**
     * 获取日志文件输出目录
     */
    fun getLogOutputDirectory(context: android.content.Context): String {
        return context.getExternalFilesDir(null)?.absolutePath ?: ""
    }
}
