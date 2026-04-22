package com.callcenter.app.di

import android.content.Context
import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.data.local.preferences.AutoDialProgressManager
import com.callcenter.app.data.local.preferences.FeatureToggleManager
import com.callcenter.app.util.CallHelper
import com.callcenter.app.util.call.CallResultClassifier
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

    @Provides
    @Singleton
    fun provideAutoDialProgressManager(
        @ApplicationContext context: Context
    ): AutoDialProgressManager {
        return AutoDialProgressManager(context)
    }

    @Provides
    @Singleton
    fun provideFeatureToggleManager(
        @ApplicationContext context: Context
    ): FeatureToggleManager {
        return FeatureToggleManager(context)
    }

    @Provides
    @Singleton
    fun provideCallResultClassifier(
        featureToggleManager: FeatureToggleManager
    ): CallResultClassifier {
        return CallResultClassifier(featureToggleManager)
    }
}
