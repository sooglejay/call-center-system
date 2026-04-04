package com.callcenter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CallCenterApp : Application() {

    companion object {
        const val CHANNEL_ID_CALL_MONITOR = "call_monitor"
        const val CHANNEL_ID_AUTO_DIAL = "auto_dial"

        var instance: CallCenterApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callMonitorChannel = NotificationChannel(
                CHANNEL_ID_CALL_MONITOR,
                getString(R.string.notification_channel_call),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_call_desc)
            }

            val autoDialChannel = NotificationChannel(
                CHANNEL_ID_AUTO_DIAL,
                getString(R.string.notification_channel_call),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_call_desc)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(listOf(callMonitorChannel, autoDialChannel))
        }
    }
}
