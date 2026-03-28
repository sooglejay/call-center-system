package com.callcenter.app.di

import android.content.Context
import com.callcenter.app.BuildConfig
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 动态 Base URL 支持
 * 允许在运行时更改服务器地址
 */
class DynamicBaseUrlInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val serverUrl = runBlocking { tokenManager.getServerUrl() } ?: Constants.DEFAULT_SERVER_URL

        // 如果 URL 是相对于 baseUrl 的，则使用动态 serverUrl
        val newUrl = originalRequest.url.newBuilder()
            .scheme("http")
            .host(serverUrl.toHttpUrlOrDefault().host)
            .port(serverUrl.toHttpUrlOrDefault().port)
            .encodedPath(serverUrl.toHttpUrlOrDefault().encodedPath + originalRequest.url.encodedPath.removePrefix("/"))
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }

    private fun String.toHttpUrlOrDefault(): okhttp3.HttpUrl {
        return this.toHttpUrlOrNull() ?: Constants.DEFAULT_SERVER_URL.toHttpUrlOrNull()!!
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
        return Interceptor { chain ->
            val token = runBlocking { tokenManager.getToken() }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
            } else {
                chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
            }
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // 使用占位符 baseUrl，实际请求时会通过拦截器替换
        return Retrofit.Builder()
            .baseUrl(Constants.DEFAULT_SERVER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
