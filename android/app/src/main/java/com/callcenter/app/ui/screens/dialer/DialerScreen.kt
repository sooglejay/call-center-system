package com.callcenter.app.ui.screens.dialer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.callcenter.app.util.CallHelper

/**
 * 手动拨号页面 - 类似系统拨号盘的界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val context = LocalContext.current
    val callHelper = remember { CallHelper(context) }

    var phoneNumber by remember { mutableStateOf("") }
    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCallPermission = isGranted
        if (isGranted && phoneNumber.isNotEmpty()) {
            callHelper.makeCall(phoneNumber, directCall = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拨号") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToContacts) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "通讯录"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 电话号码显示区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (phoneNumber.isEmpty()) "请输入号码" else phoneNumber,
                        fontSize = if (phoneNumber.isEmpty()) 28.sp else 40.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (phoneNumber.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            // 删除按钮
            if (phoneNumber.isNotEmpty()) {
                IconButton(
                    onClick = { phoneNumber = phoneNumber.dropLast(1) },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            // 拨号键盘
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 数字键 1-9
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..2) {
                            val number = row * 3 + col + 1
                            DialerButton(
                                number = number.toString(),
                                letters = getLettersForNumber(number),
                                onClick = { phoneNumber += number.toString() }
                            )
                        }
                    }
                }

                // 最后一行: * 0 #
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DialerButton(
                        number = "*",
                        letters = "",
                        onClick = { phoneNumber += "*" }
                    )
                    DialerButton(
                        number = "0",
                        letters = "+",
                        onClick = { phoneNumber += "0" }
                    )
                    DialerButton(
                        number = "#",
                        letters = "",
                        onClick = { phoneNumber += "#" }
                    )
                }
            }

            // 拨打按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CallButton(
                    enabled = phoneNumber.isNotEmpty() && callHelper.isValidPhoneNumber(phoneNumber),
                    onClick = {
                        if (hasCallPermission) {
                            callHelper.makeCall(phoneNumber, directCall = true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 拨号按钮
 */
@Composable
private fun DialerButton(
    number: String,
    letters: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 拨打按钮
 */
@Composable
private fun CallButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Color(0xFF4CAF50).copy(alpha = 0.15f)
                else Color(0xFF4CAF50).copy(alpha = 0.05f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "拨打",
            tint = if (enabled) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.3f),
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 获取数字对应的字母（传统拨号盘布局）
 */
private fun getLettersForNumber(number: Int): String {
    return when (number) {
        2 -> "ABC"
        3 -> "DEF"
        4 -> "GHI"
        5 -> "JKL"
        6 -> "MNO"
        7 -> "PQRS"
        8 -> "TUV"
        9 -> "WXYZ"
        else -> ""
    }
}
