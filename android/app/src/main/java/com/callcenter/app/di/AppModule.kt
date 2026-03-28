package com.callcenter.app.di

import android.content.Context
import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.util.CallHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCallHelper(
        @ApplicationContext context: Context
    ): CallHelper {
        return CallHelper(context)
    }

    @Provides
    @Singleton
    fun provideCallSettingsManager(
        @ApplicationContext context: Context
    ): CallSettingsManager {
        return CallSettingsManager(context)
    }
}
