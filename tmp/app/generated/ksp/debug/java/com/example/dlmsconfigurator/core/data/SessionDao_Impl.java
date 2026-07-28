package com.example.dlmsconfigurator.core.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SessionDao_Impl implements SessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SessionEntity> __insertionAdapterOfSessionEntity;

  private final EntityDeletionOrUpdateAdapter<SessionEntity> __deletionAdapterOfSessionEntity;

  private final EntityDeletionOrUpdateAdapter<SessionEntity> __updateAdapterOfSessionEntity;

  public SessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSessionEntity = new EntityInsertionAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sessions` (`id`,`startTime`,`endTime`,`meterSerial`,`jsonSourceFileName`,`detailedLogging`,`status`,`connectionOverrideUsed`,`syncedAt`,`remoteId`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getEndTime());
        }
        if (entity.getMeterSerial() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getMeterSerial());
        }
        statement.bindString(5, entity.getJsonSourceFileName());
        final int _tmp = entity.getDetailedLogging() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindString(7, entity.getStatus());
        final int _tmp_1 = entity.getConnectionOverrideUsed() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        if (entity.getSyncedAt() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getSyncedAt());
        }
        if (entity.getRemoteId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getRemoteId());
        }
      }
    };
    this.__deletionAdapterOfSessionEntity = new EntityDeletionOrUpdateAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `sessions` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfSessionEntity = new EntityDeletionOrUpdateAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `sessions` SET `id` = ?,`startTime` = ?,`endTime` = ?,`meterSerial` = ?,`jsonSourceFileName` = ?,`detailedLogging` = ?,`status` = ?,`connectionOverrideUsed` = ?,`syncedAt` = ?,`remoteId` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getStartTime());
        if (entity.getEndTime() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getEndTime());
        }
        if (entity.getMeterSerial() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getMeterSerial());
        }
        statement.bindString(5, entity.getJsonSourceFileName());
        final int _tmp = entity.getDetailedLogging() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindString(7, entity.getStatus());
        final int _tmp_1 = entity.getConnectionOverrideUsed() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        if (entity.getSyncedAt() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getSyncedAt());
        }
        if (entity.getRemoteId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getRemoteId());
        }
        statement.bindLong(11, entity.getId());
      }
    };
  }

  @Override
  public long insert(final SessionEntity session) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfSessionEntity.insertAndReturnId(session);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final SessionEntity session) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfSessionEntity.handle(session);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final SessionEntity session) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfSessionEntity.handle(session);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public SessionEntity getById(final long id) {
    final String _sql = "SELECT * FROM sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
      final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
      final int _cursorIndexOfMeterSerial = CursorUtil.getColumnIndexOrThrow(_cursor, "meterSerial");
      final int _cursorIndexOfJsonSourceFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "jsonSourceFileName");
      final int _cursorIndexOfDetailedLogging = CursorUtil.getColumnIndexOrThrow(_cursor, "detailedLogging");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfConnectionOverrideUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionOverrideUsed");
      final int _cursorIndexOfSyncedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedAt");
      final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
      final SessionEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpStartTime;
        _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
        final Long _tmpEndTime;
        if (_cursor.isNull(_cursorIndexOfEndTime)) {
          _tmpEndTime = null;
        } else {
          _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
        }
        final String _tmpMeterSerial;
        if (_cursor.isNull(_cursorIndexOfMeterSerial)) {
          _tmpMeterSerial = null;
        } else {
          _tmpMeterSerial = _cursor.getString(_cursorIndexOfMeterSerial);
        }
        final String _tmpJsonSourceFileName;
        _tmpJsonSourceFileName = _cursor.getString(_cursorIndexOfJsonSourceFileName);
        final boolean _tmpDetailedLogging;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDetailedLogging);
        _tmpDetailedLogging = _tmp != 0;
        final String _tmpStatus;
        _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
        final boolean _tmpConnectionOverrideUsed;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfConnectionOverrideUsed);
        _tmpConnectionOverrideUsed = _tmp_1 != 0;
        final Long _tmpSyncedAt;
        if (_cursor.isNull(_cursorIndexOfSyncedAt)) {
          _tmpSyncedAt = null;
        } else {
          _tmpSyncedAt = _cursor.getLong(_cursorIndexOfSyncedAt);
        }
        final String _tmpRemoteId;
        if (_cursor.isNull(_cursorIndexOfRemoteId)) {
          _tmpRemoteId = null;
        } else {
          _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
        }
        _result = new SessionEntity(_tmpId,_tmpStartTime,_tmpEndTime,_tmpMeterSerial,_tmpJsonSourceFileName,_tmpDetailedLogging,_tmpStatus,_tmpConnectionOverrideUsed,_tmpSyncedAt,_tmpRemoteId);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Flow<List<SessionEntity>> getAllSessionsFlow() {
    final String _sql = "SELECT * FROM sessions ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sessions"}, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfMeterSerial = CursorUtil.getColumnIndexOrThrow(_cursor, "meterSerial");
          final int _cursorIndexOfJsonSourceFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "jsonSourceFileName");
          final int _cursorIndexOfDetailedLogging = CursorUtil.getColumnIndexOrThrow(_cursor, "detailedLogging");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfConnectionOverrideUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionOverrideUsed");
          final int _cursorIndexOfSyncedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedAt");
          final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final Long _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            }
            final String _tmpMeterSerial;
            if (_cursor.isNull(_cursorIndexOfMeterSerial)) {
              _tmpMeterSerial = null;
            } else {
              _tmpMeterSerial = _cursor.getString(_cursorIndexOfMeterSerial);
            }
            final String _tmpJsonSourceFileName;
            _tmpJsonSourceFileName = _cursor.getString(_cursorIndexOfJsonSourceFileName);
            final boolean _tmpDetailedLogging;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDetailedLogging);
            _tmpDetailedLogging = _tmp != 0;
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final boolean _tmpConnectionOverrideUsed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfConnectionOverrideUsed);
            _tmpConnectionOverrideUsed = _tmp_1 != 0;
            final Long _tmpSyncedAt;
            if (_cursor.isNull(_cursorIndexOfSyncedAt)) {
              _tmpSyncedAt = null;
            } else {
              _tmpSyncedAt = _cursor.getLong(_cursorIndexOfSyncedAt);
            }
            final String _tmpRemoteId;
            if (_cursor.isNull(_cursorIndexOfRemoteId)) {
              _tmpRemoteId = null;
            } else {
              _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
            }
            _item = new SessionEntity(_tmpId,_tmpStartTime,_tmpEndTime,_tmpMeterSerial,_tmpJsonSourceFileName,_tmpDetailedLogging,_tmpStatus,_tmpConnectionOverrideUsed,_tmpSyncedAt,_tmpRemoteId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<SessionEntity> getAllSessions() {
    final String _sql = "SELECT * FROM sessions ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
      final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
      final int _cursorIndexOfMeterSerial = CursorUtil.getColumnIndexOrThrow(_cursor, "meterSerial");
      final int _cursorIndexOfJsonSourceFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "jsonSourceFileName");
      final int _cursorIndexOfDetailedLogging = CursorUtil.getColumnIndexOrThrow(_cursor, "detailedLogging");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfConnectionOverrideUsed = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionOverrideUsed");
      final int _cursorIndexOfSyncedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedAt");
      final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
      final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SessionEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpStartTime;
        _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
        final Long _tmpEndTime;
        if (_cursor.isNull(_cursorIndexOfEndTime)) {
          _tmpEndTime = null;
        } else {
          _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
        }
        final String _tmpMeterSerial;
        if (_cursor.isNull(_cursorIndexOfMeterSerial)) {
          _tmpMeterSerial = null;
        } else {
          _tmpMeterSerial = _cursor.getString(_cursorIndexOfMeterSerial);
        }
        final String _tmpJsonSourceFileName;
        _tmpJsonSourceFileName = _cursor.getString(_cursorIndexOfJsonSourceFileName);
        final boolean _tmpDetailedLogging;
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfDetailedLogging);
        _tmpDetailedLogging = _tmp != 0;
        final String _tmpStatus;
        _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
        final boolean _tmpConnectionOverrideUsed;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfConnectionOverrideUsed);
        _tmpConnectionOverrideUsed = _tmp_1 != 0;
        final Long _tmpSyncedAt;
        if (_cursor.isNull(_cursorIndexOfSyncedAt)) {
          _tmpSyncedAt = null;
        } else {
          _tmpSyncedAt = _cursor.getLong(_cursorIndexOfSyncedAt);
        }
        final String _tmpRemoteId;
        if (_cursor.isNull(_cursorIndexOfRemoteId)) {
          _tmpRemoteId = null;
        } else {
          _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
        }
        _item = new SessionEntity(_tmpId,_tmpStartTime,_tmpEndTime,_tmpMeterSerial,_tmpJsonSourceFileName,_tmpDetailedLogging,_tmpStatus,_tmpConnectionOverrideUsed,_tmpSyncedAt,_tmpRemoteId);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
