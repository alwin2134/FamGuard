package com.famguard.agent.activity

import android.content.Context
import androidx.room.*
import com.famguard.contracts.AppEventType

@Entity(
    tableName = "pending_app_events",
    indices = [
        Index(value = ["package_name", "event_type", "occurred_at"], unique = true)
    ]
)
data class PendingAppEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String?,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "last_processed_timestamp") val lastProcessedTimestamp: Long,
    @ColumnInfo(name = "last_sync_attempt") val lastSyncAttempt: Long
)

@Dao
interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<PendingAppEventEntity>): List<Long>

    @Query("SELECT * FROM pending_app_events WHERE is_synced = 0 ORDER BY occurred_at ASC LIMIT :limit")
    suspend fun getUnsyncedEvents(limit: Int = 100): List<PendingAppEventEntity>

    @Query("UPDATE pending_app_events SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markEventsSynced(ids: List<String>)

    @Query("DELETE FROM pending_app_events WHERE is_synced = 1 AND created_at < :olderThanMs")
    suspend fun purgeSyncedEvents(olderThanMs: Long)

    @Query("SELECT * FROM sync_cursor WHERE id = 1 LIMIT 1")
    suspend fun getCursor(): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCursor(cursor: SyncCursorEntity)
}

@Database(
    entities = [PendingAppEventEntity::class, SyncCursorEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao

    companion object {
        @Volatile
        private var INSTANCE: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "famguard_agent.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
