package com.callcenter.app.data.api

import com.callcenter.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API 服务接口
 * 与现有 Web 系统后端对接
 */
interface ApiService {

    // ==================== 认证相关 ====================
    
    /**
     * 登录
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * 获取当前用户信息
     */
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<User>

    /**
     * 更新个人信息
     */
    @PUT("auth/profile")
    suspend fun updateProfile(@Body user: User): Response<User>

    /**
     * 修改密码
     */
    @PUT("auth/password")
    suspend fun changePassword(@Body map: Map<String, String>): Response<ApiResponse<Unit>>

    // ==================== 用户/客服管理 ====================
    
    /**
     * 获取用户列表（管理员）
     */
    @GET("users")
    suspend fun getUsers(
        @Query("role") role: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<UserListResponse>

    /**
     * 获取单个用户
     */
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): Response<User>

    /**
     * 创建用户（管理员）
     */
    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<User>

    /**
     * 更新用户（管理员）
     */
    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: Int,
        @Body user: User
    ): Response<User>

    /**
     * 删除用户（管理员）
     */
    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<ApiResponse<Unit>>

    /**
     * 重置用户密码（管理员）
     */
    @POST("users/{id}/reset-password")
    suspend fun resetPassword(
        @Path("id") id: Int,
        @Body request: ResetPasswordRequest
    ): Response<ApiResponse<Unit>>

    /**
     * 更新用户数据权限
     */
    @PUT("users/{id}/data-access")
    suspend fun updateDataAccess(
        @Path("id") id: Int,
        @Body request: UpdateDataAccessRequest
    ): Response<User>

    /**
     * 获取客服列表
     */
    @GET("users/agents")
    suspend fun getAgents(): Response<List<User>>

    // ==================== 客户管理 ====================
    
    /**
     * 获取客户列表
     */
    @GET("customers")
    suspend fun getCustomers(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("assigned_to") assignedTo: Int? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("sort_order") sortOrder: String? = null
    ): Response<CustomerListResponse>

    /**
     * 获取客户详情
     */
    @GET("customers/{id}")
    suspend fun getCustomer(@Path("id") id: Int): Response<Customer>

    /**
     * 创建客户（管理员）
     */
    @POST("customers")
    suspend fun createCustomer(@Body customer: Customer): Response<Customer>

    /**
     * 更新客户信息
     */
    @PUT("customers/{id}")
    suspend fun updateCustomer(
        @Path("id") id: Int,
        @Body customer: Customer
    ): Response<Customer>

    /**
     * 删除客户（管理员）
     */
    @DELETE("customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int): Response<ApiResponse<Unit>>

    /**
     * 更新客户状态
     */
    @PATCH("customers/{id}/status")
    suspend fun updateCustomerStatus(
        @Path("id") id: Int,
        @Body request: UpdateCustomerStatusRequest
    ): Response<Customer>

    /**
     * 批量分配客服
     */
    @POST("customers/batch-assign")
    suspend fun batchAssignCustomers(
        @Body request: BatchAssignRequest
    ): Response<BatchAssignResponse>

    /**
     * 获取待拨打客户列表
     */
    @GET("customers/pending")
    suspend fun getPendingCustomers(
        @Query("limit") limit: Int = 50
    ): Response<List<Customer>>

    /**
     * 获取下一个待拨打客户
     */
    @GET("customers/next")
    suspend fun getNextCustomer(): Response<Customer?>

    /**
     * 按姓氏首字母获取客户
     */
    @GET("customers/by-name-letter")
    suspend fun getCustomersByNameLetter(
        @Query("letters") letters: String? = null,
        @Query("unassigned_only") unassignedOnly: Boolean = false
    ): Response<CustomerLetterGroupResponse>

    /**
     * 获取姓氏首字母统计
     */
    @GET("customers/name-letter-stats")
    suspend fun getNameLetterStats(
        @Query("unassigned_only") unassignedOnly: Boolean = false
    ): Response<Map<String, Int>>

    // ==================== 通话记录 ====================
    
    /**
     * 获取通话记录列表
     */
    @GET("calls")
    suspend fun getCallRecords(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("customer_id") customerId: Int? = null,
        @Query("agent_id") agentId: Int? = null,
        @Query("status") status: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): Response<CallRecordListResponse>

    /**
     * 创建通话记录
     */
    @POST("calls")
    suspend fun createCallRecord(@Body record: CallRecord): Response<CallRecord>

    /**
     * 更新通话记录
     */
    @PUT("calls/{id}")
    suspend fun updateCallRecord(
        @Path("id") id: Int,
        @Body record: CallRecord
    ): Response<CallRecord>

    /**
     * 添加通话备注
     */
    @PATCH("calls/{id}/notes")
    suspend fun addCallNote(
        @Path("id") id: Int,
        @Body request: AddCallNoteRequest
    ): Response<CallRecord>

    /**
     * 获取客户通话历史
     */
    @GET("calls/customer/{customerId}")
    suspend fun getCustomerCalls(@Path("customerId") customerId: Int): Response<List<CallRecord>>

    // ==================== 任务管理 ====================
    
    /**
     * 获取任务列表
     */
    @GET("tasks")
    suspend fun getTasks(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String? = null,
        @Query("assigned_to") assignedTo: Int? = null
    ): Response<TaskListResponse>

    /**
     * 获取我的任务
     */
    @GET("tasks/my/list")
    suspend fun getMyTasks(): Response<List<Task>>

    /**
     * 获取任务详情
     */
    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): Response<Task>

    /**
     * 创建任务
     */
    @POST("tasks")
    suspend fun createTask(@Body task: CreateTaskRequest): Response<Task>

    /**
     * 更新任务
     */
    @PUT("tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: Int,
        @Body task: Task
    ): Response<Task>

    /**
     * 删除任务
     */
    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): Response<ApiResponse<Unit>>

    // ==================== 统计数据 ====================
    
    /**
     * 获取仪表盘统计数据（管理员）
     */
    @GET("stats/dashboard")
    suspend fun getDashboardStats(): Response<DashboardStats>

    /**
     * 获取我的统计数据
     */
    @GET("stats/my")
    suspend fun getMyStats(): Response<Stats>

    /**
     * 获取客服统计数据
     */
    @GET("stats/agent/{agentId}")
    suspend fun getAgentStats(@Path("agentId") agentId: Int): Response<Stats>

    /**
     * 获取客服排行榜
     */
    @GET("stats/ranking")
    suspend fun getAgentRanking(
        @Query("period") period: String = "today",
        @Query("limit") limit: Int = 10
    ): Response<List<AgentRanking>>

    // ==================== 版本更新 ====================

    /**
     * 检查版本更新
     */
    @GET("version/check")
    suspend fun checkVersion(
        @Query("version_code") versionCode: Int,
        @Query("platform") platform: String = "android"
    ): Response<VersionInfo>
}
