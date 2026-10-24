package com.famguard.agent.activity;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AgentDao_Impl implements AgentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PendingAppEventEntity> __insertionAdapterOfPendingAppEventEntity;

  private final EntityInsertionAdapter<SyncCursorEntity> __insertionAdapterOfSyncCursorEntity;

  private final SharedSQLiteStatement __preparedStmtOfPurgeSyncedEvents;

  public AgentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPendingAppEventEntity = new EntityInsertionAdapter<PendingAppEventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `pending_app_events` (`id`,`package_name`,`app_label`,`event_type`,`occurred_at`,`duration_seconds`,`source`,`is_synced`,`created_at`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PendingAppEventEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getPackageName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getPackageName());
        }
        if (entity.getAppLabel() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getAppLabel());
        }
        if (entity.getEventType() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getEventType());
        }
        if (entity.getOccurredAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getOccurredAt());
        }
        if (entity.getDurationSeconds() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getDurationSeconds());
        }
        if (entity.getSource() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSource());
        }
        final int _tmp = entity.isSynced() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindLong(9, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfSyncCursorEntity = new EntityInsertionAdapter<SyncCursorEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sync_cursor` (`id`,`last_processed_timestamp`,`last_sync_attempt`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SyncCursorEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getLastProcessedTimestamp());
        statement.bindLong(3, entity.getLastSyncAttempt());
      }
    };
    this.__preparedStmtOfPurgeSyncedEvents = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM pending_app_events WHERE is_synced = 1 AND created_at < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertEvents(final List<PendingAppEventEntity> events,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfPendingAppEventEntity.insertAndReturnIdsList(events);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCursor(final SyncCursorEntity cursor,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSyncCursorEntity.insert(cursor);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object purgeSyncedEvents(final long olderThanMs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfPurgeSyncedEvents.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, olderThanMs);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfPurgeSyncedEvents.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsyncedEvents(final int limit,
      final Continuation<? super List<PendingAppEventEntity>> $completion) {
    final String _sql = "SELECT * FROM pending_app_events WHERE is_synced = 0 ORDER BY occurred_at ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PendingAppEventEntity>>() {
      @Override
      @NonNull
      public List<PendingAppEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "package_name");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "app_label");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "event_type");
          final int _cursorIndexOfOccurredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "occurred_at");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "duration_seconds");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfIsSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "is_synced");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final List<PendingAppEventEntity> _result = new ArrayList<PendingAppEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PendingAppEventEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpPackageName;
            if (_cursor.isNull(_cursorIndexOfPackageName)) {
              _tmpPackageName = null;
            } else {
              _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            }
            final String _tmpAppLabel;
            if (_cursor.isNull(_cursorIndexOfAppLabel)) {
              _tmpAppLabel = null;
            } else {
              _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            }
            final String _tmpEventType;
            if (_cursor.isNull(_cursorIndexOfEventType)) {
              _tmpEventType = null;
            } else {
              _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            }
            final String _tmpOccurredAt;
            if (_cursor.isNull(_cursorIndexOfOccurredAt)) {
              _tmpOccurredAt = null;
            } else {
              _tmpOccurredAt = _cursor.getString(_cursorIndexOfOccurredAt);
            }
            final Long _tmpDurationSeconds;
            if (_cursor.isNull(_cursorIndexOfDurationSeconds)) {
              _tmpDurationSeconds = null;
            } else {
              _tmpDurationSeconds = _cursor.getLong(_cursorIndexOfDurationSeconds);
            }
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final boolean _tmpIsSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSynced);
            _tmpIsSynced = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new PendingAppEventEntity(_tmpId,_tmpPackageName,_tmpAppLabel,_tmpEventType,_tmpOccurredAt,_tmpDurationSeconds,_tmpSource,_tmpIsSynced,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCursor(final Continuation<? super SyncCursorEntity> $completion) {
    final String _sql = "SELECT * FROM sync_cursor WHERE id = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SyncCursorEntity>() {
      @Override
      @Nullable
      public SyncCursorEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLastProcessedTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "last_processed_timestamp");
          final int _cursorIndexOfLastSyncAttempt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_sync_attempt");
          final SyncCursorEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final long _tmpLastProcessedTimestamp;
            _tmpLastProcessedTimestamp = _cursor.getLong(_cursorIndexOfLastProcessedTimestamp);
            final long _tmpLastSyncAttempt;
            _tmpLastSyncAttempt = _cursor.getLong(_cursorIndexOfLastSyncAttempt);
            _result = new SyncCursorEntity(_tmpId,_tmpLastProcessedTimestamp,_tmpLastSyncAttempt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object markEventsSynced(final List<String> ids,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE pending_app_events SET is_synced = 1 WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          if (_item == null) {
            _stmt.bindNull(_argIndex);
          } else {
            _stmt.bindString(_argIndex, _item);
          }
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
