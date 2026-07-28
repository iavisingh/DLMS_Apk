package com.example.dlmsconfigurator.core.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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
public final class AuthEventDao_Impl implements AuthEventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AuthEventEntity> __insertionAdapterOfAuthEventEntity;

  public AuthEventDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAuthEventEntity = new EntityInsertionAdapter<AuthEventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `auth_events` (`id`,`timestamp`,`result`,`authMethod`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AuthEventEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getResult());
        statement.bindString(4, entity.getAuthMethod());
      }
    };
  }

  @Override
  public long insert(final AuthEventEntity authEvent) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfAuthEventEntity.insertAndReturnId(authEvent);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<AuthEventEntity> getAllAuthEvents() {
    final String _sql = "SELECT * FROM auth_events ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
      final int _cursorIndexOfAuthMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "authMethod");
      final List<AuthEventEntity> _result = new ArrayList<AuthEventEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final AuthEventEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        final String _tmpResult;
        _tmpResult = _cursor.getString(_cursorIndexOfResult);
        final String _tmpAuthMethod;
        _tmpAuthMethod = _cursor.getString(_cursorIndexOfAuthMethod);
        _item = new AuthEventEntity(_tmpId,_tmpTimestamp,_tmpResult,_tmpAuthMethod);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Flow<List<AuthEventEntity>> getAllAuthEventsFlow() {
    final String _sql = "SELECT * FROM auth_events ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"auth_events"}, new Callable<List<AuthEventEntity>>() {
      @Override
      @NonNull
      public List<AuthEventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfResult = CursorUtil.getColumnIndexOrThrow(_cursor, "result");
          final int _cursorIndexOfAuthMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "authMethod");
          final List<AuthEventEntity> _result = new ArrayList<AuthEventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AuthEventEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpResult;
            _tmpResult = _cursor.getString(_cursorIndexOfResult);
            final String _tmpAuthMethod;
            _tmpAuthMethod = _cursor.getString(_cursorIndexOfAuthMethod);
            _item = new AuthEventEntity(_tmpId,_tmpTimestamp,_tmpResult,_tmpAuthMethod);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
