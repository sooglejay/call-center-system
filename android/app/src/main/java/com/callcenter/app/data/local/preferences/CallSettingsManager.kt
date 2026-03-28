package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callcenter.app.data.model.CallSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.callSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "call_settings")

/**
 * 通话设置管理器
 */
class CallSettingsManager(private val context: Context) {

    companion object {
        private val AUTO_DIAL_INTERVAL = intPreferencesKey("auto_dial_interval")
        private val CALL_TIMEOUT = intPreferencesKey("call_timeout")
        private val RETRY_COUNT = intPreferencesKey("retry_count")
        private val AUTO_SPEAKER = booleanPreferencesKey("auto_speaker")
        private val AUTO_ADD_NOTE = booleanPreferencesKey("auto_add_note")
        private val DEFAULT_NOTE_TEMPLATE = stringPreferencesKey("default_note_template")
    }

    /**
     * 获取通话设置流
     */
    val callSettingsFlow: Flow<CallSettings> = context.callSettingsDataStore.data.map { prefs ->
        CallSettings(
            autoDialInterval = prefs[AUTO_DIAL_INTERVAL] ?: 10,
            callTimeout = prefs[CALL_TIMEOUT] ?: 30,
            retryCount = prefs[RETRY_COUNT] ?: 0,
            autoSpeaker = prefs[AUTO_SPEAKER] ?: false,
            autoAddNote = prefs[AUTO_ADD_NOTE] ?: false,
            defaultNoteTemplate = prefs[DEFAULT_NOTE_TEMPLATE] ?: ""
        )
    }

    /**
     * 保存自动拨号间隔
     */
    suspend fun saveAutoDialInterval(seconds: Int) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[AUTO_DIAL_INTERVAL] = seconds
        }
    }

    /**
     * 保存通话超时时间
     */
    suspend fun saveCallTimeout(seconds: Int) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[CALL_TIMEOUT] = seconds
        }
    }

    /**
     * 保存重试次数
     */
    suspend fun saveRetryCount(count: Int) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[RETRY_COUNT] = count
        }
    }

    /**
     * 保存自动免提设置
     */
    suspend fun saveAutoSpeaker(enabled: Boolean) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[AUTO_SPEAKER] = enabled
        }
    }

    /**
     * 保存自动添加备注设置
     */
    suspend fun saveAutoAddNote(enabled: Boolean) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[AUTO_ADD_NOTE] = enabled
        }
    }

    /**
     * 保存默认备注模板
     */
    suspend fun saveDefaultNoteTemplate(template: String) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[DEFAULT_NOTE_TEMPLATE] = template
        }
    }

    /**
     * 保存所有设置
     */
    suspend fun saveSettings(settings: CallSettings) {
        context.callSettingsDataStore.edit { prefs ->
            prefs[AUTO_DIAL_INTERVAL] = settings.autoDialInterval
            prefs[CALL_TIMEOUT] = settings.callTimeout
            prefs[RETRY_COUNT] = settings.retryCount
            prefs[AUTO_SPEAKER] = settings.autoSpeaker
            prefs[AUTO_ADD_NOTE] = settings.autoAddNote
            prefs[DEFAULT_NOTE_TEMPLATE] = settings.defaultNoteTemplate
        }
    }
}
