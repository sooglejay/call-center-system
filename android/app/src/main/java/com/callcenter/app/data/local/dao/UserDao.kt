package com.callcenter.app.data.local.dao

import androidx.room.*
import com.callcenter.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户 DAO
 */
@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE role = :role ORDER BY real_name ASC")
    suspend fun getUsersByRole(role: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("SELECT COUNT(*) FROM users WHERE role = 'agent'")
    fun getAgentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE data_access_type != 'disabled'")
    fun getActiveUserCount(): Flow<Int>
}
