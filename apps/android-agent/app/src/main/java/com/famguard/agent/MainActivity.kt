package com.famguard.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.famguard.agent.activity.UsageAccessManager
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.agent.ui.AgentMainScreen

class MainActivity : ComponentActivity() {

    private lateinit var keystoreManager: DeviceKeystoreManager
    private lateinit var usageAccessManager: UsageAccessManager
    private lateinit var apiClient: AgentApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keystoreManager = DeviceKeystoreManager(this)
        usageAccessManager = UsageAccessManager(this)
        apiClient = AgentApiClient(
            context = this,
            baseUrl = "${keystoreManager.getServerUrl().trimEnd('/')}/functions/v1",
            anonKey = keystoreManager.getAnonKey()
        )

        if (keystoreManager.isPaired()) {
            com.famguard.agent.service.FamGuardProtectionService.startService(this)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AgentMainScreen(
                        keystoreManager = keystoreManager,
                        usageAccessManager = usageAccessManager,
                        apiClient = apiClient
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::keystoreManager.isInitialized && keystoreManager.isPaired()) {
            com.famguard.agent.service.FamGuardProtectionService.startService(this)
        }
    }
}
