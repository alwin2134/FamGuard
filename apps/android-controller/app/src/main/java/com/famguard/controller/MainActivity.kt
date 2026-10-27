package com.famguard.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.famguard.controller.auth.AuthUser
import com.famguard.controller.auth.ControllerApiClient
import com.famguard.controller.ui.ControllerMainScreen

import com.famguard.controller.config.ControllerConfigStore

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: ControllerApiClient
    private lateinit var configStore: ControllerConfigStore

    private val openTabState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val openDeviceIdState = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTabState.value = intent?.getStringExtra("extra_tab")
        openDeviceIdState.value = intent?.getStringExtra("extra_device_id")

        configStore = ControllerConfigStore(this)
        apiClient = ControllerApiClient(
            supabaseUrl = configStore.getServerUrl(),
            anonKey = configStore.getAnonKey()
        )

        // Automatically persist session changes to EncryptedSharedPreferences
        apiClient.onSessionPersist = { user ->
            configStore.saveSession(
                userId = user.id,
                email = user.email,
                accessToken = user.accessToken,
                refreshToken = user.refreshToken
            )
        }

        // Restore saved session immediately so app opens directly to dashboard
        configStore.getSession()?.let { saved ->
            apiClient.currentUser = AuthUser(
                id = saved.userId,
                email = saved.email,
                accessToken = saved.accessToken,
                refreshToken = saved.refreshToken
            )
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerMainScreen(
                        apiClient = apiClient,
                        configStore = configStore,
                        targetTab = openTabState.value,
                        targetDeviceId = openDeviceIdState.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("extra_tab")?.let { openTabState.value = it }
        intent.getStringExtra("extra_device_id")?.let { openDeviceIdState.value = it }
    }
}
