package com.famguard.agent.activity

import android.content.Context
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import com.famguard.contracts.AppEventType
import com.famguard.contracts.DeviceAppEvent
import com.famguard.contracts.IngestActivityRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivityRepository(
    private val context: Context,
    private val database: AgentDatabase,
    private val keystoreManager: DeviceKeystoreManager,
    private val apiClient: AgentApiClient,
    private val usageAccessManager: UsageAccessManager = UsageAccessManager(context),
    private val eventReader: UsageEventReader = UsageEventReader(context),
    private val sessionTracker: AppSessionTracker = AppSessionTracker()
) {
    private val dao = database.agentDao()

    suspend fun collectAndQueueEvents(): Result<Int> = withContext(Dispatchers.IO) {
        if (!keystoreManager.isPaired()) {
            return@withContext Result.failure(IllegalStateException("Device is not paired"))
        }

        if (!usageAccessManager.hasUsageAccess()) {
            return@withContext Result.failure(SecurityException("Usage Access permission is missing or revoked"))
        }

        val now = System.currentTimeMillis()
        val cursor = dao.getCursor()
        // If cursor exists, start from 5 minutes before cursor (safety overlap for deduplication); else start from past 2 hours
        val startTime = cursor?.lastProcessedTimestamp?.let { it - (5 * 60 * 1000) }
            ?: (now - (2 * 60 * 60 * 1000))

        val rawEvents = eventReader.readEvents(startTime, now)
        if (rawEvents.isEmpty()) {
            dao.updateCursor(SyncCursorEntity(1, now, now))
            return@withContext Result.success(0)
        }

        val processedEntities = sessionTracker.processEvents(rawEvents)
        dao.insertEvents(processedEntities)
        dao.updateCursor(SyncCursorEntity(1, now, now))

        Result.success(processedEntities.size)
    }

    suspend fun syncPendingEventsToServer(): Result<Int> = withContext(Dispatchers.IO) {
        val deviceId = keystoreManager.getDeviceId()
            ?: return@withContext Result.failure(IllegalStateException("No enrolled device ID"))
        val deviceSecret = keystoreManager.getDeviceSecret()
            ?: return@withContext Result.failure(IllegalStateException("No enrolled device secret"))

        val unsynced = dao.getUnsyncedEvents(limit = 100)
        if (unsynced.isEmpty()) {
            return@withContext Result.success(0)
        }

        val dtoList = unsynced.map { entity ->
            DeviceAppEvent(
                id = entity.id,
                deviceId = deviceId,
                packageName = entity.packageName,
                appLabel = entity.appLabel,
                eventType = if (entity.eventType == "foreground") AppEventType.FOREGROUND else AppEventType.BACKGROUND,
                occurredAt = entity.occurredAt,
                durationSeconds = entity.durationSeconds,
                source = entity.source
            )
        }

        val response = apiClient.ingestActivity(
            IngestActivityRequest(
                deviceId = deviceId,
                deviceSecret = deviceSecret,
                events = dtoList
            )
        )

        response.fold(
            onSuccess = { res ->
                dao.markEventsSynced(unsynced.map { it.id })
                keystoreManager.updateLastSyncTimestamp(System.currentTimeMillis())

                // Purge synced events older than 48 hours to save local flash storage
                val twoDaysAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000)
                dao.purgeSyncedEvents(twoDaysAgo)

                Result.success(res.ingested)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}
