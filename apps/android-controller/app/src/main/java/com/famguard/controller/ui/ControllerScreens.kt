package com.famguard.controller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famguard.contracts.*
import com.famguard.controller.auth.ControllerApiClient
import com.famguard.controller.devices.QrCodeGenerator
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.widget.Toast
import org.json.JSONObject
import com.famguard.controller.config.ControllerConfigStore
import com.famguard.controller.update.UpdateManager
import com.famguard.controller.update.UpdateInfo
import com.famguard.controller.BuildConfig

enum class ControllerScreenTab {
    DASHBOARD,
    MAP,
    APP_DRAWER,
    ACTIVITY,
    COMMANDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerMainScreen(
    apiClient: ControllerApiClient,
    configStore: ControllerConfigStore? = null
) {
    val context = LocalContext.current
    val currentConfigStore = configStore ?: remember { ControllerConfigStore(context) }
    val coroutineScope = rememberCoroutineScope()
    var isAuthenticated by remember { mutableStateOf(apiClient.currentUser != null) }

    // Settings dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Auth state
    var emailInput by remember { mutableStateOf("guardian@famguard.org") }
    var passwordInput by remember { mutableStateOf("FamGuard#Secure2026") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    // Live Devices list
    val devicesList = remember { mutableStateListOf<Device>() }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var isDevicesLoading by remember { mutableStateOf(false) }

    // Navigation tab
    var currentTab by remember { mutableStateOf(ControllerScreenTab.DASHBOARD) }

    // QR Pairing Dialog
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var targetDeviceNameInput by remember { mutableStateOf("Child Device") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayloadText by remember { mutableStateOf<String?>(null) }
    var isGeneratingQr by remember { mutableStateOf(false) }
    var qrError by remember { mutableStateOf<String?>(null) }

    // Map & Location state
    var activeLocationSession by remember { mutableStateOf<LocationSession?>(null) }
    var latestLocation by remember { mutableStateOf<LocationUpdate?>(null) }
    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

    // App activity state
    var activityJsonString by remember { mutableStateOf<String?>(null) }
    var isActivityLoading by remember { mutableStateOf(false) }

    // Commands history state
    val commandsList = remember { mutableStateListOf<DeviceCommand>() }

    fun refreshDevices() {
        coroutineScope.launch {
            isDevicesLoading = true
            val res = apiClient.fetchFamilyDevices()
            res.fold(
                onSuccess = { list ->
                    devicesList.clear()
                    devicesList.addAll(list)
                    if (selectedDevice == null || !list.any { it.id == selectedDevice?.id }) {
                        selectedDevice = list.firstOrNull()
                    }
                },
                onFailure = { /* fallback */ }
            )
            isDevicesLoading = false
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            refreshDevices()
        }
    }

    if (showSettingsDialog) {
        ControllerSettingsDialog(
            configStore = currentConfigStore,
            apiClient = apiClient,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (!isAuthenticated) {
        // Sign-in Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FamGuard Guardian Portal", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & Updates")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Guardian Cloud Sign In", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }

                        Text(
                            "Sign in to your authorized family guardian account on Supabase Cloud to manage devices.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Guardian Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (authError != null) {
                            Text(
                                text = authError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                isAuthLoading = true
                                authError = null
                                coroutineScope.launch {
                                    val res = apiClient.signIn(emailInput, passwordInput)
                                    res.fold(
                                        onSuccess = {
                                            isAuthenticated = true
                                        },
                                        onFailure = { err ->
                                            authError = "Sign in failed: ${err.message}"
                                        }
                                    )
                                    isAuthLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("Sign In to Family")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure Server & Updates")
                        }
                    }
                }
            }
        }
    } else {
        // Authenticated Controller Scaffold
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("FamGuard Controller", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = selectedDevice?.name ?: "No device selected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshDevices() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Devices")
                        }
                        IconButton(onClick = { showAddDeviceDialog = true }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Add Device")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings & Updates")
                        }
                        IconButton(onClick = {
                            apiClient.currentUser = null
                            isAuthenticated = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == ControllerScreenTab.DASHBOARD,
                        onClick = { currentTab = ControllerScreenTab.DASHBOARD },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("Devices") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ControllerScreenTab.MAP,
                        onClick = { currentTab = ControllerScreenTab.MAP },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("Live Map") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ControllerScreenTab.APP_DRAWER,
                        onClick = { currentTab = ControllerScreenTab.APP_DRAWER },
                        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                        label = { Text("App Drawer") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ControllerScreenTab.ACTIVITY,
                        onClick = { currentTab = ControllerScreenTab.ACTIVITY },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                        label = { Text("Activity") }
                    )
                    NavigationBarItem(
                        selected = currentTab == ControllerScreenTab.COMMANDS,
                        onClick = { currentTab = ControllerScreenTab.COMMANDS },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        label = { Text("Controls") }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (currentTab) {
                    ControllerScreenTab.DASHBOARD -> {
                        DevicesDashboardScreen(
                            devices = devicesList,
                            selectedDevice = selectedDevice,
                            isLoading = isDevicesLoading,
                            onSelectDevice = { selectedDevice = it },
                            onOpenAddDevice = { showAddDeviceDialog = true },
                            onRefresh = { refreshDevices() }
                        )
                    }
                    ControllerScreenTab.MAP -> {
                        GoogleMapsLocationScreen(
                            device = selectedDevice,
                            location = latestLocation,
                            session = activeLocationSession,
                            onFetchLatest = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = apiClient.fetchLatestLocation(devId)
                                    res.onSuccess { loc -> latestLocation = loc }
                                }
                            },
                            onStartSession = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = apiClient.startLocationSession(devId, 0) // 0 = Indefinite (WhatsApp style)
                                    res.fold(
                                        onSuccess = { s ->
                                            activeLocationSession = s
                                            locationStatusMessage = "Live location streaming active (Always active until stopped)"
                                        },
                                        onFailure = { err ->
                                            locationStatusMessage = "Failed: ${err.message}"
                                        }
                                    )
                                }
                            },
                            onStopSession = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id
                                    val sId = activeLocationSession?.id
                                    if (sId != null) {
                                        apiClient.stopLocationSession(sId)
                                    }
                                    if (devId != null) {
                                        apiClient.dispatchCommand(devId, CommandAction.STOP_LOCATION)
                                    }
                                    activeLocationSession = null
                                    locationStatusMessage = "Live location stopped"
                                }
                            },
                            statusMessage = locationStatusMessage
                        )
                    }
                    ControllerScreenTab.APP_DRAWER -> {
                        AppDrawerScreen(
                            device = selectedDevice,
                            activityJson = activityJsonString,
                            isLoading = isActivityLoading,
                            onRefresh = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    isActivityLoading = true
                                    val res = apiClient.getAppActivity(devId)
                                    res.fold(
                                        onSuccess = { activityJsonString = it },
                                        onFailure = { activityJsonString = null }
                                    )
                                    isActivityLoading = false
                                }
                            },
                            onLockApp = { pkg ->
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    apiClient.dispatchCommand(devId, CommandAction.LOCK_APP, mapOf("package_name" to pkg))
                                }
                            },
                            onUnlockApp = { pkg ->
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    apiClient.dispatchCommand(devId, CommandAction.UNLOCK_APP, mapOf("package_name" to pkg))
                                }
                            },
                            onUnlockAllApps = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    apiClient.dispatchCommand(devId, CommandAction.UNLOCK_APP, mapOf("package_name" to "all"))
                                }
                            }
                        )
                    }
                    ControllerScreenTab.ACTIVITY -> {
                        AppActivityDashboardScreen(
                            device = selectedDevice,
                            activityJson = activityJsonString,
                            isLoading = isActivityLoading,
                            onRefresh = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    isActivityLoading = true
                                    val res = apiClient.getAppActivity(devId)
                                    res.fold(
                                        onSuccess = { activityJsonString = it },
                                        onFailure = { activityJsonString = null }
                                    )
                                    isActivityLoading = false
                                }
                            },
                            onTriggerSync = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    apiClient.dispatchCommand(devId, CommandAction.SYNC_ACTIVITY)
                                }
                            }
                        )
                    }
                    ControllerScreenTab.COMMANDS -> {
                        DeviceCommandsScreen(
                            device = selectedDevice,
                            commands = commandsList,
                            onRefreshCommands = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = apiClient.fetchCommands(devId)
                                    res.onSuccess { cmds ->
                                        commandsList.clear()
                                        commandsList.addAll(cmds)
                                    }
                                }
                            },
                            onSendCommand = { action, payloadMap ->
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = if (action == CommandAction.REFRESH_LOCATION) {
                                        apiClient.startLocationSession(devId, 5).map { session ->
                                            activeLocationSession = session
                                            DeviceCommand(
                                                id = "sess-${session.id.take(8)}",
                                                deviceId = devId,
                                                requestedBy = session.requestedBy,
                                                action = CommandAction.REFRESH_LOCATION,
                                                status = CommandStatus.PENDING,
                                                createdAt = session.createdAt,
                                                expiresAt = session.expiresAt
                                            )
                                        }
                                    } else {
                                        apiClient.dispatchCommand(devId, action, payloadMap)
                                    }
                                    res.fold(
                                        onSuccess = { cmd ->
                                            commandsList.add(0, cmd)
                                        },
                                        onFailure = { err ->
                                            commandsList.add(
                                                0,
                                                DeviceCommand(
                                                    id = "cmd-err-${System.currentTimeMillis()}",
                                                    deviceId = devId,
                                                    requestedBy = "guardian",
                                                    action = action,
                                                    status = CommandStatus.FAILED,
                                                    createdAt = "now",
                                                    expiresAt = "now",
                                                    failureReason = err.message
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Device QR Dialog
    if (showAddDeviceDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDeviceDialog = false
                qrBitmap = null
                qrPayloadText = null
            },
            title = { Text("Pair New Family Device") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (qrBitmap == null) {
                        Text(
                            "Enter a label for the target phone, then generate a single-use pairing token from Supabase Cloud:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = targetDeviceNameInput,
                            onValueChange = { targetDeviceNameInput = it },
                            label = { Text("Device Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (qrError != null) {
                            Text(qrError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        Button(
                            enabled = !isGeneratingQr && targetDeviceNameInput.isNotBlank(),
                            onClick = {
                                isGeneratingQr = true
                                qrError = null
                                coroutineScope.launch {
                                    val res = apiClient.createPairing("11111111-1111-1111-1111-111111111111", targetDeviceNameInput)
                                    res.fold(
                                        onSuccess = { pairing ->
                                            qrPayloadText = pairing.qrPayload
                                            qrBitmap = QrCodeGenerator.generateQrBitmap(pairing.qrPayload)
                                        },
                                        onFailure = { err ->
                                            qrError = "Failed to generate: ${err.message}"
                                        }
                                    )
                                    isGeneratingQr = false
                                }
                            }
                        ) {
                            if (isGeneratingQr) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("Generate Single-Use Cloud Token")
                            }
                        }
                    } else {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Text(
                            "Token valid for 15 minutes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )

                        if (qrPayloadText != null) {
                            OutlinedTextField(
                                value = qrPayloadText ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Cloud QR Payload (JSON)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDeviceDialog = false
                    qrBitmap = null
                    qrPayloadText = null
                    refreshDevices()
                }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun DevicesDashboardScreen(
    devices: List<Device>,
    selectedDevice: Device?,
    isLoading: Boolean,
    onSelectDevice: (Device) -> Unit,
    onOpenAddDevice: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Linked Family Devices (${devices.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                FilledTonalButton(onClick = onOpenAddDevice) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pair Device")
                }
            }
        }

        if (devices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No Devices Linked Yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap 'Pair Device' above to generate a QR code and pair your family's phones.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(devices) { dev ->
                    val isSelected = dev.id == selectedDevice?.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(dev) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        "${dev.model ?: "Android"} • ${dev.osVersion ?: ""}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (dev.isOnline) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (dev.isOnline) "ONLINE" else "OFFLINE",
                                        color = if (dev.isOnline) Color(0xFF2E7D32) else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Device ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("${dev.id.take(8)}...", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                Column {
                                    Text("Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(dev.status.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                Column {
                                    Text("Enrolled", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(dev.enrolledAt.take(10), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class AppDrawerItem(
    val packageName: String,
    val appLabel: String,
    val isSystem: Boolean,
    val isLocked: Boolean
)

@Composable
fun AppDrawerScreen(
    device: Device?,
    activityJson: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLockApp: (String) -> Unit,
    onUnlockApp: (String) -> Unit,
    onUnlockAllApps: () -> Unit
) {
    // Auto-refresh every 8s while drawer is open
    LaunchedEffect(device?.id) {
        while (isActive) {
            onRefresh()
            delay(8000)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, LOCKED, UNLOCKED
    val installedApps = remember { mutableStateListOf<AppDrawerItem>() }
    val localLockedPackages = remember { mutableStateListOf<String>() }

    LaunchedEffect(activityJson) {
        if (!activityJson.isNullOrBlank()) {
            try {
                val root = JSONObject(activityJson)
                val lockedArray = root.optJSONArray("locked_apps")
                val currentLocked = mutableSetOf<String>()
                if (lockedArray != null) {
                    for (i in 0 until lockedArray.length()) {
                        currentLocked.add(lockedArray.getString(i))
                    }
                }
                localLockedPackages.clear()
                localLockedPackages.addAll(currentLocked)

                val appsArray = root.optJSONArray("installed_apps")
                installedApps.clear()
                if (appsArray != null) {
                    for (i in 0 until appsArray.length()) {
                        val obj = appsArray.getJSONObject(i)
                        val pkg = obj.optString("package_name")
                        val label = obj.optString("app_label", pkg)
                        val isSys = obj.optBoolean("is_system", false)
                        installedApps.add(
                            AppDrawerItem(
                                packageName = pkg,
                                appLabel = label,
                                isSystem = isSys,
                                isLocked = currentLocked.contains(pkg)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    val filteredApps = installedApps.filter { app ->
        val isLocallyLocked = localLockedPackages.contains(app.packageName)
        val matchesSearch = searchQuery.isBlank() ||
                app.appLabel.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterMode) {
            "LOCKED" -> isLocallyLocked
            "UNLOCKED" -> !isLocallyLocked
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val lockedCount = localLockedPackages.size
    val totalCount = installedApps.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Phone App Drawer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (device != null) "${device.name} • $totalCount Installed Apps" else "Select a device",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Filter Chips & Emergency Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("All ($totalCount)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filterMode == "LOCKED",
                    onClick = { filterMode = "LOCKED" },
                    label = { Text("Locked ($lockedCount)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
                FilterChip(
                    selected = filterMode == "UNLOCKED",
                    onClick = { filterMode = "UNLOCKED" },
                    label = { Text("Unlocked (${(totalCount - lockedCount).coerceAtLeast(0)})", fontSize = 11.sp) }
                )
            }

            if (lockedCount > 0) {
                FilledTonalButton(
                    onClick = {
                        localLockedPackages.clear()
                        onUnlockAllApps()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFD97706)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unlock All", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (installedApps.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Syncing installed apps from target device...", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Text("No apps match '$searchQuery'", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isLocked = localLockedPackages.contains(app.packageName)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLocked) 3.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Monogram Icon
                            val avatarColor = remember(app.packageName) {
                                val colors = listOf(
                                    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
                                    Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00ACC1)
                                )
                                colors[Math.abs(app.packageName.hashCode()) % colors.size]
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.appLabel.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // App Info
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.appLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    if (app.isSystem) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SYSTEM",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Lock / Unlock Action Button
                            if (isLocked) {
                                Button(
                                    onClick = {
                                        localLockedPackages.remove(app.packageName)
                                        onUnlockApp(app.packageName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Emerald
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unlock", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (!localLockedPackages.contains(app.packageName)) {
                                            localLockedPackages.add(app.packageName)
                                        }
                                        onLockApp(app.packageName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lock", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleMapsLocationScreen(
    device: Device?,
    location: LocationUpdate?,
    session: LocationSession?,
    onFetchLatest: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    statusMessage: String?
) {
    val context = LocalContext.current

    LaunchedEffect(device?.id) {
        onFetchLatest()
    }

    val isSessionActive = session?.status == LocationSessionStatus.ACTIVE || session?.status == LocationSessionStatus.AUTHORIZED

    // Live Polling while active or standby (2s refresh when live, WhatsApp style)
    LaunchedEffect(isSessionActive, device?.id) {
        while (isActive) {
            onFetchLatest()
            delay(if (isSessionActive) 2000 else 8000)
        }
    }

    val lat = location?.latitude ?: 37.7749
    val lon = location?.longitude ?: -122.4194
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lon), 15f)
    }

    LaunchedEffect(location?.latitude, location?.longitude) {
        if (location != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(location.latitude, location.longitude), 16f)
        }
    }

    var useOsmMap by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isSessionActive) Color(0xFF10B981) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSessionActive) "LIVE LOCATION STREAMING (Always Active)" else "STANDBY (Tap 'Start Live' to stream)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSessionActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (location != null) "Fix: Lat ${String.format("%.4f", location.latitude)}, Lon ${String.format("%.4f", location.longitude)} (±${location.accuracyM.toInt()}m)"
                            else "No GPS fixes received yet for this device",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row {
                        IconButton(onClick = onFetchLatest) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Location")
                        }
                        if (!isSessionActive) {
                            Button(onClick = onStartSession) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Live")
                            }
                        } else {
                            Button(
                                onClick = onStopSession,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(statusMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Live Telemetry Bar with One-Click Open in Google Maps
        if (location != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${String.format("%.5f", location.latitude)}, ${String.format("%.5f", location.longitude)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Accuracy ±${location.accuracyM.toInt()}m • ${location.recordedAt.takeLast(9)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${device?.name ?: "Family Device"})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(mapIntent)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Map App", fontSize = 12.sp)
                    }
                }
            }
        }

        // Map Provider Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Map Engine:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = useOsmMap,
                    onClick = { useOsmMap = true },
                    label = { Text("OpenStreetMap (Street Tiles)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = !useOsmMap,
                    onClick = { useOsmMap = false },
                    label = { Text("Google Maps", fontSize = 11.sp) }
                )
            }
        }

        // Embedded Map
        Box(modifier = Modifier.weight(1f)) {
            if (useOsmMap) {
                LeafletOsmMapView(
                    latitude = lat,
                    longitude = lon,
                    accuracyM = location?.accuracyM ?: 25f,
                    deviceName = device?.name ?: "Target Device",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    if (location != null) {
                        val targetLatLng = LatLng(location.latitude, location.longitude)
                        Marker(
                            state = MarkerState(position = targetLatLng),
                            title = device?.name ?: "Target Device",
                            snippet = "Accuracy ±${location.accuracyM}m"
                        )
                        Circle(
                            center = targetLatLng,
                            radius = location.accuracyM.toDouble(),
                            fillColor = Color(0x332196F3),
                            strokeColor = Color(0xFF2196F3),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeafletOsmMapView(
    latitude: Double,
    longitude: Double,
    accuracyM: Float,
    deviceName: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val delta = 0.007
    val mapUrl = "https://www.openstreetmap.org/export/embed.html?bbox=${longitude - delta},${latitude - delta},${longitude + delta},${latitude + delta}&layer=mapnik&marker=$latitude,$longitude"

    var lastLoadedLat by remember { mutableDoubleStateOf(0.0) }
    var lastLoadedLon by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(latitude, longitude) {
        // Reload when location shifts by more than 10 meters
        val latDiff = Math.abs(latitude - lastLoadedLat)
        val lonDiff = Math.abs(longitude - lastLoadedLon)
        if (latDiff > 0.0001 || lonDiff > 0.0001) {
            lastLoadedLat = latitude
            lastLoadedLon = longitude
            webViewRef?.loadUrl(mapUrl)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 FamGuard/1.0"
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("LeafletMap", "Console: \${consoleMessage?.message()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        android.util.Log.d("LeafletMap", "Page finished loading: \$url")
                    }
                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        android.util.Log.e("LeafletMap", "WebResourceError: \${error?.description} code: \${error?.errorCode} url: \${request?.url}")
                    }
                    override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        android.util.Log.e("LeafletMap", "HttpError: \${errorResponse?.statusCode} url: \${request?.url}")
                    }
                }
                loadUrl(mapUrl)
                webViewRef = this
            }
        },
        modifier = modifier
    )
}

@Composable
fun AppActivityDashboardScreen(
    device: Device?,
    activityJson: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onTriggerSync: (() -> Unit)? = null
) {
    // Auto-refresh activity every 10 seconds while the tab is active
    LaunchedEffect(device?.id) {
        while (isActive) {
            onRefresh()
            delay(10_000)
        }
    }

    // Parse JSON
    var latestAppLabel by remember { mutableStateOf<String?>(null) }
    var latestEventType by remember { mutableStateOf<String?>(null) }
    var latestOccurredAt by remember { mutableStateOf<String?>(null) }
    val parsedSummaries = remember { mutableStateListOf<Pair<String, Int>>() }
    val parsedTimeline = remember { mutableStateListOf<Triple<String, String, String>>() }

    LaunchedEffect(activityJson) {
        parsedSummaries.clear()
        parsedTimeline.clear()
        if (!activityJson.isNullOrBlank()) {
            try {
                val root = JSONObject(activityJson)
                if (root.has("latest_detected_app") && !root.isNull("latest_detected_app")) {
                    val lat = root.getJSONObject("latest_detected_app")
                    latestAppLabel = lat.optString("app_label", lat.optString("package_name"))
                    latestEventType = lat.optString("event_type")
                    latestOccurredAt = lat.optString("occurred_at")
                } else {
                    latestAppLabel = null
                }

                if (root.has("daily_summaries")) {
                    val arr = root.getJSONArray("daily_summaries")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("app_label", obj.optString("package_name"))
                        val mins = obj.optInt("total_minutes", 0)
                        parsedSummaries.add(Pair(name, mins))
                    }
                }

                if (root.has("timeline")) {
                    val arr = root.getJSONArray("timeline")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("app_label", obj.optString("package_name"))
                        val type = obj.optString("event_type").uppercase()
                        val time = obj.optString("occurred_at").takeLast(9)
                        parsedTimeline.add(Triple(name, type, time))
                    }
                }
            } catch (e: Exception) {
                // Parse error
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App Usage Activity", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onTriggerSync != null) {
                    FilledTonalButton(
                        onClick = onTriggerSync,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live Sync", fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // Latest Detected App Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Latest Detected App (Estimate)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = latestAppLabel ?: "No foreground app recorded yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (latestEventType != null) "$latestEventType event detected • $latestOccurredAt"
                        else "App events sync automatically every ~15 minutes",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (parsedSummaries.isNotEmpty()) {
            Text("Today's Aggregated Usage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            for ((appName, minutes) in parsedSummaries) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${minutes}m", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (minutes / 60f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text("Chronological Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        if (parsedTimeline.isEmpty()) {
            Text(
                "No timeline events recorded yet. Open apps on the target device and tap 'Sync Now' to upload.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parsedTimeline) { (pkg, evType, time) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (evType == "FOREGROUND") Color(0xFF2E7D32).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = evType,
                                    color = if (evType == "FOREGROUND") Color(0xFF2E7D32) else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(pkg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCommandsScreen(
    device: Device?,
    commands: List<DeviceCommand>,
    onRefreshCommands: () -> Unit,
    onSendCommand: (CommandAction, Map<String, String>) -> Unit
) {
    var targetPackage by remember { mutableStateOf("com.google.android.youtube") }

    LaunchedEffect(device?.id) {
        while (isActive) {
            onRefreshCommands()
            delay(2500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remote Device Controls", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = onRefreshCommands) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Commands")
            }
        }

        // Quick Alert Actions (Ring & Location Fix)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSendCommand(CommandAction.RING, emptyMap()) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ring Phone")
            }

            Button(
                onClick = { onSendCommand(CommandAction.REFRESH_LOCATION, emptyMap()) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Fix")
            }
        }

        // App Lock & Overlay Controls Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Lock & Restrictions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "YouTube" to "com.google.android.youtube",
                        "Chrome" to "com.android.chrome",
                        "Camera" to "com.google.android.GoogleCamera"
                    )
                    for ((label, pkg) in presets) {
                        FilterChip(
                            selected = targetPackage == pkg,
                            onClick = { targetPackage = pkg },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = { targetPackage = it },
                    label = { Text("App Package Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (targetPackage.isNotBlank()) {
                                onSendCommand(CommandAction.LOCK_APP, mapOf("package_name" to targetPackage.trim()))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock App")
                    }

                    Button(
                        onClick = {
                            if (targetPackage.isNotBlank()) {
                                onSendCommand(CommandAction.UNLOCK_APP, mapOf("package_name" to targetPackage.trim()))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlock App")
                    }
                }

                // Emergency Unlock All Apps Button (Testing Safety)
                Button(
                    onClick = {
                        onSendCommand(CommandAction.UNLOCK_APP, mapOf("package_name" to "all"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)), // Amber-600
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EMERGENCY UNLOCK ALL APPS (TESTING)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Text("Command Execution History", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        if (commands.isEmpty()) {
            Text(
                "No commands dispatched yet. Use the buttons above to test live controls.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(commands) { cmd ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cmd.action.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val statusColor = when (cmd.status) {
                                    CommandStatus.SUCCEEDED -> Color(0xFF2E7D32)
                                    CommandStatus.EXECUTING -> Color(0xFF1976D2)
                                    CommandStatus.PENDING -> Color(0xFFF57C00)
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = cmd.status.name,
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (cmd.failureReason != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Result: ${cmd.failureReason}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControllerSettingsDialog(
    configStore: ControllerConfigStore,
    apiClient: ControllerApiClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var serverUrlInput by remember { mutableStateOf(configStore.getServerUrl()) }
    var anonKeyInput by remember { mutableStateOf(configStore.getAnonKey()) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var isTestSuccess by remember { mutableStateOf(false) }

    // Update state
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Server & Update Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Configure Supabase cloud backend credentials securely. These are stored only in hardware-encrypted storage on this device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Supabase / Server URL") },
                        placeholder = { Text("https://your-project.supabase.co") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = anonKeyInput,
                        onValueChange = { anonKeyInput = it },
                        label = { Text("Anon / Public API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Hide Key" else "Show Key"
                                )
                            }
                        },
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTestingConnection = true
                                testStatusMessage = null
                                coroutineScope.launch {
                                    val res = configStore.testConnection(serverUrlInput, anonKeyInput)
                                    res.fold(
                                        onSuccess = { msg ->
                                            testStatusMessage = msg
                                            isTestSuccess = true
                                        },
                                        onFailure = { err ->
                                            testStatusMessage = err.message ?: "Connection failed"
                                            isTestSuccess = false
                                        }
                                    )
                                    isTestingConnection = false
                                }
                            },
                            enabled = !isTestingConnection
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...")
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection")
                            }
                        }

                        Button(
                            onClick = {
                                configStore.saveConfig(serverUrlInput, anonKeyInput)
                                apiClient.updateCredentials(serverUrlInput, anonKeyInput)
                                Toast.makeText(context, "Server configuration saved successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        ) {
                            Text("Save & Apply")
                        }
                    }
                }

                if (testStatusMessage != null) {
                    item {
                        Surface(
                            color = if (isTestSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = testStatusMessage!!,
                                color = if (isTestSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                // In-App Updates Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("App Version", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Installed: v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        isCheckingUpdates = true
                                        updateStatusMessage = null
                                        coroutineScope.launch {
                                            val res = UpdateManager.checkForUpdates()
                                            res.fold(
                                                onSuccess = { info ->
                                                    updateInfo = info
                                                    if (info == null) {
                                                        updateStatusMessage = "You are on the latest version."
                                                    } else if (!info.isNewer) {
                                                        updateStatusMessage = "App is up to date (v${BuildConfig.VERSION_NAME})."
                                                    }
                                                },
                                                onFailure = { err ->
                                                    updateStatusMessage = "Update check failed: ${err.message}"
                                                }
                                            )
                                            isCheckingUpdates = false
                                        }
                                    },
                                    enabled = !isCheckingUpdates && !isDownloading
                                ) {
                                    if (isCheckingUpdates) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Check Update")
                                    }
                                }
                            }

                            if (updateStatusMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(updateStatusMessage!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (updateInfo != null && updateInfo!!.isNewer) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            "Update Available: ${updateInfo!!.tagName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            updateInfo!!.changelog,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            maxLines = 3
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                isDownloading = true
                                                coroutineScope.launch {
                                                    val res = UpdateManager.downloadAndInstallApk(
                                                        context = context,
                                                        downloadUrl = updateInfo!!.downloadUrl,
                                                        fileName = updateInfo!!.fileName,
                                                        onProgress = { p -> downloadProgress = p }
                                                    )
                                                    isDownloading = false
                                                    res.onFailure { err ->
                                                        updateStatusMessage = "Install failed: ${err.message}"
                                                    }
                                                }
                                            },
                                            enabled = !isDownloading,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isDownloading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Downloading ${(downloadProgress?.times(100))?.toInt() ?: 0}%")
                                            } else {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Download & Install")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
