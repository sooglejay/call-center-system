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
     * 更新客户信息
     */
    @PUT("customers/{id}")
    suspend fun updateCustomer(
        @Path("id") id: Int,
        @Body customer: Customer
    ): Response<Customer>

    /**
     * 更新客户状态
     */
    @PATCH("customers/{id}/status")
    suspend fun updateCustomerStatus(
        @Path("id") id: Int,
        @Body request: UpdateCustomerStatusRequest
    ): Response<Customer>

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

    // ==================== 通话记录 ====================
    
    /**
     * 获取通话记录列表
     */
    @GET("calls")
    suspend fun getCallRecords(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("customer_id") customerId: Int? = null,
        @Query("agent_id") agentId: Int? = null
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
    ): Response<List<Task>>

    /**
     * 创建任务
     */
    @POST("tasks")
    suspend fun createTask(@Body task: Task): Response<Task>

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
     * 获取仪表盘统计数据
     */
    @GET("stats/dashboard")
    suspend fun getDashboardStats(): Response<Stats>

    /**
     * 获取客服统计数据
     */
    @GET("stats/agent/{agentId}")
    suspend fun getAgentStats(@Path("agentId") agentId: Int): Response<Stats>

    // ==================== 用户管理 ====================
    
    /**
     * 获取客服列表
     */
    @GET("users/agents")
    suspend fun getAgents(): Response<List<User>>
}
