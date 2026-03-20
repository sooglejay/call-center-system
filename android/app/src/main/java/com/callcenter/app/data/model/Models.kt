package com.callcenter.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 登录请求
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录响应
 */
data class LoginResponse(
    val token: String,
    val user: User
)

/**
 * 用户信息
 */
data class User(
    val id: Int,
    val username: String,
    @SerializedName("real_name")
    val realName: String,
    val role: String,
    val phone: String?,
    val email: String?,
    @SerializedName("avatar_url")
    val avatarUrl: String?,
    @SerializedName("data_access_type")
    val dataAccessType: String? = "all"
)

/**
 * 客户信息
 */
data class Customer(
    val id: Int,
    val name: String,
    val phone: String,
    val email: String? = null,
    val company: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val status: String = "pending",
    val priority: Int = 1,
    @SerializedName("data_source")
    val dataSource: String = "real",
    @SerializedName("assigned_to")
    val assignedTo: Int? = null,
    @SerializedName("assigned_agent")
    val assignedAgent: User? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

/**
 * 客户列表响应
 */
data class CustomerListResponse(
    val data: List<Customer>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

/**
 * 通话记录
 */
data class CallRecord(
    val id: Int,
    @SerializedName("customer_id")
    val customerId: Int,
    val customer: Customer? = null,
    @SerializedName("agent_id")
    val agentId: Int,
    val agent: User? = null,
    val phone: String,
    val direction: String = "outbound",
    val status: String,
    val duration: Int = 0,
    val notes: String? = null,
    val recording: String? = null,
    @SerializedName("call_sid")
    val callSid: String? = null,
    @SerializedName("dialed_at")
    val dialedAt: String? = null,
    @SerializedName("connected_at")
    val connectedAt: String? = null,
    @SerializedName("ended_at")
    val endedAt: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)

/**
 * 通话记录列表响应
 */
data class CallRecordListResponse(
    val data: List<CallRecord>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int
)

/**
 * 任务信息
 */
data class Task(
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String = "pending",
    val priority: Int = 1,
    @SerializedName("assigned_to")
    val assignedTo: Int? = null,
    @SerializedName("assigned_agent")
    val assignedAgent: User? = null,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

/**
 * 统计数据
 */
data class Stats(
    @SerializedName("total_calls")
    val totalCalls: Int,
    @SerializedName("total_duration")
    val totalDuration: Int,
    @SerializedName("successful_calls")
    val successfulCalls: Int,
    @SerializedName("failed_calls")
    val failedCalls: Int,
    @SerializedName("pending_customers")
    val pendingCustomers: Int,
    @SerializedName("completed_customers")
    val completedCustomers: Int
)

/**
 * API 通用响应
 */
data class ApiResponse<T>(
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)

/**
 * 分页参数
 */
data class PageParams(
    val page: Int = 1,
    val pageSize: Int = 20,
    val search: String? = null,
    val status: String? = null,
    val sortBy: String? = null,
    val sortOrder: String? = null
)

/**
 * 更新客户状态请求
 */
data class UpdateCustomerStatusRequest(
    val status: String,
    val notes: String? = null
)

/**
 * 添加通话备注请求
 */
data class AddCallNoteRequest(
    val notes: String
)
