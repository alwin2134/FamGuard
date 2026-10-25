package com.famguard.agent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famguard.agent.activity.ActivitySyncWorker
import com.famguard.agent.activity.UsageAccessManager
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.commands.CommandExecutor
import com.famguard.agent.location.LocationTrackingService
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.agent.security.OemCompatibilityManager
import com.famguard.agent.ui.qr.QrScannerScreen
import com.famguard.contracts.CompletePairingRequest
import com.famguard.contracts.DeviceConsentInput
import com.famguard.contracts.QrPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

private fun checkHasLocation(ctx: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun checkGpsEnabled(ctx: Context): Boolean {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
}

private fun checkHasNotification(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkHasCamera(ctx: Context): Boolean {
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentMainScreen(
    keystoreManager: DeviceKeystoreManager,
    usageAccessManager: UsageAccessManager,
    apiClient: AgentApiClient
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isPaired by remember { mutableStateOf(keystoreManager.isPaired()) }
    var familyName by remember { mutableStateOf(keystoreManager.getFamilyName() ?: "") }
    var deviceId by remember { mutableStateOf(keystoreManager.getDeviceId() ?: "") }
    val oemManager = remember { OemCompatibilityManager(context) }
    var hasUsageAccess by remember { mutableStateOf(usageAccessManager.hasUsageAccess()) }
    var isIgnoringBattery by remember { mutableStateOf(oemManager.isIgnoringBatteryOptimizations()) }
    var canDrawOverlay by remember { mutableStateOf(oemManager.canDrawOverlays()) }

    var activeSessionId by remember { mutableStateOf(keystoreManager.getActiveSessionId()) }
    var lastFixTime by remember { mutableStateOf(keystoreManager.getLastLocationFix()) }
    var lastSyncTime by remember { mutableStateOf(keystoreManager.getLastSyncTimestamp()) }

    var showPairingDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var pairingPayloadJson by remember { mutableStateOf("") }
    var isPairingLoading by remember { mutableStateOf(false) }
    var pairingErrorMessage by remember { mutableStateOf<String?>(null) }

    // Consent toggles for pairing dialog
    var consentLocation by remember { mutableStateOf(true) }
    var consentActivity by remember { mutableStateOf(true) }
    var consentCommands by remember { mutableStateOf(true) }

    val commandExecutor = remember { CommandExecutor(context, apiClient, keystoreManager) }

    var hasLocationPerm by remember { mutableStateOf(checkHasLocation(context)) }
    var isGpsActive by remember { mutableStateOf(checkGpsEnabled(context)) }
    var hasNotificationPerm by remember { mutableStateOf(checkHasNotification(context)) }
    var hasCameraPerm by remember { mutableStateOf(checkHasCamera(context)) }

    // Unified initial permission prompt for Location, Notifications, and Camera
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPerm = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                checkHasLocation(context)
        isGpsActive = checkGpsEnabled(context)
        hasNotificationPerm = checkHasNotification(context)
        hasCameraPerm = checkHasCamera(context)
    }

    // Automatically trigger comprehensive permission request on launch
    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasLocationPerm) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPerm) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hasCameraPerm) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // Real-time permission & state refresher loop
    LaunchedEffect(Unit) {
        while (isActive) {
            hasUsageAccess = usageAccessManager.hasUsageAccess()
            hasLocationPerm = checkHasLocation(context)
            isGpsActive = checkGpsEnabled(context)
            hasNotificationPerm = checkHasNotification(context)
            hasCameraPerm = checkHasCamera(context)
            isIgnoringBattery = oemManager.isIgnoringBatteryOptimizations()
            canDrawOverlay = oemManager.canDrawOverlays()
            delay(1500)
        }
    }

    // Status & Live Command Execution loop
    LaunchedEffect(isPaired) {
        hasUsageAccess = usageAccessManager.hasUsageAccess()
        activeSessionId = keystoreManager.getActiveSessionId()
        if (isPaired) {
            com.famguard.agent.service.FamGuardProtectionService.startService(context)
            while (isActive && isPaired) {
                val devId = keystoreManager.getDeviceId()
                val devSecret = keystoreManager.getDeviceSecret()
                if (devId != null && devSecret != null) {
                    val pendingRes = apiClient.fetchPendingCommands(devId, devSecret)
                    pendingRes.onSuccess { commands ->
                        if (commands.isNotEmpty()) {
                            android.util.Log.d("AgentScreens", "Found ${commands.size} pending commands: ${commands.map { it.action }}")
                        }
                        for (cmd in commands) {
                            commandExecutor.executeCommand(cmd)
                        }
                    }
                    pendingRes.onFailure { err ->
                        android.util.Log.w("AgentScreens", "fetchPendingCommands failed: ${err.message}")
                    }
                }
                activeSessionId = keystoreManager.getActiveSessionId()
                lastFixTime = keystoreManager.getLastLocationFix()
                delay(2500)
            }
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            onQrCodeScanned = { scanned ->
                pairingPayloadJson = scanned
                showQrScanner = false
                showPairingDialog = true
            },
            onManualEntry = {
                showQrScanner = false
                showPairingDialog = true
            },
            onDismiss = {
                showQrScanner = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FamGuard Agent", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showQrScanner = true }) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Pairing QR",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Pairing Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPaired) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isPaired) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPaired) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPaired) "Linked to $familyName" else "Device Not Paired",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isPaired) "Device ID: ${deviceId.take(8)}..." else "Scan guardian QR code to establish link",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Setup Banner if Critical Permissions Are Missing
            val hasMissingPermissions = !hasLocationPerm || !hasUsageAccess || !canDrawOverlay || !isIgnoringBattery || !hasNotificationPerm
            if (hasMissingPermissions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = Color(0xFFE65100))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Setup Permissions Needed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "To ensure live location, app usage activity, and protection work reliably in the background on ${oemManager.getOemDisplayName()}, complete the permissions below:",
                            fontSize = 12.sp,
                            color = Color(0xFF5D4037)
                        )
                    }
                }
            }

            if (!isPaired) {
                // Not Paired State - Pairing Actions
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pair with Family Guardian", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Scan the QR code displayed on your guardian's controller phone or enter the pairing code manually to view and approve permissions.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Guardian QR Code")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showPairingDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Code Manually")
                        }
                    }
                }

                // Device Background & OEM Protection Card
                OemSetupGuideCard(
                    oemManager = oemManager,
                    isIgnoringBattery = isIgnoringBattery,
                    canDrawOverlay = canDrawOverlay
                )
            } else {
                // Paired State - Transparent Monitoring & Location Cards
                Text(
                    "Transparent Consent Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Live Location Sharing Card
                val locationStatusText = when {
                    !hasLocationPerm -> "PERMISSION MISSING"
                    !isGpsActive -> "GPS DISABLED"
                    activeSessionId != null -> "LIVE STREAMING"
                    else -> "STANDBY (READY TO STREAM)"
                }
                val locationStatusColor = when {
                    !hasLocationPerm -> MaterialTheme.colorScheme.error
                    !isGpsActive -> Color(0xFFF59E0B)
                    activeSessionId != null -> Color(0xFF10B981)
                    else -> Color(0xFF2E7D32)
                }
                val locationDescription = when {
                    !hasLocationPerm -> "Location permission ('Allow all the time' or 'While using app') is required for GPS tracking."
                    !isGpsActive -> "Device Location / GPS is turned OFF in phone settings. Turn ON GPS to enable location fixes."
                    activeSessionId != null -> "WhatsApp-style Live Location: Real-time GPS stream is active. Coordinates are uploading to family map."
                    else -> "Location is on standby. Activates into continuous live streaming when requested by your guardian."
                }

                StatusCard(
                    title = "Live Location Sharing",
                    icon = Icons.Default.LocationOn,
                    statusText = locationStatusText,
                    statusColor = locationStatusColor,
                    description = locationDescription,
                    lastActionText = if (lastFixTime > 0)
                        "Last location fix: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastFixTime))}"
                    else "No recent fix"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (!hasLocationPerm) {
                            Button(onClick = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }) {
                                Text("Grant Location")
                            }
                        }
                        if (!isGpsActive) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }) {
                                Text("Turn On GPS")
                            }
                        }
                        if (activeSessionId != null) {
                            Button(
                                onClick = {
                                    LocationTrackingService.stop(context)
                                    activeSessionId = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop Live Sharing")
                            }
                        } else if (hasLocationPerm && isGpsActive) {
                            OutlinedButton(onClick = {
                                val testSessionId = "live_stream_${System.currentTimeMillis()}"
                                LocationTrackingService.start(context, testSessionId, 0L)
                                activeSessionId = testSessionId
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Live Stream")
                            }
                        }
                    }
                }

                // 2. App Usage Monitoring Card
                StatusCard(
                    title = "App Activity Monitoring",
                    icon = Icons.Default.Apps,
                    statusText = if (hasUsageAccess) "USAGE ACCESS GRANTED" else "USAGE ACCESS MISSING",
                    statusColor = if (hasUsageAccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    description = if (hasUsageAccess)
                        "Foreground app transitions are batched locally and synchronized efficiently every ~15 minutes."
                    else
                        "Android blocks app activity reading until you grant Usage Access in Android Settings.",
                    lastActionText = if (lastSyncTime > 0)
                        "Last synchronized: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSyncTime))}"
                    else "No syncs yet"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasUsageAccess) {
                            Button(onClick = {
                                context.startActivity(usageAccessManager.createUsageAccessSettingsIntent())
                            }) {
                                Text("Grant Usage Access")
                            }
                        } else {
                            OutlinedButton(onClick = {
                                ActivitySyncWorker.triggerImmediateSync(context)
                                lastSyncTime = System.currentTimeMillis()
                            }) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now")
                            }
                        }
                    }
                }

                // 3. App Lock & Screen Overlay Card
                StatusCard(
                    title = "App Lock & Screen Overlay",
                    icon = Icons.Default.Lock,
                    statusText = if (canDrawOverlay) "OVERLAY ACTIVE" else "PERMISSION REQUIRED",
                    statusColor = if (canDrawOverlay) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    description = if (canDrawOverlay)
                        "Overlay permission is granted. FamGuard can display screen blocking overlays on restricted apps."
                    else
                        "Android blocks screen locking overlays until 'Appear on top' is granted in Settings.",
                    lastActionText = if (oemManager.isVivoOrIqoo)
                        "iQOO/Vivo: Must also grant 'Display pop-up windows while running in background'"
                    else
                        "Protects against unauthorized app usage"
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!canDrawOverlay) {
                            Button(onClick = { oemManager.openOverlaySettings() }) {
                                Text("Grant Overlay")
                            }
                        }
                        if (oemManager.isVivoOrIqoo) {
                            OutlinedButton(onClick = { oemManager.openBackgroundPopupSettings() }) {
                                Text("iQOO/Vivo Pop-ups")
                            }
                        }
                    }
                }

                // 4. Background Battery & Sleep Protection Card
                StatusCard(
                    title = "Background Sleep & Battery Protection",
                    icon = Icons.Default.BatteryChargingFull,
                    statusText = if (isIgnoringBattery) "UNRESTRICTED (PROTECTED)" else "OPTIMIZED (FREEZE RISK)",
                    statusColor = if (isIgnoringBattery) Color(0xFF2E7D32) else Color(0xFFF59E0B),
                    description = if (isIgnoringBattery)
                        "Battery optimization is disabled. Background live location and remote commands will remain active even when locked."
                    else
                        "${oemManager.getOemDisplayName()} will freeze or kill background services when locked unless battery optimization is set to Unrestricted.",
                    lastActionText = "Manufacturer: ${oemManager.getOemDisplayName()}"
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isIgnoringBattery) {
                            Button(onClick = { oemManager.requestIgnoreBatteryOptimizations() }) {
                                Text("Allow Unrestricted")
                            }
                        }
                        if (oemManager.isCustomOem) {
                            OutlinedButton(onClick = { oemManager.openAutostartSettings() }) {
                                Text("Open Autostart")
                            }
                        }
                    }
                }

                // 5. Notifications Permission Card
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    StatusCard(
                        title = "Foreground & Push Notifications",
                        icon = Icons.Default.Notifications,
                        statusText = if (hasNotificationPerm) "NOTIFICATIONS ENABLED" else "NOTIFICATIONS DISABLED",
                        statusColor = if (hasNotificationPerm) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        description = if (hasNotificationPerm)
                            "Notifications permitted. The system will keep FamGuard foreground services active reliably."
                        else
                            "Android 13+ requires notification permission so the foreground service is not killed by the OS.",
                        lastActionText = "Required for reliable persistent background protection"
                    ) {
                        if (!hasNotificationPerm) {
                            Button(onClick = {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }) {
                                Text("Enable Notifications")
                            }
                        }
                    }
                }

                // 6. Remote Device Controls Card
                StatusCard(
                    title = "Remote Device Controls",
                    icon = Icons.Default.NotificationsActive,
                    statusText = "SUPPORTED (RING & REFRESH)",
                    statusColor = Color(0xFF2E7D32),
                    description = "Permits audible ring alerts to help locate the phone, and location refresh when authorized.",
                    lastActionText = "Silent and protected: NO secret microphone, screen recording, or text interception"
                )

                // 7. OEM Compatibility & Instructions Card
                OemSetupGuideCard(
                    oemManager = oemManager,
                    isIgnoringBattery = isIgnoringBattery,
                    canDrawOverlay = canDrawOverlay
                )

                // 8. Privacy and Revocation
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Privacy Guarantee & Unlinking", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "FamGuard requires mutual consent. You can revoke all permissions or unlink this device from the family at any time.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val devId = keystoreManager.getDeviceId()
                                    val devSec = keystoreManager.getDeviceSecret()
                                    if (devId != null && devSec != null) {
                                        apiClient.revokeDevice(devId, devSec, "Target unlinked")
                                    }
                                    keystoreManager.clearCredentials()
                                    isPaired = false
                                    familyName = ""
                                    deviceId = ""
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlink & Revoke All Consents")
                        }
                    }
                }
            }
        }
    }

    // Pairing Approval Dialog
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPairingLoading) showPairingDialog = false },
            title = { Text("Approve Device Pairing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pairing payload or JSON:",
                            fontSize = 13.sp
                        )
                        TextButton(
                            onClick = {
                                showPairingDialog = false
                                showQrScanner = true
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan QR", fontSize = 12.sp)
                        }
                    }
                    OutlinedTextField(
                        value = pairingPayloadJson,
                        onValueChange = { pairingPayloadJson = it },
                        label = { Text("QR Payload JSON") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    Text("Granular Permission Scopes:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consentLocation, onCheckedChange = { consentLocation = it })
                        Text("Live Location Sharing (Time-limited)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consentActivity, onCheckedChange = { consentActivity = it })
                        Text("App Activity Usage Summaries", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consentCommands, onCheckedChange = { consentCommands = it })
                        Text("Remote Ring & Location Refresh", fontSize = 13.sp)
                    }

                    if (pairingErrorMessage != null) {
                        Text(
                            text = pairingErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isPairingLoading && pairingPayloadJson.isNotBlank(),
                    onClick = {
                        isPairingLoading = true
                        pairingErrorMessage = null
                        coroutineScope.launch {
                            try {
                                val json = Json { ignoreUnknownKeys = true }
                                val parsed = json.decodeFromString<QrPayload>(pairingPayloadJson.trim())

                                val model = "${Build.MANUFACTURER} ${Build.MODEL}"
                                val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

                                val res = apiClient.completePairing(
                                    CompletePairingRequest(
                                        token = parsed.token,
                                        deviceName = "${Build.MODEL} Phone",
                                        deviceModel = model,
                                        osVersion = osVersion,
                                        consents = DeviceConsentInput(
                                            locationSharing = consentLocation,
                                            appActivity = consentActivity,
                                            remoteCommands = consentCommands
                                        )
                                    )
                                )

                                res.fold(
                                    onSuccess = { paired ->
                                        keystoreManager.saveDeviceCredentials(
                                            deviceId = paired.deviceId,
                                            deviceSecret = paired.deviceSecret,
                                            familyId = paired.familyId,
                                            familyName = paired.familyName
                                        )
                                        isPaired = true
                                        familyName = paired.familyName
                                        deviceId = paired.deviceId
                                        showPairingDialog = false

                                        // Start periodic work if activity is consented
                                        if (consentActivity) {
                                            ActivitySyncWorker.schedulePeriodicSync(context)
                                        }
                                    },
                                    onFailure = { err ->
                                        pairingErrorMessage = err.message ?: "Failed to complete pairing"
                                    }
                                )
                            } catch (e: Exception) {
                                pairingErrorMessage = "Invalid QR payload format: ${e.message}"
                            } finally {
                                isPairingLoading = false
                            }
                        }
                    }
                ) {
                    if (isPairingLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Approve & Pair")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isPairingLoading,
                    onClick = { showPairingDialog = false }
                ) {
                    Text("Decline")
                }
            }
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    icon: ImageVector,
    statusText: String,
    statusColor: Color,
    description: String,
    lastActionText: String,
    actionContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastActionText, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

            if (actionContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                actionContent()
            }
        }
    }
}

@Composable
fun OemSetupGuideCard(
    oemManager: OemCompatibilityManager,
    isIgnoringBattery: Boolean,
    canDrawOverlay: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Compatibility: ${oemManager.getOemDisplayName()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (oemManager.isCustomOem)
                            "Custom battery optimization must be disabled to prevent background freeze."
                        else
                            "Ensure unrestricted background battery and overlay permissions.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            for (step in oemManager.getOemActionInstructions()) {
                Text(
                    text = step,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action shortcut buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isIgnoringBattery) {
                    Button(
                        onClick = { oemManager.requestIgnoreBatteryOptimizations() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Battery Whitelist", fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (oemManager.isCustomOem) {
                    OutlinedButton(
                        onClick = { oemManager.openAutostartSettings() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Autostart", fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (oemManager.isVivoOrIqoo) {
                    OutlinedButton(
                        onClick = { oemManager.openBackgroundPopupSettings() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pop-up Window", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
