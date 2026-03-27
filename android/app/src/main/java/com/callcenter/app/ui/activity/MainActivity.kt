package com.callcenter.app.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.callcenter.app.R
import com.callcenter.app.data.preferences.UserPreferences
import com.callcenter.app.ui.fragment.CallRecordsFragment
import com.callcenter.app.ui.fragment.CustomerListFragment
import com.callcenter.app.ui.fragment.DialerFragment
import com.callcenter.app.ui.fragment.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主页面
 * 包含底部导航和各个 Fragment
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // 视图引用
    private lateinit var tvTitle: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView

    // 依赖注入
    @Inject
    lateinit var userPreferences: UserPreferences

    // 当前显示的 Fragment
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews()

        // 设置底部导航
        setupBottomNavigation()

        // 检查登录状态
        checkLoginStatus()

        // 默认显示客户列表
        if (savedInstanceState == null) {
            showFragment(CustomerListFragment())
        }
    }

    /**
     * 初始化视图引用
     */
    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        btnSettings = findViewById(R.id.btnSettings)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // 设置按钮点击事件
        btnSettings.setOnClickListener {
            // 跳转到设置页面
            showFragment(ProfileFragment())
            bottomNavigation.selectedItemId = R.id.nav_profile
        }
    }

    /**
     * 设置底部导航
     */
    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_customers -> {
                    tvTitle.text = "客户列表"
                    showFragment(CustomerListFragment())
                    true
                }
                R.id.nav_dialer -> {
                    tvTitle.text = "自动拨号"
                    showFragment(DialerFragment())
                    true
                }
                R.id.nav_records -> {
                    tvTitle.text = "通话记录"
                    showFragment(CallRecordsFragment())
                    true
                }
                R.id.nav_profile -> {
                    tvTitle.text = "我的"
                    showFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 检查登录状态
     */
    private fun checkLoginStatus() {
        lifecycleScope.launch {
            userPreferences.isLoggedIn.collect { isLoggedIn ->
                if (!isLoggedIn) {
                    navigateToLogin()
                }
            }
        }
    }

    /**
     * 显示 Fragment
     */
    private fun showFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /**
     * 导航到登录页面
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
