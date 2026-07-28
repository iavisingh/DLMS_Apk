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
public final class OperationDao_Impl implements OperationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OperationEntity> __insertionAdapterOfOperationEntity;

  public OperationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOperationEntity = new EntityInsertionAdapter<OperationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `operations` (`id`,`sessionId`,`sequenceNo`,`opType`,`obisCode`,`classId`,`attributeOrMethod`,`status`,`startTime`,`endTime`,`errorMessage`,`attemptNumber`,`maxAttemptsConfigured`,`rawRequestHex`,`rawResponseHex`,`decodedValue`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OperationEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getSessionId());
        statement.bindLong(3, entity.getSequenceNo());
        statement.bindString(4, entity.getOpType());
        statement.bindString(5, entity.getObisCode());
        statement.bindLong(6, entity.getClassId());
        statement.bindLong(7, entity.getAttributeOrMethod());
        statement.bindString(8, entity.getStatus());
        statement.bindLong(9, entity.getStartTime());
        statement.bindLong(10, entity.getEndTime());
        if (entity.getErrorMessage() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getErrorMessage());
        }
        statement.bindLong(12, entity.getAttemptNumber());
        statement.bindLong(13, entity.getMaxAttemptsConfigured());
        if (entity.getRawRequestHex() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getRawRequestHex());
        }
        if (entity.getRawResponseHex() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getRawResponseHex());
        }
        if (entity.getDecodedValue() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getDecodedValue());
        }
      }
    };
  }

  @Override
  public long insert(final OperationEntity operation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfOperationEntity.insertAndReturnId(operation);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertAll(final List<OperationEntity> operations) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfOperationEntity.insert(operations);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<OperationEntity> getOperationsForSession(final long sessionId) {
    final String _sql = "SELECT * FROM operations WHERE sessionId = ? ORDER BY sequenceNo ASC, attemptNumber ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
      final int _cursorIndexOfSequenceNo = CursorUtil.getColumnIndexOrThrow(_cursor, "sequenceNo");
      final int _cursorIndexOfOpType = CursorUtil.getColumnIndexOrThrow(_cursor, "opType");
      final int _cursorIndexOfObisCode = CursorUtil.getColumnIndexOrThrow(_cursor, "obisCode");
      final int _cursorIndexOfClassId = CursorUtil.getColumnIndexOrThrow(_cursor, "classId");
      final int _cursorIndexOfAttributeOrMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "attributeOrMethod");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
      final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
      final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
      final int _cursorIndexOfAttemptNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "attemptNumber");
      final int _cursorIndexOfMaxAttemptsConfigured = CursorUtil.getColumnIndexOrThrow(_cursor, "maxAttemptsConfigured");
      final int _cursorIndexOfRawRequestHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawRequestHex");
      final int _cursorIndexOfRawResponseHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawResponseHex");
      final int _cursorIndexOfDecodedValue = CursorUtil.getColumnIndexOrThrow(_cursor, "decodedValue");
      final List<OperationEntity> _result = new ArrayList<OperationEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final OperationEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpSessionId;
        _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
        final int _tmpSequenceNo;
        _tmpSequenceNo = _cursor.getInt(_cursorIndexOfSequenceNo);
        final String _tmpOpType;
        _tmpOpType = _cursor.getString(_cursorIndexOfOpType);
        final String _tmpObisCode;
        _tmpObisCode = _cursor.getString(_cursorIndexOfObisCode);
        final int _tmpClassId;
        _tmpClassId = _cursor.getInt(_cursorIndexOfClassId);
        final int _tmpAttributeOrMethod;
        _tmpAttributeOrMethod = _cursor.getInt(_cursorIndexOfAttributeOrMethod);
        final String _tmpStatus;
        _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
        final long _tmpStartTime;
        _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
        final long _tmpEndTime;
        _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
        final String _tmpErrorMessage;
        if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
          _tmpErrorMessage = null;
        } else {
          _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
        }
        final int _tmpAttemptNumber;
        _tmpAttemptNumber = _cursor.getInt(_cursorIndexOfAttemptNumber);
        final int _tmpMaxAttemptsConfigured;
        _tmpMaxAttemptsConfigured = _cursor.getInt(_cursorIndexOfMaxAttemptsConfigured);
        final String _tmpRawRequestHex;
        if (_cursor.isNull(_cursorIndexOfRawRequestHex)) {
          _tmpRawRequestHex = null;
        } else {
          _tmpRawRequestHex = _cursor.getString(_cursorIndexOfRawRequestHex);
        }
        final String _tmpRawResponseHex;
        if (_cursor.isNull(_cursorIndexOfRawResponseHex)) {
          _tmpRawResponseHex = null;
        } else {
          _tmpRawResponseHex = _cursor.getString(_cursorIndexOfRawResponseHex);
        }
        final String _tmpDecodedValue;
        if (_cursor.isNull(_cursorIndexOfDecodedValue)) {
          _tmpDecodedValue = null;
        } else {
          _tmpDecodedValue = _cursor.getString(_cursorIndexOfDecodedValue);
        }
        _item = new OperationEntity(_tmpId,_tmpSessionId,_tmpSequenceNo,_tmpOpType,_tmpObisCode,_tmpClassId,_tmpAttributeOrMethod,_tmpStatus,_tmpStartTime,_tmpEndTime,_tmpErrorMessage,_tmpAttemptNumber,_tmpMaxAttemptsConfigured,_tmpRawRequestHex,_tmpRawResponseHex,_tmpDecodedValue);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Flow<List<OperationEntity>> getOperationsForSessionFlow(final long sessionId) {
    final String _sql = "SELECT * FROM operations WHERE sessionId = ? ORDER BY sequenceNo ASC, attemptNumber ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"operations"}, new Callable<List<OperationEntity>>() {
      @Override
      @NonNull
      public List<OperationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfSequenceNo = CursorUtil.getColumnIndexOrThrow(_cursor, "sequenceNo");
          final int _cursorIndexOfOpType = CursorUtil.getColumnIndexOrThrow(_cursor, "opType");
          final int _cursorIndexOfObisCode = CursorUtil.getColumnIndexOrThrow(_cursor, "obisCode");
          final int _cursorIndexOfClassId = CursorUtil.getColumnIndexOrThrow(_cursor, "classId");
          final int _cursorIndexOfAttributeOrMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "attributeOrMethod");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final int _cursorIndexOfAttemptNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "attemptNumber");
          final int _cursorIndexOfMaxAttemptsConfigured = CursorUtil.getColumnIndexOrThrow(_cursor, "maxAttemptsConfigured");
          final int _cursorIndexOfRawRequestHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawRequestHex");
          final int _cursorIndexOfRawResponseHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawResponseHex");
          final int _cursorIndexOfDecodedValue = CursorUtil.getColumnIndexOrThrow(_cursor, "decodedValue");
          final List<OperationEntity> _result = new ArrayList<OperationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OperationEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final int _tmpSequenceNo;
            _tmpSequenceNo = _cursor.getInt(_cursorIndexOfSequenceNo);
            final String _tmpOpType;
            _tmpOpType = _cursor.getString(_cursorIndexOfOpType);
            final String _tmpObisCode;
            _tmpObisCode = _cursor.getString(_cursorIndexOfObisCode);
            final int _tmpClassId;
            _tmpClassId = _cursor.getInt(_cursorIndexOfClassId);
            final int _tmpAttributeOrMethod;
            _tmpAttributeOrMethod = _cursor.getInt(_cursorIndexOfAttributeOrMethod);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            final int _tmpAttemptNumber;
            _tmpAttemptNumber = _cursor.getInt(_cursorIndexOfAttemptNumber);
            final int _tmpMaxAttemptsConfigured;
            _tmpMaxAttemptsConfigured = _cursor.getInt(_cursorIndexOfMaxAttemptsConfigured);
            final String _tmpRawRequestHex;
            if (_cursor.isNull(_cursorIndexOfRawRequestHex)) {
              _tmpRawRequestHex = null;
            } else {
              _tmpRawRequestHex = _cursor.getString(_cursorIndexOfRawRequestHex);
            }
            final String _tmpRawResponseHex;
            if (_cursor.isNull(_cursorIndexOfRawResponseHex)) {
              _tmpRawResponseHex = null;
            } else {
              _tmpRawResponseHex = _cursor.getString(_cursorIndexOfRawResponseHex);
            }
            final String _tmpDecodedValue;
            if (_cursor.isNull(_cursorIndexOfDecodedValue)) {
              _tmpDecodedValue = null;
            } else {
              _tmpDecodedValue = _cursor.getString(_cursorIndexOfDecodedValue);
            }
            _item = new OperationEntity(_tmpId,_tmpSessionId,_tmpSequenceNo,_tmpOpType,_tmpObisCode,_tmpClassId,_tmpAttributeOrMethod,_tmpStatus,_tmpStartTime,_tmpEndTime,_tmpErrorMessage,_tmpAttemptNumber,_tmpMaxAttemptsConfigured,_tmpRawRequestHex,_tmpRawResponseHex,_tmpDecodedValue);
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
  public OperationEntity getById(final long id) {
    final String _sql = "SELECT * FROM operations WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
      final int _cursorIndexOfSequenceNo = CursorUtil.getColumnIndexOrThrow(_cursor, "sequenceNo");
      final int _cursorIndexOfOpType = CursorUtil.getColumnIndexOrThrow(_cursor, "opType");
      final int _cursorIndexOfObisCode = CursorUtil.getColumnIndexOrThrow(_cursor, "obisCode");
      final int _cursorIndexOfClassId = CursorUtil.getColumnIndexOrThrow(_cursor, "classId");
      final int _cursorIndexOfAttributeOrMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "attributeOrMethod");
      final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
      final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
      final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
      final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
      final int _cursorIndexOfAttemptNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "attemptNumber");
      final int _cursorIndexOfMaxAttemptsConfigured = CursorUtil.getColumnIndexOrThrow(_cursor, "maxAttemptsConfigured");
      final int _cursorIndexOfRawRequestHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawRequestHex");
      final int _cursorIndexOfRawResponseHex = CursorUtil.getColumnIndexOrThrow(_cursor, "rawResponseHex");
      final int _cursorIndexOfDecodedValue = CursorUtil.getColumnIndexOrThrow(_cursor, "decodedValue");
      final OperationEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final long _tmpSessionId;
        _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
        final int _tmpSequenceNo;
        _tmpSequenceNo = _cursor.getInt(_cursorIndexOfSequenceNo);
        final String _tmpOpType;
        _tmpOpType = _cursor.getString(_cursorIndexOfOpType);
        final String _tmpObisCode;
        _tmpObisCode = _cursor.getString(_cursorIndexOfObisCode);
        final int _tmpClassId;
        _tmpClassId = _cursor.getInt(_cursorIndexOfClassId);
        final int _tmpAttributeOrMethod;
        _tmpAttributeOrMethod = _cursor.getInt(_cursorIndexOfAttributeOrMethod);
        final String _tmpStatus;
        _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
        final long _tmpStartTime;
        _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
        final long _tmpEndTime;
        _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
        final String _tmpErrorMessage;
        if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
          _tmpErrorMessage = null;
        } else {
          _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
        }
        final int _tmpAttemptNumber;
        _tmpAttemptNumber = _cursor.getInt(_cursorIndexOfAttemptNumber);
        final int _tmpMaxAttemptsConfigured;
        _tmpMaxAttemptsConfigured = _cursor.getInt(_cursorIndexOfMaxAttemptsConfigured);
        final String _tmpRawRequestHex;
        if (_cursor.isNull(_cursorIndexOfRawRequestHex)) {
          _tmpRawRequestHex = null;
        } else {
          _tmpRawRequestHex = _cursor.getString(_cursorIndexOfRawRequestHex);
        }
        final String _tmpRawResponseHex;
        if (_cursor.isNull(_cursorIndexOfRawResponseHex)) {
          _tmpRawResponseHex = null;
        } else {
          _tmpRawResponseHex = _cursor.getString(_cursorIndexOfRawResponseHex);
        }
        final String _tmpDecodedValue;
        if (_cursor.isNull(_cursorIndexOfDecodedValue)) {
          _tmpDecodedValue = null;
        } else {
          _tmpDecodedValue = _cursor.getString(_cursorIndexOfDecodedValue);
        }
        _result = new OperationEntity(_tmpId,_tmpSessionId,_tmpSequenceNo,_tmpOpType,_tmpObisCode,_tmpClassId,_tmpAttributeOrMethod,_tmpStatus,_tmpStartTime,_tmpEndTime,_tmpErrorMessage,_tmpAttemptNumber,_tmpMaxAttemptsConfigured,_tmpRawRequestHex,_tmpRawResponseHex,_tmpDecodedValue);
      } else {
        _result = null;
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
