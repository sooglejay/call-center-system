package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.TaskDao
import com.callcenter.app.data.local.entity.TaskEntity
import com.callcenter.app.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务数据仓库
 */
@Singleton
class TaskRepository @Inject constructor(
    private val apiService: ApiService,
    private val taskDao: TaskDao
) {

    /**
     * 将 TaskEntity 转换为 Task
     */
    private fun TaskEntity.toTask(): Task {
        return Task(
            id = this.id,
            title = this.title,
            description = this.description,
            status = this.status,
            priority = this.priority,
            assignedTo = this.assignedTo,
            dueDate = this.dueDate,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * 将 Task 转换为 TaskEntity
     */
    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = this.id,
            title = this.title,
            description = this.description,
            status = this.status,
            priority = this.priority,
            assignedTo = this.assignedTo,
            dueDate = this.dueDate,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    /**
     * 获取任务列表
     */
    suspend fun getTasks(
        page: Int = 1,
        pageSize: Int = 100,
        status: String? = null,
        assignedTo: Int? = null
    ): Result<List<Task>> {
        return try {
            val response = apiService.getTasks(
                page = page,
                pageSize = pageSize,
                status = status,
                assignedTo = assignedTo
            )
            if (response.isSuccessful && response.body() != null) {
                val tasks = response.body()!!.data
                // 缓存到本地
                taskDao.insertTasks(tasks.map { it.toEntity() })
                Result.success(tasks)
            } else {
                // 从本地获取
                val localTasks = taskDao.getAllTasks()
                Result.success(localTasks.map { it.toTask() })
            }
        } catch (e: Exception) {
            // 网络异常，从本地获取
            val localTasks = taskDao.getAllTasks()
            if (localTasks.isNotEmpty()) {
                Result.success(localTasks.map { it.toTask() })
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取我的任务
     */
    suspend fun getMyTasks(): Result<List<Task>> {
        return try {
            val response = apiService.getMyTasks()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("获取任务失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取任务详情
     */
    suspend fun getTask(taskId: Int): Result<Task> {
        return try {
            val response = apiService.getTask(taskId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val localTask = taskDao.getTaskById(taskId)
                if (localTask != null) {
                    Result.success(localTask.toTask())
                } else {
                    Result.failure(Exception("任务不存在"))
                }
            }
        } catch (e: Exception) {
            val localTask = taskDao.getTaskById(taskId)
            if (localTask != null) {
                Result.success(localTask.toTask())
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * 创建任务
     */
    suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return try {
            val response = apiService.createTask(request)
            if (response.isSuccessful && response.body() != null) {
                val task = response.body()!!
                taskDao.insertTask(task.toEntity())
                Result.success(task)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "创建任务失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新任务
     */
    suspend fun updateTask(taskId: Int, task: Task): Result<Task> {
        return try {
            val response = apiService.updateTask(taskId, task)
            if (response.isSuccessful && response.body() != null) {
                val updatedTask = response.body()!!
                taskDao.insertTask(updatedTask.toEntity())
                Result.success(updatedTask)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "更新任务失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除任务
     */
    suspend fun deleteTask(taskId: Int): Result<Unit> {
        return try {
            val response = apiService.deleteTask(taskId)
            if (response.isSuccessful) {
                taskDao.deleteTask(taskId)
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "删除任务失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
