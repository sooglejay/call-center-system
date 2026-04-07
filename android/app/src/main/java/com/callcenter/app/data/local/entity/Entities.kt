package com.callcenter.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 客户实体
 */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val phone: String,
    val email: String? = null,
    val company: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val status: String = "pending",
    val priority: Int = 1,
    val dataSource: String = "real",
    val assignedTo: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncStatus: String = "synced" // synced, pending, error
)

/**
 * 通话记录实体
 */
@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val customerId: Int,
    val customerName: String,
    val phone: String,
    val agentId: Int,
    val direction: String = "outbound",
    val status: String,
    val duration: Int = 0,
    val notes: String? = null,
    val dialedAt: String? = null,
    val connectedAt: String? = null,
    val endedAt: String? = null,
    val createdAt: String? = null,
    val syncStatus: String = "synced"
)

/**
 * 自动拨号配置实体
 */
@Entity(tableName = "auto_dial_config")
data class AutoDialConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val enabled: Boolean = false,
    val intervalSeconds: Int = 10,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 0,
    val autoHangup: Boolean = true,
    val pauseAfterCall: Boolean = true,
    val pauseDuration: Int = 5
)

/**
 * 任务实体
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String = "pending",
    val priority: String = "normal",
    val assignedTo: Int? = null,
    val dueDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncStatus: String = "synced",
    // 任务统计字段
    val customerCount: Int = 0,
    val completedCount: Int = 0,
    val calledCount: Int = 0,
    val progress: Int = 0
)

/**
 * 用户实体
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Int,
    val username: String,
    val realName: String,
    val role: String,
    val phone: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val dataAccessType: String? = "all",
    val syncStatus: String = "synced"
)
