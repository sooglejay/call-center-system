package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.CustomerDao
import com.callcenter.app.data.model.Stats
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统计数据仓库
 */
@Singleton
class StatsRepository @Inject constructor(
    private val apiService: ApiService,
    private val callRecordDao: CallRecordDao,
    private val customerDao: CustomerDao
) {

    /**
     * 获取我的统计数据
     */
    suspend fun getMyStats(): Result<Stats> {
        return try {
            val response = apiService.getMyStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 从本地数据计算
                getLocalStats()
            }
        } catch (e: Exception) {
            // 网络异常，从本地计算
            getLocalStats()
        }
    }

    /**
     * 从本地数据计算统计
     */
    private suspend fun getLocalStats(): Result<Stats> {
        return try {
            // 这里可以基于本地数据库计算统计数据
            val callRecords = callRecordDao.getAllRecords()
            val customers = customerDao.getAllCustomers()

            val totalCalls = callRecords.size
            val successfulCalls = callRecords.count { record -> record.status == "completed" }
            val totalDuration = callRecords.sumOf { record -> record.duration }
            val pendingCustomers = customers.count { customer -> customer.status == "pending" }
            val completedCustomers = customers.count { customer -> customer.status == "completed" }

            Result.success(
                Stats(
                    totalCalls = totalCalls,
                    totalDuration = totalDuration,
                    successfulCalls = successfulCalls,
                    failedCalls = totalCalls - successfulCalls,
                    pendingCustomers = pendingCustomers,
                    completedCustomers = completedCustomers
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
