package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.CustomerDao
import com.callcenter.app.data.local.dao.TaskDao
import com.callcenter.app.data.local.dao.UserDao
import com.callcenter.app.data.local.entity.CustomerEntity
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.UpdateCustomerStatusRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 客户仓库
 * 支持离线模式，先从本地数据库读取，再从服务器同步
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val callRecordDao: CallRecordDao,
    private val taskDao: TaskDao,
    private val userDao: UserDao
) {
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 获取客户列表（管理员使用）
     */
    suspend fun getCustomers(
        page: Int = 1,
        pageSize: Int = 20,
        search: String? = null,
        status: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<Customer>> {
        _isLoading.value = true
        return try {
            // 先从本地数据库读取
            val localEntities = if (search != null) {
                customerDao.searchCustomers(search)
            } else if (status != null) {
                customerDao.getCustomersByStatus(status)
            } else {
                customerDao.getAllCustomers()
            }
            val localCustomers: List<Customer> = localEntities.map { it.toModel() }

            // 如果有本地数据且不需要刷新，直接返回
            if (localCustomers.isNotEmpty() && !forceRefresh) {
                _customers.value = localCustomers
                _isLoading.value = false
                return Result.success(localCustomers)
            }

            // 从服务器获取
            val response = apiService.getCustomers(
                page = page,
                pageSize = pageSize,
                search = search,
                status = status
            )

            if (response.isSuccessful && response.body() != null) {
                val serverCustomers: List<Customer> = response.body()!!.data
                _customers.value = serverCustomers
                
                // 保存到本地数据库
                customerDao.deleteAll()
                customerDao.insertAll(serverCustomers.map { it.toEntity() })
                
                Result.success(serverCustomers)
            } else {
                // 服务器请求失败，返回本地数据
                _customers.value = localCustomers
                Result.success(localCustomers)
            }
        } catch (e: Exception) {
            // 网络错误，返回本地数据
            val localCustomers: List<Customer> = customerDao.getAllCustomers().map { it.toModel() }
            _customers.value = localCustomers
            Result.success(localCustomers)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 获取客服专属客户列表（客服角色使用）
     */
    suspend fun getAgentCustomers(
        page: Int = 1,
        pageSize: Int = 20,
        search: String? = null,
        status: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<Customer>> {
        _isLoading.value = true
        return try {
            // 先从本地数据库读取
            val localEntities = if (search != null) {
                customerDao.searchCustomers(search)
            } else if (status != null) {
                customerDao.getCustomersByStatus(status)
            } else {
                customerDao.getAllCustomers()
            }
            val localCustomers: List<Customer> = localEntities.map { it.toModel() }

            // 如果有本地数据且不需要刷新，直接返回
            if (localCustomers.isNotEmpty() && !forceRefresh) {
                _customers.value = localCustomers
                _isLoading.value = false
                return Result.success(localCustomers)
            }

            // 从服务器获取客服专属客户
            val response = apiService.getAgentCustomers(
                page = page,
                pageSize = pageSize,
                search = search,
                status = status
            )

            if (response.isSuccessful && response.body() != null) {
                val serverCustomers: List<Customer> = response.body()!!.data
                _customers.value = serverCustomers
                
                // 保存到本地数据库
                customerDao.deleteAll()
                customerDao.insertAll(serverCustomers.map { it.toEntity() })
                
                Result.success(serverCustomers)
            } else {
                // 服务器请求失败，返回本地数据
                _customers.value = localCustomers
                Result.success(localCustomers)
            }
        } catch (e: Exception) {
            // 网络错误，返回本地数据
            val localCustomers: List<Customer> = customerDao.getAllCustomers().map { it.toModel() }
            _customers.value = localCustomers
            Result.success(localCustomers)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 获取待拨打客户列表
     */
    suspend fun getPendingCustomers(): Result<List<Customer>> {
        return try {
            val response = apiService.getPendingCustomers()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 从本地获取
                val localCustomers = customerDao.getCustomersByStatus("pending").map { it.toModel() }
                Result.success(localCustomers)
            }
        } catch (e: Exception) {
            val localCustomers = customerDao.getCustomersByStatus("pending").map { it.toModel() }
            Result.success(localCustomers)
        }
    }

    /**
     * 获取下一个待拨打客户
     */
    suspend fun getNextCustomer(): Result<Customer?> {
        return try {
            val response = apiService.getNextCustomer()
            if (response.isSuccessful) {
                Result.success(response.body())
            } else {
                // 从本地获取
                val localCustomer = customerDao.getNextPendingCustomer()?.toModel()
                Result.success(localCustomer)
            }
        } catch (e: Exception) {
            val localCustomer = customerDao.getNextPendingCustomer()?.toModel()
            Result.success(localCustomer)
        }
    }

    /**
     * 更新客户状态
     */
    suspend fun updateCustomerStatus(
        customerId: Int,
        status: String,
        notes: String? = null
    ): Result<Customer> {
        return try {
            val response = apiService.updateCustomerStatus(
                customerId,
                UpdateCustomerStatusRequest(status, notes)
            )
            if (response.isSuccessful && response.body() != null) {
                // 更新本地数据库
                customerDao.updateStatus(customerId, status)
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("更新失败"))
            }
        } catch (e: Exception) {
            // 离线模式，只更新本地
            customerDao.updateStatus(customerId, status)
            val customer = customerDao.getCustomerById(customerId)?.toModel()
            if (customer != null) {
                Result.success(customer)
            } else {
                Result.failure(Exception("客户不存在"))
            }
        }
    }

    /**
     * 获取单个客户详情
     */
    suspend fun getCustomer(customerId: Int): Result<Customer> {
        return try {
            // 先尝试从服务器获取
            val response = apiService.getCustomer(customerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // 从本地获取
                val localCustomer = customerDao.getCustomerById(customerId)?.toModel()
                if (localCustomer != null) {
                    Result.success(localCustomer)
                } else {
                    Result.failure(Exception("客户不存在"))
                }
            }
        } catch (e: Exception) {
            // 从本地获取
            val localCustomer = customerDao.getCustomerById(customerId)?.toModel()
            if (localCustomer != null) {
                Result.success(localCustomer)
            } else {
                Result.failure(Exception("客户不存在: ${e.message}"))
            }
        }
    }

    /**
     * 更新客户信息
     */
    suspend fun updateCustomer(customerId: Int, customer: Customer): Result<Customer> {
        return try {
            val response = apiService.updateCustomer(customerId, customer)
            if (response.isSuccessful && response.body() != null) {
                // 更新本地数据库
                customerDao.update(customer.toEntity())
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("更新失败"))
            }
        } catch (e: Exception) {
            // 离线模式，只更新本地
            customerDao.update(customer.toEntity())
            Result.success(customer)
        }
    }

    /**
     * 刷新数据
     */
    suspend fun refresh() {
        getCustomers(forceRefresh = true)
    }

    /**
     * 清空所有用户相关数据（退出登录时调用）
     */
    suspend fun clearAllData() {
        _customers.value = emptyList()
        customerDao.deleteAll()
        callRecordDao.deleteAll()
        taskDao.deleteAllTasks()
        userDao.deleteAllUsers()
    }
}

// 扩展函数：Entity -> Model
fun CustomerEntity.toModel(): Customer {
    return Customer(
        id = id,
        name = name,
        phone = phone,
        email = email,
        company = company,
        address = address,
        notes = notes,
        status = status,
        priority = priority,
        dataSource = dataSource,
        assignedTo = assignedTo,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// 扩展函数：Model -> Entity
fun Customer.toEntity(): CustomerEntity {
    return CustomerEntity(
        id = id,
        name = name ?: "",
        phone = phone ?: "",
        email = email,
        company = company,
        address = address,
        notes = notes,
        status = status,
        priority = priority,
        dataSource = dataSource,
        assignedTo = assignedTo,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
