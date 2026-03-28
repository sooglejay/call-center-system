package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.CustomerDao
import com.callcenter.app.data.model.DashboardStats
import com.callcenter.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仪表盘数据仓库
 */
@Singleton
class DashboardRepository @Inject constructor(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val callRecordDao: CallRecordDao,
    private val userPreferences: UserPreferences
) {

    /**
     * 获取仪表盘统计数据
     */
    suspend fun getDashboardStats(): Result<DashboardStats> {
        return try {
            val response = apiService.getDashboardStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 如果网络请求失败，尝试从本地数据计算
                getLocalDashboardStats()
            }
        } catch (e: Exception) {
            // 网络异常，从本地计算
            getLocalDashboardStats()
        }
    }

    /**
     * 从本地数据计算仪表盘统计
     */
    private suspend fun getLocalDashboardStats(): Result<DashboardStats> {
        return try {
            // 这里可以基于本地数据库计算统计数据
            // 暂时返回一个空的统计对象
            Result.success(
                DashboardStats(
                    todayCalls = 0,
                    todayDuration = 0,
                    todaySuccessRate = 0.0,
                    pendingCustomers = 0,
                    totalCustomers = 0,
                    totalAgents = 0,
                    activeAgents = 0,
                    agentRanking = emptyList(),
                    recentCalls = emptyList()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
