package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callcenter.app.data.model.Customer
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.autoDialProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_dial_progress")

/**
 * 自动拨号进度数据
 */
data class AutoDialProgress(
    val scopeType: AutoDialScopeType = AutoDialScopeType.ALL_PENDING,
    val taskId: Int? = null,
    val taskTitle: String? = null,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val dialedCount: Int = 0,
    val intervalSeconds: Int = 10,
    val timeoutSeconds: Int = 30,
    val dialsPerCustomer: Int = 1,  // 每个客户连续拨打次数
    val currentDialRound: Int = 1,  // 当前客户的第几轮拨打
    val remainingCustomers: List<Customer> = emptyList(),
    val allCustomerIds: List<Int> = emptyList(), // 保存所有客户ID用于恢复
    val isActive: Boolean = false,
    val lastUpdateTime: Long = 0
)

/**
 * 自动拨号进度管理器
 * 用于保存和恢复自动拨号任务的进度
 */
class AutoDialProgressManager(private val context: Context) {

    companion object {
        private val SCOPE_TYPE = stringPreferencesKey("scope_type")
        private val TASK_ID = intPreferencesKey("task_id")
        private val TASK_TITLE = stringPreferencesKey("task_title")
        private val CURRENT_INDEX = intPreferencesKey("current_index")
        private val TOTAL_COUNT = intPreferencesKey("total_count")
        private val DIALED_COUNT = intPreferencesKey("dialed_count")
        private val INTERVAL_SECONDS = intPreferencesKey("interval_seconds")
        private val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        private val DIALS_PER_CUSTOMER = intPreferencesKey("dials_per_customer")
        private val CURRENT_DIAL_ROUND = intPreferencesKey("current_dial_round")
        private val REMAINING_CUSTOMERS = stringPreferencesKey("remaining_customers")
        private val ALL_CUSTOMER_IDS = stringPreferencesKey("all_customer_ids")
        private val IS_ACTIVE = booleanPreferencesKey("is_active")
        private val LAST_UPDATE_TIME = intPreferencesKey("last_update_time")

        // 进度有效期（24小时）
        const val PROGRESS_VALIDITY_MS = 24 * 60 * 60 * 1000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取保存的进度流
     */
    val progressFlow: Flow<AutoDialProgress?> = context.autoDialProgressDataStore.data.map { prefs ->
        val isActive = prefs[IS_ACTIVE] ?: false
        if (!isActive) return@map null

        val lastUpdateTime = prefs[LAST_UPDATE_TIME]?.toLong() ?: 0
        // 检查进度是否过期
        if (System.currentTimeMillis() - lastUpdateTime > PROGRESS_VALIDITY_MS) {
            return@map null
        }

        val scopeType = try {
            AutoDialScopeType.valueOf(prefs[SCOPE_TYPE] ?: AutoDialScopeType.ALL_PENDING.name)
        } catch (e: Exception) {
            AutoDialScopeType.ALL_PENDING
        }

        val remainingCustomersJson = prefs[REMAINING_CUSTOMERS] ?: "[]"
        val allCustomerIdsJson = prefs[ALL_CUSTOMER_IDS] ?: "[]"

        AutoDialProgress(
            scopeType = scopeType,
            taskId = prefs[TASK_ID],
            taskTitle = prefs[TASK_TITLE],
            currentIndex = prefs[CURRENT_INDEX] ?: 0,
            totalCount = prefs[TOTAL_COUNT] ?: 0,
            dialedCount = prefs[DIALED_COUNT] ?: 0,
            intervalSeconds = prefs[INTERVAL_SECONDS] ?: 10,
            timeoutSeconds = prefs[TIMEOUT_SECONDS] ?: 30,
            dialsPerCustomer = prefs[DIALS_PER_CUSTOMER] ?: 1,
            currentDialRound = prefs[CURRENT_DIAL_ROUND] ?: 1,
            remainingCustomers = try {
                json.decodeFromString(remainingCustomersJson)
            } catch (e: Exception) {
                emptyList()
            },
            allCustomerIds = try {
                json.decodeFromString(allCustomerIdsJson)
            } catch (e: Exception) {
                emptyList()
            },
            isActive = true,
            lastUpdateTime = lastUpdateTime
        )
    }

    /**
     * 保存拨号进度
     */
    suspend fun saveProgress(progress: AutoDialProgress) {
        context.autoDialProgressDataStore.edit { prefs ->
            prefs[SCOPE_TYPE] = progress.scopeType.name
            progress.taskId?.let { prefs[TASK_ID] = it }
            progress.taskTitle?.let { prefs[TASK_TITLE] = it }
            prefs[CURRENT_INDEX] = progress.currentIndex
            prefs[TOTAL_COUNT] = progress.totalCount
            prefs[DIALED_COUNT] = progress.dialedCount
            prefs[INTERVAL_SECONDS] = progress.intervalSeconds
            prefs[TIMEOUT_SECONDS] = progress.timeoutSeconds
            prefs[DIALS_PER_CUSTOMER] = progress.dialsPerCustomer
            prefs[CURRENT_DIAL_ROUND] = progress.currentDialRound
            prefs[REMAINING_CUSTOMERS] = json.encodeToString(progress.remainingCustomers)
            prefs[ALL_CUSTOMER_IDS] = json.encodeToString(progress.allCustomerIds)
            prefs[IS_ACTIVE] = true
            prefs[LAST_UPDATE_TIME] = System.currentTimeMillis().toInt()
        }
    }

    /**
     * 更新当前拨打索引
     */
    suspend fun updateCurrentIndex(index: Int, dialedCount: Int) {
        context.autoDialProgressDataStore.edit { prefs ->
            prefs[CURRENT_INDEX] = index
            prefs[DIALED_COUNT] = dialedCount
            prefs[LAST_UPDATE_TIME] = System.currentTimeMillis().toInt()
        }
    }

    /**
     * 更新剩余客户列表
     */
    suspend fun updateRemainingCustomers(customers: List<Customer>) {
        context.autoDialProgressDataStore.edit { prefs ->
            prefs[REMAINING_CUSTOMERS] = json.encodeToString(customers)
            prefs[LAST_UPDATE_TIME] = System.currentTimeMillis().toInt()
        }
    }

    /**
     * 清除进度
     */
    suspend fun clearProgress() {
        context.autoDialProgressDataStore.edit { prefs ->
            prefs[IS_ACTIVE] = false
            prefs[CURRENT_INDEX] = 0
            prefs[DIALED_COUNT] = 0
            prefs[REMAINING_CUSTOMERS] = "[]"
        }
    }

    /**
     * 检查是否有可恢复的进度
     */
    suspend fun hasRecoverableProgress(): Boolean {
        var hasProgress = false
        context.autoDialProgressDataStore.data.collect { prefs ->
            val isActive = prefs[IS_ACTIVE] ?: false
            if (isActive) {
                val lastUpdateTime = prefs[LAST_UPDATE_TIME]?.toLong() ?: 0
                hasProgress = System.currentTimeMillis() - lastUpdateTime <= PROGRESS_VALIDITY_MS
            }
        }
        return hasProgress
    }

    /**
     * 获取上次更新的时间
     */
    fun getLastUpdateTime(prefs: Preferences): Long {
        return prefs[LAST_UPDATE_TIME]?.toLong() ?: 0
    }
}
