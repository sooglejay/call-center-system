package com.callcenter.app.di

import android.content.Context
import androidx.room.Room
import com.callcenter.app.data.local.AppDatabase
import com.callcenter.app.data.local.dao.CallRecordDao
import com.callcenter.app.data.local.dao.CustomerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "call_center.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCustomerDao(database: AppDatabase): CustomerDao {
        return database.customerDao()
    }

    @Provides
    fun provideCallRecordDao(database: AppDatabase): CallRecordDao {
        return database.callRecordDao()
    }
}
