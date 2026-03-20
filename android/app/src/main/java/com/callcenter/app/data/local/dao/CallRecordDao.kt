package com.callcenter.app.data.local.dao

import androidx.room.*
import com.callcenter.app.data.local.entity.CallRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getRecordsByCustomer(customerId: Int): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE agentId = :agentId ORDER BY createdAt DESC")
    fun getRecordsByAgent(agentId: Int): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE id = :id")
    suspend fun getRecordById(id: Int): CallRecordEntity?

    @Query("SELECT * FROM call_records WHERE phone = :phone ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRecordByPhone(phone: String): CallRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CallRecordEntity): Long

    @Update
    suspend fun update(record: CallRecordEntity)

    @Query("UPDATE call_records SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Int, notes: String)

    @Query("UPDATE call_records SET status = :status, duration = :duration, endedAt = :endedAt WHERE id = :id")
    suspend fun updateCallResult(id: Int, status: String, duration: Int, endedAt: String)

    @Delete
    suspend fun delete(record: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM call_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM call_records WHERE agentId = :agentId")
    fun getCallCountByAgent(agentId: Int): Flow<Int>

    @Query("SELECT SUM(duration) FROM call_records WHERE agentId = :agentId")
    fun getTotalDurationByAgent(agentId: Int): Flow<Int?>

    @Query("SELECT COUNT(*) FROM call_records WHERE agentId = :agentId AND status = 'completed'")
    fun getSuccessCallCountByAgent(agentId: Int): Flow<Int>
}
