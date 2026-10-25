package com.famguard.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.famguard.controller.auth.ControllerApiClient
import com.famguard.controller.ui.ControllerMainScreen

import com.famguard.controller.config.ControllerConfigStore

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: ControllerApiClient
    private lateinit var configStore: ControllerConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = ControllerConfigStore(this)
        apiClient = ControllerApiClient(
            supabaseUrl = configStore.getServerUrl(),
            anonKey = configStore.getAnonKey()
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerMainScreen(
                        apiClient = apiClient,
                        configStore = configStore
                    )
                }
            }
        }
    }
}
