package com.callcenter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.callcenter.app.ui.screens.dialer.DialerScreen
import com.callcenter.app.ui.theme.CallCenterTheme

/**
 * 系统默认电话应用入口。
 *
 * 直接承载拨号盘 UI，而不是透明跳转到 MainActivity，
 * 让系统更容易把本应用识别为真正的拨号应用候选。
 */
class DialerEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_DIALER = "extra_open_dialer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CallCenterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DialerScreen(
                        initialPhoneNumber = extractDialNumber(intent),
                        onNavigateBack = { finish() },
                        onNavigateToContacts = {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra(EXTRA_OPEN_DIALER, true)
                            })
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun extractDialNumber(intent: Intent?): String {
        if (intent == null) return ""
        return intent.data?.schemeSpecificPart?.trim().orEmpty()
    }
}
