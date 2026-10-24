package com.famguard.agent.activity;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AgentDatabase_Impl extends AgentDatabase {
  private volatile AgentDao _agentDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_app_events` (`id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `app_label` TEXT, `event_type` TEXT NOT NULL, `occurred_at` TEXT NOT NULL, `duration_seconds` INTEGER, `source` TEXT NOT NULL, `is_synced` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_app_events_package_name_event_type_occurred_at` ON `pending_app_events` (`package_name`, `event_type`, `occurred_at`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_cursor` (`id` INTEGER NOT NULL, `last_processed_timestamp` INTEGER NOT NULL, `last_sync_attempt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '254b0d8bf0b2eeffefc4e87262215c82')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `pending_app_events`");
        db.execSQL("DROP TABLE IF EXISTS `sync_cursor`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPendingAppEvents = new HashMap<String, TableInfo.Column>(9);
        _columnsPendingAppEvents.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("package_name", new TableInfo.Column("package_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("app_label", new TableInfo.Column("app_label", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("event_type", new TableInfo.Column("event_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("occurred_at", new TableInfo.Column("occurred_at", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("duration_seconds", new TableInfo.Column("duration_seconds", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("is_synced", new TableInfo.Column("is_synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAppEvents.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPendingAppEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPendingAppEvents = new HashSet<TableInfo.Index>(1);
        _indicesPendingAppEvents.add(new TableInfo.Index("index_pending_app_events_package_name_event_type_occurred_at", true, Arrays.asList("package_name", "event_type", "occurred_at"), Arrays.asList("ASC", "ASC", "ASC")));
        final TableInfo _infoPendingAppEvents = new TableInfo("pending_app_events", _columnsPendingAppEvents, _foreignKeysPendingAppEvents, _indicesPendingAppEvents);
        final TableInfo _existingPendingAppEvents = TableInfo.read(db, "pending_app_events");
        if (!_infoPendingAppEvents.equals(_existingPendingAppEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "pending_app_events(com.famguard.agent.activity.PendingAppEventEntity).\n"
                  + " Expected:\n" + _infoPendingAppEvents + "\n"
                  + " Found:\n" + _existingPendingAppEvents);
        }
        final HashMap<String, TableInfo.Column> _columnsSyncCursor = new HashMap<String, TableInfo.Column>(3);
        _columnsSyncCursor.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncCursor.put("last_processed_timestamp", new TableInfo.Column("last_processed_timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncCursor.put("last_sync_attempt", new TableInfo.Column("last_sync_attempt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSyncCursor = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSyncCursor = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSyncCursor = new TableInfo("sync_cursor", _columnsSyncCursor, _foreignKeysSyncCursor, _indicesSyncCursor);
        final TableInfo _existingSyncCursor = TableInfo.read(db, "sync_cursor");
        if (!_infoSyncCursor.equals(_existingSyncCursor)) {
          return new RoomOpenHelper.ValidationResult(false, "sync_cursor(com.famguard.agent.activity.SyncCursorEntity).\n"
                  + " Expected:\n" + _infoSyncCursor + "\n"
                  + " Found:\n" + _existingSyncCursor);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "254b0d8bf0b2eeffefc4e87262215c82", "7f8751a96cb9f50235af07f70faa8894");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "pending_app_events","sync_cursor");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `pending_app_events`");
      _db.execSQL("DELETE FROM `sync_cursor`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(AgentDao.class, AgentDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public AgentDao agentDao() {
    if (_agentDao != null) {
      return _agentDao;
    } else {
      synchronized(this) {
        if(_agentDao == null) {
          _agentDao = new AgentDao_Impl(this);
        }
        return _agentDao;
      }
    }
  }
}
