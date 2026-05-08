package com.callcenter.app.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import kotlinx.serialization.Serializable as KSerializable

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
@KSerializable
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
) : Serializable

/**
 * 客户信息
 */
@KSerializable
data class Customer(
    val id: Int,
    val name: String? = null,
    val phone: String? = null,
    val tag: String = "未打标客户",
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
    val updatedAt: String? = null,
    // 任务相关字段
    @SerializedName("source_type")
    val sourceType: String? = null, // "task" 或 null(直接分配)
    @SerializedName("task_id")
    val taskId: Int? = null,
    @SerializedName("task_title")
    val taskTitle: String? = null
) : Serializable

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

typealias CustomerTagListResponse = List<String>

/**
 * 通话记录
 */
data class CallRecord(
    val id: Int = 0,
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

data class CallRecordingUploadResponse(
    val message: String,
    @SerializedName("recording_url")
    val recordingUrl: String,
    val call: CallRecord? = null
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
    val priority: String = "normal",
    @SerializedName("assigned_to")
    val assignedTo: Int? = null,
    @SerializedName("assigned_agent")
    val assignedAgent: User? = null,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    // 任务统计字段
    @SerializedName("customer_count")
    val customerCount: Int = 0,
    @SerializedName("completed_count")
    val completedCount: Int = 0,
    @SerializedName("called_count")
    val calledCount: Int = 0,
    @SerializedName("progress")
    val progress: Int = 0,
    // 任务客户列表（仅在详情时返回）
    @SerializedName("customers")
    val customers: List<TaskCustomer>? = null
) {
    /**
     * 获取优先级数值（用于UI显示）
     * normal -> 1, high -> 2, urgent -> 3
     */
    fun getPriorityValue(): Int {
        return when (priority.lowercase()) {
            "urgent" -> 3
            "high" -> 2
            else -> 1
        }
    }
}

/**
 * 任务中的客户信息
 */
data class TaskCustomer(
    @SerializedName("task_customer_id")
    val taskCustomerId: Int,
    val id: Int? = null,  // 改为可空，因为后端可能返回 null
    val name: String? = null,
    val phone: String? = null,
    val tag: String = "未打标客户",
    val email: String? = null,
    val company: String? = null,
    @SerializedName("customer_status")
    val customerStatus: String? = null,
    @SerializedName("call_status")
    val callStatus: String = "pending",
    @SerializedName("call_result")
    val callResult: String? = null,
    @SerializedName("called_at")
    val calledAt: String? = null,
    @SerializedName("call_id")
    val callId: Int? = null,
    @SerializedName("call_duration")
    val callDuration: Int? = null,
    @SerializedName("is_connected")
    val isConnected: Boolean = false,
    @SerializedName("call_time")
    val callTime: String? = null,
    @SerializedName("recording_url")
    val recordingUrl: String? = null
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
    @SerializedName("connected_calls")
    val connectedCalls: Int = 0, // 兼容 Web 端字段名
    @SerializedName("failed_calls")
    val failedCalls: Int,
    @SerializedName("pending_customers")
    val pendingCustomers: Int,
    @SerializedName("completed_customers")
    val completedCustomers: Int,
    @SerializedName("avg_duration")
    val avgDuration: Int = 0,
    @SerializedName("connection_rate")
    val connectionRate: Double = 0.0,
    // 扩展字段
    @SerializedName("today_calls")
    val todayCalls: Int = 0,
    @SerializedName("today_duration")
    val todayDuration: Int = 0,
    @SerializedName("today_successful")
    val todaySuccessful: Int = 0,
    @SerializedName("week_calls")
    val weekCalls: Int = 0,
    @SerializedName("week_duration")
    val weekDuration: Int = 0,
    @SerializedName("month_calls")
    val monthCalls: Int = 0,
    @SerializedName("month_duration")
    val monthDuration: Int = 0
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
 * 任务客户列表响应（分页）
 */
data class TaskCustomerListResponse(
    val data: List<TaskCustomer>,
    val total: Int,
    val page: Int,
    val page_size: Int,
    val total_pages: Int
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

// ==================== 用户/客服管理相关 ====================

/**
 * 用户列表响应
 */
data class UserListResponse(
    val data: List<User>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

/**
 * 创建用户请求
 */
data class CreateUserRequest(
    val username: String,
    val password: String,
    @SerializedName("real_name")
    val realName: String,
    val role: String = "agent",
    val phone: String? = null,
    val email: String? = null,
    @SerializedName("data_access_type")
    val dataAccessType: String = "all"
)

/**
 * 重置密码请求
 */
data class ResetPasswordRequest(
    @SerializedName("new_password")
    val newPassword: String? = null // 不传则使用默认密码
)

/**
 * 更新数据权限请求
 */
data class UpdateDataAccessRequest(
    @SerializedName("data_access_type")
    val dataAccessType: String
)

// ==================== 任务管理相关 ====================

/**
 * 任务列表响应
 */
data class TaskListResponse(
    val data: List<Task>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)

/**
 * 创建任务请求
 */
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "normal",
    @SerializedName("assigned_to")
    val assignedTo: Int? = null,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("customer_ids")
    val customerIds: List<Int>? = null // 关联的客户ID列表
)

/**
 * 更新任务客户状态请求（客服执行任务时使用）
 */
data class UpdateTaskCustomerStatusRequest(
    val status: String, // pending, called, connected, completed, failed
    @SerializedName("call_result")
    val callResult: String? = null,
    @SerializedName("call_id")
    val callId: Int? = null,
    @SerializedName("call_duration")
    val callDuration: Int? = null // 通话时长（秒）
)

/**
 * 更新任务客户信息请求（编辑电话号码等）
 */
data class UpdateTaskCustomerInfoRequest(
    val name: String? = null,
    val phone: String? = null,
    val company: String? = null
)

// ==================== 统计相关 ====================

/**
 * 仪表盘统计数据（管理员）
 */
data class DashboardStats(
    // Web 端字段
    @SerializedName("total_customers")
    val totalCustomers: Int,
    @SerializedName("total_calls")
    val totalCalls: Int,
    @SerializedName("connection_rate")
    val connectionRate: Double,
    @SerializedName("active_agents")
    val activeAgents: Int,
    // Android 扩展字段
    @SerializedName("today_calls")
    val todayCalls: Int = 0,
    @SerializedName("today_duration")
    val todayDuration: Int = 0,
    @SerializedName("today_success_rate")
    val todaySuccessRate: Double = 0.0,
    @SerializedName("pending_customers")
    val pendingCustomers: Int = 0,
    @SerializedName("total_agents")
    val totalAgents: Int = 0,
    @SerializedName("agent_ranking")
    val agentRanking: List<AgentRanking> = emptyList(),
    @SerializedName("recent_calls")
    val recentCalls: List<CallRecord> = emptyList(),
    @SerializedName("trend")
    val trend: List<TrendData> = emptyList()
)

/**
 * 趋势数据
 */
data class TrendData(
    val date: String,
    @SerializedName("total_calls")
    val totalCalls: Int,
    @SerializedName("connected_calls")
    val connectedCalls: Int
)

/**
 * 客服排行榜
 */
data class AgentRanking(
    @SerializedName("agent_id")
    val agentId: Int,
    @SerializedName("agent_name")
    val agentName: String,
    @SerializedName("total_calls")
    val totalCalls: Int,
    @SerializedName("successful_calls")
    val successfulCalls: Int = 0,
    @SerializedName("connected_calls")
    val connectedCalls: Int = 0, // 兼容 Web 端
    @SerializedName("total_duration")
    val totalDuration: Int,
    @SerializedName("success_rate")
    val successRate: Double = 0.0,
    @SerializedName("connection_rate")
    val connectionRate: Double = 0.0 // 兼容 Web 端
)

/**
 * 客服详细统计
 */
data class AgentStats(
    @SerializedName("agent_id")
    val agentId: Int,
    @SerializedName("agent_name")
    val agentName: String,
    @SerializedName("today_calls")
    val todayCalls: Int,
    @SerializedName("today_duration")
    val todayDuration: Int,
    @SerializedName("today_successful")
    val todaySuccessful: Int,
    @SerializedName("week_calls")
    val weekCalls: Int,
    @SerializedName("week_duration")
    val weekDuration: Int,
    @SerializedName("month_calls")
    val monthCalls: Int,
    @SerializedName("month_duration")
    val monthDuration: Int,
    @SerializedName("pending_customers")
    val pendingCustomers: Int,
    @SerializedName("completed_customers")
    val completedCustomers: Int
)

// ==================== 批量操作相关 ====================

/**
 * 批量分配请求
 */
data class BatchAssignRequest(
    @SerializedName("customer_ids")
    val customerIds: List<Int>,
    @SerializedName("assigned_to")
    val assignedTo: Int
)

/**
 * 批量分配响应
 */
data class BatchAssignResponse(
    val success: Boolean,
    @SerializedName("assigned_count")
    val assignedCount: Int,
    val message: String
)

/**
 * 客户按首字母分组响应
 */
data class CustomerLetterGroupResponse(
    val groups: Map<String, List<Customer>>
)

// ==================== 版本更新相关 ====================

/**
 * 版本信息响应
 */
data class VersionInfo(
    @SerializedName("version_code")
    val versionCode: Int,
    @SerializedName("version_name")
    val versionName: String,
    @SerializedName("apk_url")
    val apkUrl: String,
    @SerializedName("update_log")
    val updateLog: String? = null,
    @SerializedName("force_update")
    val forceUpdate: Boolean = false,
    @SerializedName("min_version_code")
    val minVersionCode: Int? = null
)
