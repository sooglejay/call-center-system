package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.entity.CallRecordEntity
import com.callcenter.app.data.model.AddCallNoteRequest
import com.callcenter.app.data.model.CallRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通话记录仓库
 */
@Singleton
class CallRecordRepository @Inject constructor(
    private val apiService: ApiService,
    private val callRecordDao: CallRecordDao
) {
    /**
     * 获取通话记录列表
     */
    suspend fun getCallRecords(
        page: Int = 1,
        pageSize: Int = 20,
        customerId: Int? = null
    ): Result<List<CallRecord>> {
        return try {
            val response = apiService.getCallRecords(
                page = page,
                pageSize = pageSize,
                customerId = customerId
            )

            if (response.isSuccessful && response.body() != null) {
                val records = response.body()!!.data
                // 保存到本地
                records.forEach { record ->
                    callRecordDao.insert(record.toEntity())
                }
                Result.success(records)
            } else {
                // 返回本地数据
                val localRecords = callRecordDao.getRecordsByCustomer(customerId ?: 0)
                Result.success(localRecords.map { it.toModel() })
            }
        } catch (e: Exception) {
            // 返回本地数据
            val localRecords = if (customerId != null) {
                callRecordDao.getRecordsByCustomer(customerId)
            } else {
                callRecordDao.getAllRecords()
            }
            Result.success(localRecords.map { it.toModel() })
        }
    }

    /**
     * 创建通话记录
     */
    suspend fun createCallRecord(
        customerId: Int,
        customerName: String,
        phone: String,
        agentId: Int,
        status: String = "dialing"
    ): Result<CallRecord> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        val entity = CallRecordEntity(
            customerId = customerId,
            customerName = customerName,
            phone = phone,
            agentId = agentId,
            status = status,
            dialedAt = now,
            createdAt = now
        )

        val id = callRecordDao.insert(entity)
        
        return try {
            val response = apiService.createCallRecord(
                CallRecord(
                    customerId = customerId,
                    phone = phone,
                    agentId = agentId,
                    status = status,
                    dialedAt = now
                )
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(entity.copy(id = id.toInt()).toModel())
            }
        } catch (e: Exception) {
            Result.success(entity.copy(id = id.toInt()).toModel())
        }
    }

    /**
     * 更新通话结果
     */
    suspend fun updateCallResult(
        recordId: Int,
        status: String,
        duration: Int
    ): Result<Unit> {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        
        callRecordDao.updateCallResult(recordId, status, duration, now)
        
        return try {
            apiService.updateCallRecord(
                recordId,
                CallRecord(
                    id = recordId,
                    status = status,
                    duration = duration,
                    endedAt = now
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * 添加通话备注
     */
    suspend fun addCallNote(recordId: Int, notes: String): Result<Unit> {
        callRecordDao.updateNotes(recordId, notes)
        
        return try {
            apiService.addCallNote(recordId, AddCallNoteRequest(notes))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * 获取客户的通话历史
     */
    fun getCustomerCallHistory(customerId: Int): Flow<List<CallRecord>> {
        return callRecordDao.getRecordsByCustomer(customerId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 获取通话统计
     */
    suspend fun getCallStats(agentId: Int): CallStats {
        val totalCalls = callRecordDao.getCallCountByAgent(agentId)
        val totalDuration = callRecordDao.getTotalDurationByAgent(agentId)
        val successCalls = callRecordDao.getSuccessCallCountByAgent(agentId)
        
        return CallStats(
            totalCalls = totalCalls,
            totalDuration = totalDuration ?: 0,
            successCalls = successCalls
        )
    }
}

data class CallStats(
    val totalCalls: Int,
    val totalDuration: Int,
    val successCalls: Int
)

// 扩展函数
fun CallRecordEntity.toModel(): CallRecord {
    return CallRecord(
        id = id,
        customerId = customerId,
        phone = phone,
        agentId = agentId,
        direction = direction,
        status = status,
        duration = duration,
        notes = notes,
        dialedAt = dialedAt,
        connectedAt = connectedAt,
        endedAt = endedAt,
        createdAt = createdAt
    )
}

fun CallRecord.toEntity(): CallRecordEntity {
    return CallRecordEntity(
        id = id,
        customerId = customerId,
        customerName = customer?.name ?: "",
        phone = phone,
        agentId = agentId,
        direction = direction,
        status = status,
        duration = duration,
        notes = notes,
        dialedAt = dialedAt,
        connectedAt = connectedAt,
        endedAt = endedAt,
        createdAt = createdAt
    )
}
