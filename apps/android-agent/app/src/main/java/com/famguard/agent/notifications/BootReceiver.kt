package com.famguard.agent.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.agent.service.FamGuardProtectionService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val keystore = DeviceKeystoreManager(context)
        if (keystore.isPaired()) {
            FamGuardProtectionService.startService(context)
        }
    }
}
