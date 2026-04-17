package com.callcenter.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.*
import android.util.Log

/**
 * 电话连接服务
 * 
 * 作为默认拨号应用时，这个服务负责：
 * 1. 处理拨出电话请求
 * 2. 将通话委托给系统的 SIM 卡执行
 * 3. InCallService 负责监控通话状态和自动扩音
 * 
 * 设计原理：
 * - 我们注册一个 PhoneAccount 并设置 CAPABILITY_CONNECTION_MANAGER 能力
 * - 当收到拨号请求时，我们创建一个 Connection 并立即激活
 * - 使用 TelecomManager.placeCall() 并指定 SIM 卡 PhoneAccount 来执行实际拨号
 */
class PhoneConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "PhoneConnectionService"
        const val PHONE_ACCOUNT_ID = "callcenter_connection_manager"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection? {
        Log.d(TAG, "========== onCreateOutgoingConnection ==========")
        Log.d(TAG, "拨号请求: ${request.address}")
        Log.d(TAG, "connectionManagerPhoneAccount: $connectionManagerPhoneAccount")
        Log.d(TAG, "accountHandle: ${request.accountHandle}")

        val phoneNumber = request.address?.schemeSpecificPart
        if (phoneNumber.isNullOrEmpty()) {
            Log.e(TAG, "无效的电话号码")
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.ERROR, "无效的电话号码")
            )
        }

        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            
            // 获取 SIM 卡 PhoneAccount
            val simPhoneAccounts = telecomManager.callCapablePhoneAccounts
            Log.d(TAG, "可用的 SIM PhoneAccount 数量: ${simPhoneAccounts?.size ?: 0}")
            
            val simAccount = simPhoneAccounts?.firstOrNull()
            if (simAccount == null) {
                Log.e(TAG, "没有可用的 SIM 卡")
                return Connection.createFailedConnection(
                    DisconnectCause(DisconnectCause.ERROR, "没有可用的 SIM 卡")
                )
            }

            Log.d(TAG, "使用 SIM PhoneAccount: $simAccount")

            // 创建一个连接
            val connection = DialerConnection()
            connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
            connection.setAudioModeIsVoip(false)
            connection.connectionCapabilities = (
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_MUTE
            )
            
            // 设置为拨号中
            connection.setDialing()
            
            // 使用 SIM 卡执行实际拨号
            // 通过 Bundle 指定 SIM 卡 PhoneAccount
            val extras = Bundle()
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simAccount)
            
            // 使用 placeCall 触发 SIM 卡拨号
            // 注意：这里我们使用 SIM 卡的 PhoneAccount，所以不会循环调用我们自己
            telecomManager.placeCall(request.address, extras)
            
            Log.d(TAG, "Connection 创建成功")
            return connection

        } catch (e: Exception) {
            Log.e(TAG, "创建 Connection 失败: ${e.message}", e)
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.ERROR, e.message)
            )
        }
    }

    /**
     * 拨号连接
     */
    private inner class DialerConnection : Connection() {
        init {
            Log.d(TAG, "DialerConnection created")
        }

        override fun onStateChanged(state: Int) {
            super.onStateChanged(state)
            Log.d(TAG, "DialerConnection state: $state")
        }

        override fun onDisconnect() {
            Log.d(TAG, "DialerConnection onDisconnect")
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onAbort() {
            Log.d(TAG, "DialerConnection onAbort")
            setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
            destroy()
        }
    }
}
