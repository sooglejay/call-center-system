package com.callcenter.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.callcenter.app.R
import com.callcenter.app.data.api.ApiClient
import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 客户列表 Fragment
 */
@AndroidEntryPoint
class CustomerListFragment : Fragment() {

    // 视图引用
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var spinnerStatus: Spinner
    private lateinit var tvTotalCount: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var apiClient: ApiClient

    private lateinit var apiService: ApiService
    private lateinit var adapter: CustomerAdapter

    // 数据
    private var customers: List<Customer> = emptyList()
    private var currentStatus: String? = null
    private var searchKeyword: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_list, container, false)
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
        loadCustomers()
    }

    /**
     * 初始化视图引用
     */
    private fun initViews(view: View) {
        etSearch = view.findViewById(R.id.etSearch)
        btnSearch = view.findViewById(R.id.btnSearch)
        spinnerStatus = view.findViewById(R.id.spinnerStatus)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        progressBar = view.findViewById(R.id.progressBar)
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = CustomerAdapter(
            onItemClick = { customer ->
                // 点击客户详情
                showCustomerDetail(customer)
            },
            onCallClick = { customer ->
                // 拨打电话
                makeCall(customer)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    /**
     * 设置事件监听
     */
    private fun setupListeners() {
        // 搜索按钮
        btnSearch.setOnClickListener {
            searchKeyword = etSearch.text.toString().trim()
            loadCustomers()
        }

        // 下拉刷新
        swipeRefresh.setOnRefreshListener {
            loadCustomers()
        }

        // 空状态刷新按钮
        btnRefresh.setOnClickListener {
            loadCustomers()
        }

        // 状态筛选
        val statusOptions = arrayOf("全部", "待跟进", "已联系", "已成交", "已失败")
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusOptions
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStatus.adapter = spinnerAdapter

        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentStatus = if (position == 0) null else statusOptions[position]
                loadCustomers()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentStatus = null
            }
        }
    }

    /**
     * 加载客户列表
     */
    private fun loadCustomers() {
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
                if (searchKeyword.isNotEmpty()) {
                    params["keyword"] = searchKeyword
                }
                if (!currentStatus.isNullOrEmpty()) {
                    params["status"] = currentStatus!!
                }

                // 发送请求
                val response = apiService.getCustomers(params)

                showLoading(false)

                if (response.isSuccessful) {
                    val data = response.body()
                    customers = data?.data ?: emptyList()
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
        adapter.submitList(customers)
        tvTotalCount.text = "共 ${customers.size} 条"

        if (customers.isEmpty()) {
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
     * 显示客户详情
     */
    private fun showCustomerDetail(customer: Customer) {
        // TODO: 跳转到客户详情页面
        showToast("客户详情: ${customer.name}")
    }

    /**
     * 拨打电话
     */
    private fun makeCall(customer: Customer) {
        // TODO: 实现拨打电话功能
        showToast("拨打: ${customer.phone}")
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
