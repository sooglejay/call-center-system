package com.callcenter.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.callcenter.app.R
import com.callcenter.app.data.preferences.UserPreferences
import com.callcenter.app.service.AutoDialService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自动拨号 Fragment
 */
@AndroidEntryPoint
class DialerFragment : Fragment() {

    // 视图引用
    private lateinit var switchAutoDial: Switch
    private lateinit var tvAutoDialStatus: TextView
    private lateinit var tvTotalCalls: TextView
    private lateinit var tvSuccessCalls: TextView
    private lateinit var tvPendingCalls: TextView
    private lateinit var currentCallLayout: LinearLayout
    private lateinit var tvCurrentCustomer: TextView
    private lateinit var tvCurrentPhone: TextView
    private lateinit var etInterval: EditText
    private lateinit var etMaxAttempts: EditText
    private lateinit var switchRedial: Switch
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    // 自动拨号服务
    private var autoDialService: AutoDialService? = null

    // 是否正在拨号
    private var isDialing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dialer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        initViews(view)

        // 设置事件监听
        setupListeners()

        // 加载设置
        loadSettings()
    }

    /**
     * 初始化视图引用
     */
    private fun initViews(view: View) {
        switchAutoDial = view.findViewById(R.id.switchAutoDial)
        tvAutoDialStatus = view.findViewById(R.id.tvAutoDialStatus)
        tvTotalCalls = view.findViewById(R.id.tvTotalCalls)
        tvSuccessCalls = view.findViewById(R.id.tvSuccessCalls)
        tvPendingCalls = view.findViewById(R.id.tvPendingCalls)
        currentCallLayout = view.findViewById(R.id.currentCallLayout)
        tvCurrentCustomer = view.findViewById(R.id.tvCurrentCustomer)
        tvCurrentPhone = view.findViewById(R.id.tvCurrentPhone)
        etInterval = view.findViewById(R.id.etInterval)
        etMaxAttempts = view.findViewById(R.id.etMaxAttempts)
        switchRedial = view.findViewById(R.id.switchRedial)
        btnStart = view.findViewById(R.id.btnStart)
        btnStop = view.findViewById(R.id.btnStop)
    }

    /**
     * 设置事件监听
     */
    private fun setupListeners() {
        // 自动拨号开关
        switchAutoDial.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvAutoDialStatus.text = "等待开始..."
            } else {
                stopAutoDial()
                tvAutoDialStatus.text = "已停止"
            }
        }

        // 开始按钮
        btnStart.setOnClickListener {
            startAutoDial()
        }

        // 停止按钮
        btnStop.setOnClickListener {
            stopAutoDial()
        }

        // 保存设置
        etInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveSettings()
            }
        }

        etMaxAttempts.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveSettings()
            }
        }

        switchRedial.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        lifecycleScope.launch {
            userPreferences.autoDialSettings.collect { settings ->
                etInterval.setText(settings.intervalSeconds.toString())
                etMaxAttempts.setText(settings.maxAttempts.toString())
                switchRedial.isChecked = settings.autoRedial
            }
        }
    }

    /**
     * 保存设置
     */
    private fun saveSettings() {
        val interval = etInterval.text.toString().toIntOrNull() ?: 30
        val maxAttempts = etMaxAttempts.text.toString().toIntOrNull() ?: 3
        val autoRedial = switchRedial.isChecked

        lifecycleScope.launch {
            userPreferences.saveAutoDialSettings(
                intervalSeconds = interval,
                maxAttempts = maxAttempts,
                autoRedial = autoRedial
            )
        }
    }

    /**
     * 开始自动拨号
     */
    private fun startAutoDial() {
        if (isDialing) {
            return
        }

        // 保存设置
        saveSettings()

        // 更新 UI 状态
        isDialing = true
        updateDialingUI(true)

        // 显示当前拨号信息
        currentCallLayout.visibility = View.VISIBLE
        tvCurrentCustomer.text = "正在准备..."
        tvCurrentPhone.text = ""

        // TODO: 启动自动拨号服务
        // 这里需要实现实际的拨号逻辑
        showToast("自动拨号已启动")

        // 模拟更新统计数据
        updateStatistics(0, 0, 10)
    }

    /**
     * 停止自动拨号
     */
    private fun stopAutoDial() {
        if (!isDialing) {
            return
        }

        // 更新 UI 状态
        isDialing = false
        updateDialingUI(false)

        // 隐藏当前拨号信息
        currentCallLayout.visibility = View.GONE

        // 关闭开关
        switchAutoDial.isChecked = false

        showToast("自动拨号已停止")
    }

    /**
     * 更新拨号 UI 状态
     */
    private fun updateDialingUI(isDialing: Boolean) {
        btnStart.isEnabled = !isDialing
        btnStop.isEnabled = isDialing

        if (isDialing) {
            tvAutoDialStatus.text = "正在拨号..."
            switchAutoDial.isChecked = true
        } else {
            tvAutoDialStatus.text = "已停止"
        }
    }

    /**
     * 更新统计数据
     */
    private fun updateStatistics(total: Int, success: Int, pending: Int) {
        tvTotalCalls.text = total.toString()
        tvSuccessCalls.text = success.toString()
        tvPendingCalls.text = pending.toString()
    }

    /**
     * 更新当前拨号客户
     */
    fun updateCurrentCustomer(name: String, phone: String) {
        if (isAdded) {
            requireActivity().runOnUiThread {
                tvCurrentCustomer.text = name
                tvCurrentPhone.text = phone
            }
        }
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 停止自动拨号
        if (isDialing) {
            stopAutoDial()
        }
    }
}
