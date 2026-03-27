package com.callcenter.app.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.callcenter.app.R
import com.callcenter.app.data.api.ApiClient
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.preferences.UserPreferences
import com.callcenter.app.ui.activity.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心 Fragment
 */
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    // 视图引用
    private lateinit var tvUsername: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvTodayCalls: TextView
    private lateinit var tvTodayConnect: TextView
    private lateinit var tvTodayDuration: TextView
    private lateinit var btnSync: LinearLayout
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnAbout: LinearLayout
    private lateinit var btnLogout: Button

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var apiClient: ApiClient

    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        initViews(view)

        // 设置事件监听
        setupListeners()

        // 加载用户信息
        loadUserInfo()

        // 加载今日统计
        loadTodayStatistics()
    }

    /**
     * 初始化视图引用
     */
    private fun initViews(view: View) {
        tvUsername = view.findViewById(R.id.tvUsername)
        tvRole = view.findViewById(R.id.tvRole)
        tvTodayCalls = view.findViewById(R.id.tvTodayCalls)
        tvTodayConnect = view.findViewById(R.id.tvTodayConnect)
        tvTodayDuration = view.findViewById(R.id.tvTodayDuration)
        btnSync = view.findViewById(R.id.btnSync)
        btnChangePassword = view.findViewById(R.id.btnChangePassword)
        btnAbout = view.findViewById(R.id.btnAbout)
        btnLogout = view.findViewById(R.id.btnLogout)
    }

    /**
     * 设置事件监听
     */
    private fun setupListeners() {
        // 同步数据
        btnSync.setOnClickListener {
            syncData()
        }

        // 修改密码
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // 关于
        btnAbout.setOnClickListener {
            showAboutDialog()
        }

        // 退出登录
        btnLogout.setOnClickListener {
            logout()
        }
    }

    /**
     * 加载用户信息
     */
    private fun loadUserInfo() {
        lifecycleScope.launch {
            userPreferences.username.collect { username ->
                tvUsername.text = username ?: "未知用户"
            }
        }

        // 设置角色
        tvRole.text = "销售人员"
    }

    /**
     * 加载今日统计
     */
    private fun loadTodayStatistics() {
        lifecycleScope.launch {
            try {
                // 获取服务器地址
                val serverUrl = userPreferences.serverUrl.value
                if (serverUrl.isNullOrEmpty()) {
                    return@launch
                }

                // 初始化 API 服务
                apiService = apiClient.getApiService(serverUrl)

                // 发送请求
                val response = apiService.getTodayStatistics()

                if (response.isSuccessful) {
                    val data = response.body()?.data
                    data?.let {
                        tvTodayCalls.text = it.totalCalls.toString()
                        tvTodayConnect.text = it.connectCalls.toString()
                        tvTodayDuration.text = formatDuration(it.totalDuration)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 格式化通话时长
     */
    private fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) {
            return "0分钟"
        }

        val minutes = seconds / 60
        return "${minutes}分钟"
    }

    /**
     * 同步数据
     */
    private fun syncData() {
        showToast("正在同步数据...")

        lifecycleScope.launch {
            try {
                // 获取服务器地址
                val serverUrl = userPreferences.serverUrl.value
                if (serverUrl.isNullOrEmpty()) {
                    showToast("服务器地址未配置")
                    return@launch
                }

                // 初始化 API 服务
                apiService = apiClient.getApiService(serverUrl)

                // 发送同步请求
                val response = apiService.syncData()

                if (response.isSuccessful) {
                    showToast("同步成功")
                    // 刷新统计
                    loadTodayStatistics()
                } else {
                    showToast("同步失败: ${response.message()}")
                }
            } catch (e: Exception) {
                showToast("同步失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 显示修改密码对话框
     */
    private fun showChangePasswordDialog() {
        // TODO: 实现修改密码对话框
        showToast("修改密码功能开发中")
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        val message = """
            自动拨号销售系统
            
            版本: 1.0.0
            
            功能特点:
            • 自动拨号
            • 客户管理
            • 通话记录
            • 数据统计
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("关于")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 退出登录
     */
    private fun logout() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                // 清除登录信息
                lifecycleScope.launch {
                    userPreferences.clearLoginInfo()
                }

                // 跳转到登录页面
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
