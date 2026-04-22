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
    fun uploadDeviceLogs(context: Context) {
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
                val result = uploadLogFile(logFile)

                result.fold(
                    onSuccess = { response ->
                        _logUploadMessage.value = "日志上传成功 (${LogcatCollector.formatFileSize(response.file_size)})"
                        Log.d(TAG, "日志上传成功: ${response.message}")
                    },
                    onFailure = { exception ->
                        _logUploadMessage.value = "上传失败: ${exception.message}"
                        Log.e(TAG, "日志上传失败: ${exception.message}")
                    }
                )

            } catch (e: Exception) {
                _logUploadMessage.value = "上传异常: ${e.message}"
                Log.e(TAG, "日志上传异常: ${e.message}")
            } finally {
                _isUploadingLogs.value = false
            }
        }
    }

    /**
     * 上传日志文件
     */
    private suspend fun uploadLogFile(logFile: File): Result<LogUploadResponse> {
        return try {
            val requestBody = logFile.asRequestBody("text/plain".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", logFile.name, requestBody)
            val response: Response<LogUploadResponse> = statsRepository.uploadDeviceLogs(part)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("上传失败: ${response.code()}"))
            }
        } catch (e: Exception) {
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
