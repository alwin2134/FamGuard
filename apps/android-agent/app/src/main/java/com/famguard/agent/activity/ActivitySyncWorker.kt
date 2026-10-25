package com.famguard.agent.activity

import android.content.Context
import androidx.work.*
import com.famguard.agent.auth.AgentApiClient
import com.famguard.agent.security.DeviceKeystoreManager
import java.util.concurrent.TimeUnit

class ActivitySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val keystoreManager = DeviceKeystoreManager(applicationContext)
        if (!keystoreManager.isPaired()) {
            return Result.success()
        }

        val usageManager = UsageAccessManager(applicationContext)
        val database = AgentDatabase.getInstance(applicationContext)
        val apiClient = AgentApiClient(applicationContext)
        val repository = ActivityRepository(applicationContext, database, keystoreManager, apiClient)

        return try {
            // 1. Collect new usage events if permission is granted
            if (usageManager.hasUsageAccess()) {
                repository.collectAndQueueEvents()
            }

            // 2. Sync pending events and installed apps to Supabase
            val syncResult = repository.syncPendingEventsToServer()

            if (syncResult.isSuccess) {
                Result.success()
            } else {
                // If network failure, retry with exponential backoff
                Result.retry()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val PERIODIC_WORK_TAG = "famguard_activity_periodic_sync"
        private const val ONE_TIME_WORK_TAG = "famguard_activity_one_time_sync"

        /**
         * Enqueues periodic 15-minute synchronization worker with network constraints.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ActivitySyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(PERIODIC_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Enqueues immediate one-time sync (e.g. on app launch or reconciliation).
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ActivitySyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(ONE_TIME_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
