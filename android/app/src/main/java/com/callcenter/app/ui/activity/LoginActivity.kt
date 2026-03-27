package com.callcenter.app.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.callcenter.app.R
import com.callcenter.app.data.api.ApiClient
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录页面
 * 使用传统的 findViewById 方式获取视图
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    // 视图引用
    private lateinit var etServerUrl: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvError: TextView
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var apiClient: ApiClient

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 初始化视图
        initViews()

        // 设置点击事件
        setupClickListeners()

        // 加载保存的服务器地址
        loadSavedData()
    }

    /**
     * 初始化视图引用
     */
    private fun initViews() {
        etServerUrl = findViewById(R.id.etServerUrl)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        tvError = findViewById(R.id.tvError)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
    }

    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            attemptLogin()
        }
    }

    /**
     * 加载保存的数据
     */
    private fun loadSavedData() {
        lifecycleScope.launch {
            // 加载保存的服务器地址
            userPreferences.serverUrl.collect { url ->
                if (!url.isNullOrEmpty()) {
                    etServerUrl.setText(url)
                }
            }
        }

        // 检查是否已登录
        lifecycleScope.launch {
            userPreferences.isLoggedIn.collect { isLoggedIn ->
                if (isLoggedIn) {
                    navigateToMain()
                }
            }
        }
    }

    /**
     * 尝试登录
     */
    private fun attemptLogin() {
        // 获取输入值
        val serverUrl = etServerUrl.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // 验证输入
        if (!validateInput(serverUrl, username, password)) {
            return
        }

        // 显示加载状态
        showLoading(true)
        hideError()

        // 保存服务器地址
        lifecycleScope.launch {
            userPreferences.saveServerUrl(serverUrl)
        }

        // 初始化 API 服务
        apiService = apiClient.getApiService(serverUrl)

        // 执行登录请求
        lifecycleScope.launch {
            try {
                val response = apiService.login(
                    mapOf(
                        "username" to username,
                        "password" to password
                    )
                )

                showLoading(false)

                if (response.isSuccessful && response.body()?.get("success") == true) {
                    // 登录成功
                    val data = response.body()?.get("data") as? Map<*, *>
                    val token = data?.get("token") as? String ?: ""
                    val userId = (data?.get("userId") as? Number)?.toInt() ?: 0

                    // 保存登录信息
                    lifecycleScope.launch {
                        userPreferences.saveLoginInfo(
                            userId = userId,
                            username = username,
                            token = token
                        )
                    }

                    showToast("登录成功")
                    navigateToMain()
                } else {
                    // 登录失败
                    val errorMsg = response.body()?.get("message") as? String
                        ?: "登录失败，请检查用户名和密码"
                    showError(errorMsg)
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("连接服务器失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 验证输入
     */
    private fun validateInput(serverUrl: String, username: String, password: String): Boolean {
        if (serverUrl.isEmpty()) {
            showError("请输入服务器地址")
            etServerUrl.requestFocus()
            return false
        }

        if (username.isEmpty()) {
            showError("请输入用户名")
            etUsername.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            showError("请输入密码")
            etPassword.requestFocus()
            return false
        }

        // 简单的 URL 格式检查
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            showError("服务器地址必须以 http:// 或 https:// 开头")
            etServerUrl.requestFocus()
            return false
        }

        return true
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    /**
     * 隐藏错误信息
     */
    private fun hideError() {
        tvError.visibility = View.GONE
    }

    /**
     * 显示/隐藏加载状态
     */
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        btnLogin.text = if (show) "登录中..." else "登录"
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 导航到主页面
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
