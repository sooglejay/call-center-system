package com.callcenter.app.ui.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.callcenter.app.ui.theme.CallCenterTheme

/**
 * 拨号入口 Activity
 * 
 * 这个 Activity 是处理 ACTION_DIAL 和 ACTION_CALL intent 的入口。
 * 当其他应用请求拨号时，系统会启动这个 Activity。
 * 
 * 处理逻辑：
 * 1. 提取电话号码
 * 2. 使用 Intent.ACTION_CALL 执行拨号
 * 3. 拨号完成后关闭自己
 */
class DialerActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "DialerActivity"
    }
    
    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performCall()
        } else {
            Toast.makeText(this, "需要电话权限才能拨号", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private var phoneNumberToCall: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 提取电话号码
        phoneNumberToCall = extractPhoneNumber()
        
        setContent {
            CallCenterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "正在拨号...")
                        }
                    }
                }
            }
        }
        
        // 处理拨号
        handleDialRequest()
    }
    
    private fun extractPhoneNumber(): String? {
        val data = intent?.data
        return when {
            data != null -> {
                // tel:xxx 格式
                data.schemeSpecificPart?.trim()
            }
            intent?.hasExtra(Intent.EXTRA_PHONE_NUMBER) == true -> {
                intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            }
            else -> null
        }
    }
    
    private fun handleDialRequest() {
        val phoneNumber = phoneNumberToCall
        
        if (phoneNumber.isNullOrEmpty()) {
            Toast.makeText(this, "无效的电话号码", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 检查拨号权限
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            performCall()
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
    
    private fun performCall() {
        val phoneNumber = phoneNumberToCall ?: run {
            finish()
            return
        }
        
        try {
            val uri = Uri.parse("tel:$phoneNumber")
            
            // 使用 Intent.ACTION_CALL 拨号
            // 这会使用系统默认的电话应用来执行拨号
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
            Log.d(TAG, "拨号请求已发送: $phoneNumber")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少拨号权限", e)
            Toast.makeText(this, "缺少拨号权限", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "拨号失败", e)
            Toast.makeText(this, "拨号失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        
        // 关闭自己
        finish()
    }
}
