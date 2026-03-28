package com.callcenter.app.data.local.dao

import androidx.room.*
import com.callcenter.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 任务 DAO
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY priority DESC, created_at DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY priority DESC, created_at DESC")
    suspend fun getTasksByStatus(status: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE assigned_to = :agentId ORDER BY priority DESC, created_at DESC")
    suspend fun getTasksByAgent(agentId: Int): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'pending'")
    fun getPendingTaskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'completed'")
    fun getCompletedTaskCount(): Flow<Int>
}
