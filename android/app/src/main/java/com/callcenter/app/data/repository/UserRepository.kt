package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.UserDao
import com.callcenter.app.data.local.entity.UserEntity
import com.callcenter.app.data.model.*
import com.callcenter.app.data.local.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户数据仓库
 */
@Singleton
class UserRepository @Inject constructor(
    private val apiService: ApiService,
    private val userDao: UserDao,
    private val userPreferences: UserPreferences
) {

    /**
     * 将 UserEntity 转换为 User
     */
    private fun UserEntity.toUser(): User {
        return User(
            id = this.id,
            username = this.username,
            realName = this.realName,
            role = this.role,
            phone = this.phone,
            email = this.email,
            avatarUrl = this.avatarUrl,
            dataAccessType = this.dataAccessType
        )
    }

    /**
     * 将 User 转换为 UserEntity
     */
    private fun User.toEntity(): UserEntity {
        return UserEntity(
            id = this.id,
            username = this.username,
            realName = this.realName,
            role = this.role,
            phone = this.phone,
            email = this.email,
            avatarUrl = this.avatarUrl,
            dataAccessType = this.dataAccessType
        )
    }

    /**
     * 获取用户列表
     */
    suspend fun getUsers(
        role: String? = null,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 100
    ): Result<List<User>> {
        return try {
            val response = apiService.getUsers(
                role = role,
                status = status,
                page = page,
                pageSize = pageSize
            )
            if (response.isSuccessful && response.body() != null) {
                val users = response.body()!!.data
                // 缓存到本地
                userDao.insertUsers(users.map { it.toEntity() })
                Result.success(users)
            } else {
                // 从本地获取
                val localUsers = userDao.getAllUsers()
                val filtered = if (role != null) {
                    localUsers.filter { it.role == role }
                } else localUsers
                Result.success(filtered.map { it.toUser() })
            }
        } catch (e: Exception) {
            // 网络异常，从本地获取
            val localUsers = userDao.getAllUsers()
            val filtered = if (role != null) {
                localUsers.filter { it.role == role }
            } else localUsers
            if (filtered.isNotEmpty()) {
                Result.success(filtered.map { it.toUser() })
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取单个用户
     */
    suspend fun getUser(userId: Int): Result<User> {
        return try {
            val response = apiService.getUser(userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val localUser = userDao.getUserById(userId)
                if (localUser != null) {
                    Result.success(localUser.toUser())
                } else {
                    Result.failure(Exception("用户不存在"))
                }
            }
        } catch (e: Exception) {
            val localUser = userDao.getUserById(userId)
            if (localUser != null) {
                Result.success(localUser.toUser())
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * 创建用户
     */
    suspend fun createUser(request: CreateUserRequest): Result<User> {
        return try {
            val response = apiService.createUser(request)
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                userDao.insertUser(user.toEntity())
                Result.success(user)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "创建用户失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新用户
     */
    suspend fun updateUser(userId: Int, user: User): Result<User> {
        return try {
            val response = apiService.updateUser(userId, user)
            if (response.isSuccessful && response.body() != null) {
                val updatedUser = response.body()!!
                userDao.insertUser(updatedUser.toEntity())
                Result.success(updatedUser)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "更新用户失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除用户
     */
    suspend fun deleteUser(userId: Int): Result<Unit> {
        return try {
            val response = apiService.deleteUser(userId)
            if (response.isSuccessful) {
                userDao.deleteUser(userId)
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "删除用户失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 重置密码
     */
    suspend fun resetPassword(userId: Int, newPassword: String? = null): Result<Unit> {
        return try {
            val response = apiService.resetPassword(userId, ResetPasswordRequest(newPassword))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "重置密码失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新数据权限
     */
    suspend fun updateDataAccess(userId: Int, dataAccessType: String): Result<Unit> {
        return try {
            val response = apiService.updateDataAccess(userId, UpdateDataAccessRequest(dataAccessType))
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                userDao.insertUser(user.toEntity())
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "更新权限失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取客服列表
     */
    suspend fun getAgents(): Result<List<User>> {
        return try {
            val response = apiService.getAgents()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 从本地过滤
                val localAgents = userDao.getAllUsers().filter { it.role == "agent" }
                Result.success(localAgents.map { it.toUser() })
            }
        } catch (e: Exception) {
            val localAgents = userDao.getAllUsers().filter { it.role == "agent" }
            if (localAgents.isNotEmpty()) {
                Result.success(localAgents.map { it.toUser() })
            } else {
                Result.failure(e)
            }
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
                // 返回空统计
                Result.success(
                    Stats(
                        totalCalls = 0,
                        totalDuration = 0,
                        successfulCalls = 0,
                        failedCalls = 0,
                        pendingCustomers = 0,
                        completedCustomers = 0
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
