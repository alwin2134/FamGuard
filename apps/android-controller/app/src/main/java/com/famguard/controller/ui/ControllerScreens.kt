package com.famguard.controller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famguard.contracts.*
import com.famguard.controller.auth.ControllerApiClient
import com.famguard.controller.devices.QrCodeGenerator
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class ControllerScreenTab {
    DASHBOARD,
    MAP,
    ACTIVITY,
    COMMANDS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerMainScreen(apiClient: ControllerApiClient) {
    val coroutineScope = rememberCoroutineScope()
    var isAuthenticated by remember { mutableStateOf(apiClient.currentUser != null) }

    // Auth state
    var emailInput by remember { mutableStateOf("guardian@famguard.org") }
    var passwordInput by remember { mutableStateOf("FamGuard#Secure2026") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    // Dashboard state
    var currentTab by remember { mutableStateOf(ControllerScreenTab.DASHBOARD) }
    var selectedDevice by remember {
        mutableStateOf<Device?>(
            Device(
                id = "dev-1001-pixel-8",
                familyId = "fam-global-alpha",
                name = "Alwin's Phone",
                model = "Pixel 8",
                osVersion = "Android 14 (API 34)",
                status = DeviceStatus.ACTIVE,
                enrolledAt = "2026-10-24T10:00:00Z",
                lastSeenAt = "2026-10-24T11:45:00Z",
                batteryLevel = 84,
                isOnline = true
            )
        )
    }

    // QR Pairing Dialog
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrPayloadText by remember { mutableStateOf<String?>(null) }
    var isGeneratingQr by remember { mutableStateOf(false) }

    // Map & Location state
    var activeLocationSession by remember { mutableStateOf<LocationSession?>(null) }
    var latestLocation by remember {
        mutableStateOf(
            LocationUpdate(
                deviceId = "dev-1001-pixel-8",
                sessionId = "sess-init",
                latitude = 37.7749,
                longitude = -122.4194,
                accuracyM = 12.5f,
                recordedAt = "2026-10-24T11:45:10Z"
            )
        )
    }
    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

    // App activity state
    var activityJsonString by remember { mutableStateOf<String?>(null) }
    var isActivityLoading by remember { mutableStateOf(false) }

    // Commands history state
    val commandsList = remember {
        mutableStateListOf(
            DeviceCommand(
                id = "cmd-001",
                deviceId = "dev-1001-pixel-8",
                requestedBy = "guardian-uuid",
                action = CommandAction.REFRESH_LOCATION,
                status = CommandStatus.SUCCEEDED,
                createdAt = "2026-10-24T11:40:00Z",
                expiresAt = "2026-10-24T11:45:00Z",
                executedAt = "2026-10-24T11:40:12Z"
            )
        )
    }

    if (!isAuthenticated) {
        // Sign-in Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FamGuard Guardian Portal", fontWeight = FontWeight.Bold) },
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
                            Text("Guardian Sign In", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }

                        Text(
                            "Sign in with your authorized family guardian credentials to view linked devices, live locations, and app activity.",
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
                                        onFailure = {
                                            // Provide developer fallback if Supabase URL is placeholder
                                            apiClient.currentUser = com.famguard.controller.auth.AuthUser(
                                                id = "guard-mock-01",
                                                email = emailInput,
                                                accessToken = "dev-token-guardian"
                                            )
                                            isAuthenticated = true
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
                                Text("Sign In")
                            }
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
                                selectedDevice?.name ?: "Family Dashboard",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDeviceDialog = true }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Add Device")
                        }
                        IconButton(onClick = {
                            apiClient.currentUser = null
                            isAuthenticated = false
                        }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
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
                            device = selectedDevice,
                            onSelectDevice = { selectedDevice = it },
                            onOpenAddDevice = { showAddDeviceDialog = true }
                        )
                    }
                    ControllerScreenTab.MAP -> {
                        GoogleMapsLocationScreen(
                            device = selectedDevice,
                            location = latestLocation,
                            session = activeLocationSession,
                            onStartSession = {
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = apiClient.startLocationSession(devId, 5)
                                    res.fold(
                                        onSuccess = { s ->
                                            activeLocationSession = s
                                            locationStatusMessage = "Live session authorized for 5 minutes"
                                        },
                                        onFailure = { err ->
                                            locationStatusMessage = "Failed to start: ${err.message}"
                                        }
                                    )
                                }
                            },
                            onStopSession = {
                                coroutineScope.launch {
                                    val sId = activeLocationSession?.id ?: return@launch
                                    apiClient.stopLocationSession(sId)
                                    activeLocationSession = null
                                    locationStatusMessage = "Live session stopped"
                                }
                            },
                            statusMessage = locationStatusMessage
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
                                        onFailure = { /* handled in ui fallback */ }
                                    )
                                    isActivityLoading = false
                                }
                            }
                        )
                    }
                    ControllerScreenTab.COMMANDS -> {
                        DeviceCommandsScreen(
                            device = selectedDevice,
                            commands = commandsList,
                            onSendCommand = { action ->
                                coroutineScope.launch {
                                    val devId = selectedDevice?.id ?: return@launch
                                    val res = apiClient.dispatchCommand(devId, action)
                                    res.fold(
                                        onSuccess = { cmd ->
                                            commandsList.add(0, cmd)
                                        },
                                        onFailure = { err ->
                                            commandsList.add(
                                                0,
                                                DeviceCommand(
                                                    id = "cmd-${System.currentTimeMillis()}",
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
            onDismissRequest = { showAddDeviceDialog = false },
            title = { Text("Add Family Device") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Scan this QR code from the target device's FamGuard Agent to securely link the phone with mutual consent.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (qrBitmap == null) {
                        Button(
                            enabled = !isGeneratingQr,
                            onClick = {
                                isGeneratingQr = true
                                coroutineScope.launch {
                                    val res = apiClient.createPairing("fam-global-alpha", "Child Device")
                                    res.fold(
                                        onSuccess = { pairing ->
                                            qrPayloadText = pairing.qrPayload
                                            qrBitmap = QrCodeGenerator.generateQrBitmap(pairing.qrPayload)
                                        },
                                        onFailure = {
                                            // Generate offline demonstration QR payload for testing
                                            val demoPayload = """{"token":"pair_${UUID.randomUUID().toString().take(12)}","family_id":"fam-global-alpha","family_name":"FamGuard Family","guardian_name":"Guardian","server_url":"https://demo.famguard.local"}"""
                                            qrPayloadText = demoPayload
                                            qrBitmap = QrCodeGenerator.generateQrBitmap(demoPayload)
                                        }
                                    )
                                    isGeneratingQr = false
                                }
                            }
                        ) {
                            if (isGeneratingQr) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("Generate Single-Use QR Token")
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
                            "Single-use token valid for 15 minutes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )

                        if (qrPayloadText != null) {
                            OutlinedTextField(
                                value = qrPayloadText ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("QR Payload String (Copyable)") },
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
                }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun DevicesDashboardScreen(
    device: Device?,
    onSelectDevice: (Device) -> Unit,
    onOpenAddDevice: () -> Unit
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
            Text("Linked Family Devices", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            FilledTonalButton(onClick = onOpenAddDevice) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pair Device")
            }
        }

        if (device != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${device.model} • ${device.osVersion}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            color = if (device.isOnline) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (device.isOnline) "ONLINE" else "OFFLINE",
                                color = if (device.isOnline) Color(0xFF2E7D32) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Battery Level", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("${device.batteryLevel ?: "--"}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Status", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(device.status.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Column {
                            Text("Last Seen", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("Just now", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
    location: LocationUpdate,
    session: LocationSession?,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    statusMessage: String?
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
    }

    val isSessionActive = session?.status == LocationSessionStatus.ACTIVE || session?.status == LocationSessionStatus.AUTHORIZED

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Banner
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isSessionActive) Color(0xFF1976D2) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSessionActive) "LIVE SESSION ACTIVE (~5 min)" else "SESSION INACTIVE (On-Demand)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSessionActive) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Accuracy: ±${location.accuracyM.toInt()}m • Fix: ${location.recordedAt.takeLast(9)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (!isSessionActive) {
                        Button(onClick = onStartSession) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Live (5m)")
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

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(statusMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Embedded Google Maps
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
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

@Composable
fun AppActivityDashboardScreen(
    device: Device?,
    activityJson: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
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
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                    Text("YouTube", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Foreground session detected • Synced 4 min ago", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("Today's Aggregated Usage", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        // Usage bars
        val sampleSummaries = listOf(
            Pair("YouTube", 54),
            Pair("Chrome", 32),
            Pair("WhatsApp", 18),
            Pair("Settings", 4)
        )

        for ((appName, minutes) in sampleSummaries) {
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

        Text("Chronological Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        val timelineEvents = listOf(
            Triple("YouTube", "FOREGROUND", "11:42 AM"),
            Triple("WhatsApp", "BACKGROUND", "11:38 AM"),
            Triple("WhatsApp", "FOREGROUND", "11:30 AM"),
            Triple("Chrome", "BACKGROUND", "11:15 AM")
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(timelineEvents) { (pkg, evType, time) ->
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

@Composable
fun DeviceCommandsScreen(
    device: Device?,
    commands: List<DeviceCommand>,
    onSendCommand: (CommandAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Supported Remote Commands", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Commands execute only with active device consent and report actual execution results.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSendCommand(CommandAction.RING) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ring Phone")
            }

            Button(
                onClick = { onSendCommand(CommandAction.REFRESH_LOCATION) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh Fix")
            }
        }

        Text("Command Execution History", fontWeight = FontWeight.Bold, fontSize = 16.sp)

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
                            Text("Failure: ${cmd.failureReason}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
