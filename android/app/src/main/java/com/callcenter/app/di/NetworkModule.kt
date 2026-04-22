package com.callcenter.app.di

import android.content.Context
import android.util.Log
import com.callcenter.app.BuildConfig
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.local.preferences.UserPreferences
import com.callcenter.app.util.Constants
import com.callcenter.app.util.SessionManager
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
import okhttp3.Response
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

/**
 * 认证响应拦截器
 *
 * 拦截401响应，通知SessionManager处理token过期
 */
class AuthResponseInterceptor(
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : Interceptor {

    companion object {
        private const val TAG = "AuthResponseInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 检查是否为401未授权响应
        if (response.code == 401) {
            Log.w(TAG, "收到401响应，Token可能已过期")

            // 关闭响应体
            response.close()

            // 清除本地token
            runBlocking {
                tokenManager.clearAuth()
            }

            // 通知SessionManager token过期
            sessionManager.notifyTokenExpired()

            // 返回新的401响应（因为原响应已关闭）
            return response.newBuilder()
                .code(401)
                .message("Unauthorized - Token expired")
                .build()
        }

        return response
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
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authResponseInterceptor: AuthResponseInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)  // 先添加动态URL拦截器
            .addInterceptor(authInterceptor)
            .addInterceptor(authResponseInterceptor)  // 添加401响应拦截器
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
    fun provideAuthResponseInterceptor(
        tokenManager: TokenManager,
        sessionManager: SessionManager
    ): AuthResponseInterceptor {
        return AuthResponseInterceptor(tokenManager, sessionManager)
    }

    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager {
        return SessionManager()
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
