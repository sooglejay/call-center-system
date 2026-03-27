package com.callcenter.app.ui.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.callcenter.app.R
import com.callcenter.app.data.api.ApiClient
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.model.CallRecord
import com.callcenter.app.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 通话记录 Fragment
 */
@AndroidEntryPoint
class CallRecordsFragment : Fragment() {

    // 视图引用
    private lateinit var btnDateStart: Button
    private lateinit var btnDateEnd: Button
    private lateinit var btnFilter: Button
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var progressBar: ProgressBar

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var apiClient: ApiClient

    private lateinit var apiService: ApiService
    private lateinit var adapter: CallRecordAdapter

    // 数据
    private var records: List<CallRecord> = emptyList()
    private var startDate: Date? = null
    private var endDate: Date? = null

    // 日期格式化
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_call_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        initViews(view)

        // 设置 RecyclerView
        setupRecyclerView()

        // 设置事件监听
        setupListeners()

        // 加载数据
        loadRecords()
    }

    /**
     * 初始化视图引用
     */
    private fun initViews(view: View) {
        btnDateStart = view.findViewById(R.id.btnDateStart)
        btnDateEnd = view.findViewById(R.id.btnDateEnd)
        btnFilter = view.findViewById(R.id.btnFilter)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        progressBar = view.findViewById(R.id.progressBar)
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CallRecordAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    /**
     * 设置事件监听
     */
    private fun setupListeners() {
        // 开始日期选择
        btnDateStart.setOnClickListener {
            showDatePicker(true)
        }

        // 结束日期选择
        btnDateEnd.setOnClickListener {
            showDatePicker(false)
        }

        // 筛选按钮
        btnFilter.setOnClickListener {
            loadRecords()
        }

        // 下拉刷新
        swipeRefresh.setOnRefreshListener {
            loadRecords()
        }
    }

    /**
     * 显示日期选择器
     */
    private fun showDatePicker(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        val currentDate = if (isStart) startDate else endDate

        currentDate?.let {
            calendar.time = it
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val selectedDate = calendar.time

                if (isStart) {
                    startDate = selectedDate
                    btnDateStart.text = dateFormat.format(selectedDate)
                } else {
                    endDate = selectedDate
                    btnDateEnd.text = dateFormat.format(selectedDate)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    /**
     * 加载通话记录
     */
    private fun loadRecords() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // 获取服务器地址
                val serverUrl = userPreferences.serverUrl.value
                if (serverUrl.isNullOrEmpty()) {
                    showToast("服务器地址未配置")
                    showLoading(false)
                    return@launch
                }

                // 初始化 API 服务
                apiService = apiClient.getApiService(serverUrl)

                // 构建查询参数
                val params = mutableMapOf<String, String>()
                startDate?.let {
                    params["startDate"] = dateFormat.format(it)
                }
                endDate?.let {
                    params["endDate"] = dateFormat.format(it)
                }

                // 发送请求
                val response = apiService.getCallRecords(params)

                showLoading(false)

                if (response.isSuccessful) {
                    val data = response.body()
                    records = data?.data ?: emptyList()
                    updateUI()
                } else {
                    showToast("加载失败: ${response.message()}")
                    showEmpty(true)
                }
            } catch (e: Exception) {
                showLoading(false)
                showToast("加载失败: ${e.message}")
                showEmpty(true)
                e.printStackTrace()
            }
        }
    }

    /**
     * 更新 UI
     */
    private fun updateUI() {
        adapter.submitList(records)

        if (records.isEmpty()) {
            showEmpty(true)
        } else {
            showEmpty(false)
        }
    }

    /**
     * 显示/隐藏加载状态
     */
    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            swipeRefresh.isRefreshing = false
        }
    }

    /**
     * 显示/隐藏空状态
     */
    private fun showEmpty(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
