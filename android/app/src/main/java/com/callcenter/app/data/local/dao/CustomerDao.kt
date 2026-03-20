package com.callcenter.app.data.local.dao

import androidx.room.*
import com.callcenter.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY priority DESC, created_at DESC")
    fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers WHERE status = :status ORDER BY priority DESC, created_at DESC")
    fun getCustomersByStatus(status: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE status = 'pending' ORDER BY priority DESC, created_at ASC LIMIT 1")
    suspend fun getNextPendingCustomer(): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun searchCustomers(query: String): List<CustomerEntity>

    @Query("SELECT COUNT(*) FROM customers WHERE status = 'pending'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers WHERE status = 'completed'")
    fun getCompletedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE customers SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, updatedAt: String = System.currentTimeMillis().toString())

    @Query("UPDATE customers SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Int, notes: String)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCount(): Int
}
