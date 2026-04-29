package com.callcenter.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.callcenter.app.util.UserNotifier
import java.io.File
import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通话录音管理器。
 *
 * 说明：不同 ROM 对通话录音支持差异很大，这里采用多音频源回退策略，
 * 优先尝试双向通话相关音频源，失败后再回退到更通用的音频源。
 * 
 * 对于无法直接录音的设备（Android 10+），支持读取系统自带的通话录音文件。
 */
class CallRecordingManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "CallRecordingManager"

        data class PendingRecordingUpload(
            val audioFile: File,
            val callId: Int,
            val phoneNumber: String?,
            val durationSeconds: Int?
        )

        // 系统通话录音目录（各厂商不同）
        // 注意：Android 11+ 使用 scoped storage，部分路径可能需要特殊权限
        private val SYSTEM_RECORDING_PATHS = listOf(
            // ===== 小米 MIUI =====
            "/sdcard/MIUI/sound_recorder/call_rec/",
            "/sdcard/MIUI/music_recorder/call/",
            
            // ===== Google Pixel (原生 Android) =====
            "/sdcard/Recordings/Call Recordings/",
            "/sdcard/Recordings/Call/",
            "/sdcard/Music/Recordings/Call Recordings/",
            
            // ===== 华为/荣耀 =====
            "/sdcard/HWCallRecorder/",
            "/sdcard/sounds/callrecorder/",
            "/sdcard/Sounds/CallRecorder/",
            
            // ===== OPPO =====
            "/sdcard/PhoneRecord/",
            "/sdcard/phone_record/",
            "/sdcard/Recordings/PhoneRecord/",
            
            // ===== Vivo =====
            "/sdcard/Record/Call/",
            "/sdcard/录音/通话录音/",
            
            // ===== 三星 =====
            "/sdcard/Voice Recorder/",
            "/sdcard/Sounds/Call recordings/",
            
            // ===== 一加 =====
            "/sdcard/Recordings/CallRecordings/",
            "/sdcard/record/CallRecord/",
            
            // ===== 魅族 =====
            "/sdcard/Recorder/CallRecording/",
            
            // ===== 索尼 =====
            "/sdcard/Music/Recordings/Voice Call/",
            
            // ===== LG =====
            "/sdcard/Sounds/Call/",
            
            // ===== 摩托罗拉 =====
            "/sdcard/Recordings/Call Recordings/",
            
            // ===== 联想 =====
            "/sdcard/LenovoRecord/call/",
            
            // ===== 中兴 =====
            "/sdcard/record/call/",
            
            // ===== 通用/其他 =====
            "/sdcard/call_recording/",
            "/sdcard/CallRecordings/",
            "/sdcard/CallRecord/",
            "/sdcard/callrecordings/",
            "/sdcard/Recordings/",
            "/sdcard/Recording/Call/",
            
            // ===== 第三方录音应用 =====
            "/sdcard/CallRecorder/",
            "/sdcard/AutoCallRecorder/",
            "/sdcard/CallRecord/"
        )

        fun getRecordingDirectory(context: Context): File {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "call_recordings"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        fun normalizePhoneNumber(phoneNumber: String?): String {
            return phoneNumber?.replace(Regex("[^0-9+]"), "")?.ifBlank { "unknown" } ?: "unknown"
        }

        /**
         * 扫描系统通话录音目录，找到最新的录音文件
         * @param phoneNumber 电话号码（用于匹配）
         * @param afterTime 查找此时间之后的录音（毫秒时间戳）
         * @return 找到的最新录音文件，如果没找到返回 null
         */
        fun findLatestSystemRecording(phoneNumber: String?, afterTime: Long = 0): File? {
            val normalizedPhone = normalizePhoneNumber(phoneNumber)
            
            Log.d(TAG, "开始扫描系统录音目录，号码: $normalizedPhone, 时间限制: $afterTime")
            
            val allRecordings = mutableListOf<File>()
            
            for (path in SYSTEM_RECORDING_PATHS) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    Log.d(TAG, "扫描目录: $path")
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && isAudioFile(file)) {
                            // 检查文件修改时间
                            if (afterTime > 0 && file.lastModified() < afterTime) {
                                return@forEach
                            }
                            
                            // 检查文件名是否包含电话号码
                            val fileName = file.name.lowercase()
                            val phoneDigits = normalizedPhone.takeLast(8) // 取后8位匹配
                            
                            if (fileName.contains(phoneDigits.lowercase()) || phoneNumber == null) {
                                allRecordings.add(file)
                                Log.d(TAG, "找到匹配录音: ${file.absolutePath}, 时间: ${file.lastModified()}")
                            }
                        }
                    }
                }
            }
            
            // 返回最新的录音文件
            return allRecordings.maxByOrNull { it.lastModified() }.also {
                Log.d(TAG, "共找到 ${allRecordings.size} 个匹配录音，最新: ${it?.absolutePath}")
            }
        }
        
        /**
         * 获取所有可用的系统录音目录
         */
        fun getAvailableSystemRecordingDirectories(): List<File> {
            return SYSTEM_RECORDING_PATHS.mapNotNull { path ->
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) dir else null
            }
        }
        
        /**
         * 检查文件是否是音频文件
         */
        private fun isAudioFile(file: File): Boolean {
            val extension = file.extension.lowercase()
            return extension in listOf("mp3", "m4a", "aac", "wav", "amr", "ogg", "3gp", "mp4")
        }

        fun listRecordingsForPhone(context: Context, phoneNumber: String?): List<File> {
            val normalizedPhone = normalizePhoneNumber(phoneNumber)
            val prefix = "call_${normalizedPhone}_"
            
            // 同时扫描应用目录和系统目录
            val appRecordings = getRecordingDirectory(context)
                .listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) && it.extension.equals("m4a", ignoreCase = true) }
                ?: emptyList()
            
            val systemRecordings = findLatestSystemRecording(phoneNumber)
                ?.let { listOf(it) }
                ?: emptyList()
            
            return (appRecordings + systemRecordings)
                .sortedByDescending { it.lastModified() }
        }

        fun getRecordingDurationSeconds(file: File): Int? {
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                retriever.release()
                durationMs?.let { kotlin.math.max(1, (it / 1000L).toInt()) }
            } catch (e: Exception) {
                Log.w(TAG, "读取录音时长失败: ${e.message}")
                null
            }
        }

        private fun getPendingMarkerFile(audioFile: File): File {
            return File(audioFile.parentFile, "${audioFile.name}.pending")
        }

        fun markPendingUpload(
            audioFile: File,
            callId: Int,
            phoneNumber: String?,
            durationSeconds: Int?
        ) {
            try {
                val properties = Properties().apply {
                    setProperty("callId", callId.toString())
                    setProperty("phoneNumber", phoneNumber.orEmpty())
                    durationSeconds?.let { setProperty("durationSeconds", it.toString()) }
                }
                getPendingMarkerFile(audioFile).outputStream().use { output ->
                    properties.store(output, "pending recording upload")
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入待补传标记失败: ${e.message}")
                UserNotifier.showError("写入录音待补传标记失败: ${e.message}")
            }
        }

        fun clearPendingUpload(audioFile: File) {
            runCatching { getPendingMarkerFile(audioFile).takeIf { it.exists() }?.delete() }
        }

        fun listPendingUploads(context: Context): List<PendingRecordingUpload> {
            return getRecordingDirectory(context)
                .listFiles()
                ?.filter { it.isFile && it.extension.equals("pending", ignoreCase = true) }
                ?.mapNotNull { marker ->
                    try {
                        val props = Properties().apply {
                            marker.inputStream().use { load(it) }
                        }
                        val audioFile = File(marker.parentFile, marker.name.removeSuffix(".pending"))
                        val callId = props.getProperty("callId")?.toIntOrNull() ?: return@mapNotNull null
                        if (!audioFile.exists()) {
                            marker.delete()
                            return@mapNotNull null
                        }
                        PendingRecordingUpload(
                            audioFile = audioFile,
                            callId = callId,
                            phoneNumber = props.getProperty("phoneNumber"),
                            durationSeconds = props.getProperty("durationSeconds")?.toIntOrNull()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "解析待补传标记失败: ${e.message}")
                        UserNotifier.showError("解析录音待补传标记失败: ${e.message}")
                        null
                    }
                }
                ?.sortedBy { it.audioFile.lastModified() }
                ?: emptyList()
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false

    fun startRecording(phoneNumber: String?): Boolean {
        if (isRecording) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "未授予 RECORD_AUDIO 权限，无法开始录音")
            return false
        }

        val outputDir = getRecordingDirectory(context)

        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val normalizedPhone = normalizePhoneNumber(phoneNumber)
        val outputFile = File(outputDir, "call_${normalizedPhone}_$time.m4a")

        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        ).distinct()

        for (audioSource in audioSources) {
            val recorder = createRecorder()
            try {
                recorder.setAudioSource(audioSource)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
                recorder.setOutputFile(outputFile.absolutePath)
                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                currentOutputFile = outputFile
                isRecording = true
                Log.d(TAG, "开始通话录音: source=$audioSource, file=${outputFile.absolutePath}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "录音源 $audioSource 启动失败: ${e.message}")
                runCatching { recorder.reset() }
                runCatching { recorder.release() }
            }
        }

        currentOutputFile = null
        isRecording = false
        return false
    }

    fun stopRecording(): String? {
        if (!isRecording) return currentOutputFile?.absolutePath

        val outputPath = currentOutputFile?.absolutePath

        try {
            mediaRecorder?.stop()
            Log.d(TAG, "通话录音已保存: $outputPath")
        } catch (e: Exception) {
            Log.w(TAG, "停止录音失败: ${e.message}")
            currentOutputFile?.delete()
        } finally {
            runCatching { mediaRecorder?.reset() }
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
        }

        return outputPath
    }

    fun isRecording(): Boolean = isRecording

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
