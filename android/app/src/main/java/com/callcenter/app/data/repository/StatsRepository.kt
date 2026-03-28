package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.model.Stats
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val apiService: ApiService
) {
    /**
     * 获取仪表盘统计数据
     */
    suspend fun getDashboardStats(): Result<Stats> {
        return try {
            val response = apiService.getDashboardStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("获取统计数据失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取客服统计数据
     */
    suspend fun getAgentStats(agentId: Int): Result<Stats> {
        return try {
            val response = apiService.getAgentStats(agentId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("获取统计数据失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
