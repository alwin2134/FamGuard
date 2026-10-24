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

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: ControllerApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = ControllerApiClient()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControllerMainScreen(apiClient = apiClient)
                }
            }
        }
    }
}
