package com.callcenter.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.ContactDao
import com.callcenter.app.data.local.dao.CustomerDao
import com.callcenter.app.data.local.dao.TaskDao
import com.callcenter.app.data.local.dao.UserDao
import com.callcenter.app.data.local.entity.*

@Database(
    entities = [
        CustomerEntity::class,
        CallRecordEntity::class,
        AutoDialConfigEntity::class,
        TaskEntity::class,
        UserEntity::class,
        ContactEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
}
