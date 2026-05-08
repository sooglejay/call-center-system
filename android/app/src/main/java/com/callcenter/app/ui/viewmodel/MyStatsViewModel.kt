package com.callcenter.app.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.api.LogUploadResponse
import com.callcenter.app.data.model.Stats
import com.callcenter.app.data.repository.StatsRepository
import com.callcenter.app.util.LogcatCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject

/**
 * 个人统计 ViewModel
 */
@HiltViewModel
class MyStatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MyStatsViewModel"
    }

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 日志上传状态
    private val _isUploadingLogs = MutableStateFlow(false)
    val isUploadingLogs: StateFlow<Boolean> = _isUploadingLogs.asStateFlow()

    private val _logUploadMessage = MutableStateFlow<String?>(null)
    val logUploadMessage: StateFlow<String?> = _logUploadMessage.asStateFlow()

    init {
        loadStats()
    }

    /**
     * 加载统计数据
     */
    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = statsRepository.getMyStats()

            result.fold(
                onSuccess = { stats ->
                    _stats.value = stats
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载统计数据失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadStats()
    }

    /**
     * 上传设备日志
     */
    fun uploadDeviceLogs(context: Context, description: String = "") {
        viewModelScope.launch {
            _isUploadingLogs.value = true
            _logUploadMessage.value = null

            try {
                // 收集日志
                val logFile = LogcatCollector.collectLogs(context)
                if (logFile == null) {
                    _logUploadMessage.value = "收集日志失败"
                    _isUploadingLogs.value = false
                    return@launch
                }

                Log.d(TAG, "日志文件大小: ${logFile.length()} 字节")

                // 上传日志
                val result = uploadLogFile(logFile, description)

                result.fold(
                    onSuccess = { response ->
                        _logUploadMessage.value = "日志上传成功 (${LogcatCollector.formatFileSize(response.file_size)})"
                        Log.d(TAG, "日志上传成功: ${response.message}")
                    },
                    onFailure = { exception ->
                        val msg = exception.message ?: exception.toString()
                        _logUploadMessage.value = "上传失败: $msg"
                        Log.e(TAG, "日志上传失败: $msg", exception)
                    }
                )

            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                _logUploadMessage.value = "上传异常: $msg"
                Log.e(TAG, "日志上传异常: $msg", e)
            } finally {
                _isUploadingLogs.value = false
            }
        }
    }

    /**
     * 上传日志文件
     */
    private suspend fun uploadLogFile(logFile: File, description: String): Result<LogUploadResponse> {
        return try {
            val requestBody = logFile.asRequestBody("text/plain".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", logFile.name, requestBody)

            // 创建描述的 RequestBody
            val descriptionBody = if (description.isNotEmpty()) {
                okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), description)
            } else {
                null
            }

            val response: Response<LogUploadResponse> = statsRepository.uploadDeviceLogs(part, descriptionBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 处理错误响应
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }
                Log.w(
                    TAG,
                    "日志上传接口返回失败: code=${response.code()}, message=${response.message()}, errorBody=${errorBody?.take(500)}"
                )
                val errorMessage = if (response.code() == 413) {
                    // 文件过大错误
                    "日志文件过大，请升级App至最新版本"
                } else if (errorBody?.contains("FILE_TOO_LARGE") == true) {
                    "日志文件过大，请升级App至最新版本"
                } else {
                    // 尽量把服务端返回带出来，避免出现“失败但原因不明”
                    val serverMsg = errorBody
                        ?.takeIf { it.isNotBlank() }
                        ?.replace("\n", " ")
                        ?.take(120)
                    if (serverMsg != null) {
                        "上传失败: ${response.code()} ($serverMsg)"
                    } else {
                        "上传失败: ${response.code()}"
                    }
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "日志上传请求异常: ${e.message ?: e}", e)
            Result.failure(e)
        }
    }

    /**
     * 清除日志上传消息
     */
    fun clearLogUploadMessage() {
        _logUploadMessage.value = null
    }
}
