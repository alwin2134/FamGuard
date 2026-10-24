package com.famguard.agent.ui

import android.content.Intent
import android.os.Build
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
import com.famguard.agent.location.LocationTrackingService
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.contracts.CompletePairingRequest
import com.famguard.contracts.DeviceConsentInput
import com.famguard.contracts.QrPayload
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

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
    var hasUsageAccess by remember { mutableStateOf(usageAccessManager.hasUsageAccess()) }

    var activeSessionId by remember { mutableStateOf(keystoreManager.getActiveSessionId()) }
    var lastFixTime by remember { mutableStateOf(keystoreManager.getLastLocationFix()) }
    var lastSyncTime by remember { mutableStateOf(keystoreManager.getLastSyncTimestamp()) }

    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingPayloadJson by remember { mutableStateOf("") }
    var isPairingLoading by remember { mutableStateOf(false) }
    var pairingErrorMessage by remember { mutableStateOf<String?>(null) }

    // Consent toggles for pairing dialog
    var consentLocation by remember { mutableStateOf(true) }
    var consentActivity by remember { mutableStateOf(true) }
    var consentCommands by remember { mutableStateOf(true) }

    // Status updater
    LaunchedEffect(Unit) {
        hasUsageAccess = usageAccessManager.hasUsageAccess()
        activeSessionId = keystoreManager.getActiveSessionId()
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
                            onClick = { showPairingDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter Pairing QR Payload")
                        }
                    }
                }
            } else {
                // Paired State - Transparent Monitoring & Location Cards
                Text(
                    "Transparent Consent Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. Live Location Sharing Card
                StatusCard(
                    title = "Live Location Sharing",
                    icon = Icons.Default.LocationOn,
                    statusText = if (activeSessionId != null) "LIVE SESSION ACTIVE" else "STANDBY (On-Demand Only)",
                    statusColor = if (activeSessionId != null) Color(0xFF1976D2) else Color(0xFF757575),
                    description = if (activeSessionId != null)
                        "An authorized live location session is active. Coordinates are uploaded every ~12s."
                    else
                        "Location is NOT continuously tracked. Location activates only when authorized by you and your guardian for up to 5 minutes.",
                    lastActionText = if (lastFixTime > 0)
                        "Last location fix: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastFixTime))}"
                    else "No recent fix"
                ) {
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
                                Text("Open Settings")
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

                // 3. Remote Commands Card
                StatusCard(
                    title = "Remote Device Controls",
                    icon = Icons.Default.NotificationsActive,
                    statusText = "SUPPORTED (RING & REFRESH)",
                    statusColor = Color(0xFF2E7D32),
                    description = "Permits audible ring alerts to help locate the phone, and location refresh when authorized.",
                    lastActionText = "Silent and protected: NO secret microphone, screen recording, or text interception"
                )

                // 4. Privacy and Revocation
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
                    Text(
                        "Paste QR payload string received from controller:",
                        fontSize = 13.sp
                    )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
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
