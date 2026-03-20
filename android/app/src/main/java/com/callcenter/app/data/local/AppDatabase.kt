package com.callcenter.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.CustomerDao
import com.callcenter.app.data.local.entity.CallRecordEntity
import com.callcenter.app.data.local.entity.CustomerEntity

@Database(
    entities = [
        CustomerEntity::class,
        CallRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun callRecordDao(): CallRecordDao
}
