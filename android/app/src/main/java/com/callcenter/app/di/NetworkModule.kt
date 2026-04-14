package com.callcenter.app.di

import android.content.Context
import com.callcenter.app.BuildConfig
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.local.preferences.UserPreferences
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

        // 解析服务器地址
        val serverHttpUrl = serverUrl.toHttpUrlOrDefault()
        val originalPath = originalRequest.url.encodedPath

        // 构建新的 URL：服务器基础地址 + 原始请求路径（去掉可能重复的 /api 前缀）
        // 如果服务器地址已包含 /api/，而原始路径以 /api/ 开头，需要避免重复
        val serverPath = serverHttpUrl.encodedPath
        val newPath = if (serverPath.endsWith("/api/") && originalPath.startsWith("/api/")) {
            // 服务器地址已包含 /api/，去掉原始路径的 /api 前缀
            serverPath + originalPath.removePrefix("/api/").removePrefix("/")
        } else if (serverPath.endsWith("/api") && originalPath.startsWith("/api/")) {
            // 服务器地址以 /api 结尾（没有斜杠），原始路径以 /api/ 开头
            serverPath + originalPath.removePrefix("/api")
        } else {
            // 正常拼接
            serverPath + originalPath.removePrefix("/")
        }

        val newUrl = originalRequest.url.newBuilder()
            .scheme(serverHttpUrl.scheme)
            .host(serverHttpUrl.host)
            .port(serverHttpUrl.port)
            .encodedPath(newPath)
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
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
        return Interceptor { chain ->
            val token = runBlocking { tokenManager.getToken() }
            val builder = chain.request().newBuilder()
            if (token != null) {
                builder.addHeader("Authorization", "Bearer $token")
            }

            val hasContentType = chain.request().header("Content-Type") != null
            val bodyContentType = chain.request().body?.contentType()?.toString()
            if (!hasContentType && (bodyContentType == null || bodyContentType.contains("json", ignoreCase = true))) {
                builder.addHeader("Content-Type", "application/json")
            }

            val request = builder.build()
            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)  // 先添加动态URL拦截器
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
    fun provideDynamicBaseUrlInterceptor(tokenManager: TokenManager): DynamicBaseUrlInterceptor {
        return DynamicBaseUrlInterceptor(tokenManager)
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
