package com.example.dlmsconfigurator.core.data;

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
public final class AppDatabase_Impl extends AppDatabase {
  private volatile SessionDao _sessionDao;

  private volatile OperationDao _operationDao;

  private volatile AuthEventDao _authEventDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `meterSerial` TEXT, `jsonSourceFileName` TEXT NOT NULL, `detailedLogging` INTEGER NOT NULL, `status` TEXT NOT NULL, `connectionOverrideUsed` INTEGER NOT NULL, `syncedAt` INTEGER, `remoteId` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `operations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `sequenceNo` INTEGER NOT NULL, `opType` TEXT NOT NULL, `obisCode` TEXT NOT NULL, `classId` INTEGER NOT NULL, `attributeOrMethod` INTEGER NOT NULL, `status` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `errorMessage` TEXT, `attemptNumber` INTEGER NOT NULL, `maxAttemptsConfigured` INTEGER NOT NULL, `rawRequestHex` TEXT, `rawResponseHex` TEXT, `decodedValue` TEXT, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_sessionId` ON `operations` (`sessionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `auth_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `result` TEXT NOT NULL, `authMethod` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7e3f0c011adddc866b890ce62467ced4')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sessions`");
        db.execSQL("DROP TABLE IF EXISTS `operations`");
        db.execSQL("DROP TABLE IF EXISTS `auth_events`");
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
        db.execSQL("PRAGMA foreign_keys = ON");
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
        final HashMap<String, TableInfo.Column> _columnsSessions = new HashMap<String, TableInfo.Column>(10);
        _columnsSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("startTime", new TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("endTime", new TableInfo.Column("endTime", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("meterSerial", new TableInfo.Column("meterSerial", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("jsonSourceFileName", new TableInfo.Column("jsonSourceFileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("detailedLogging", new TableInfo.Column("detailedLogging", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("connectionOverrideUsed", new TableInfo.Column("connectionOverrideUsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("syncedAt", new TableInfo.Column("syncedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("remoteId", new TableInfo.Column("remoteId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSessions = new TableInfo("sessions", _columnsSessions, _foreignKeysSessions, _indicesSessions);
        final TableInfo _existingSessions = TableInfo.read(db, "sessions");
        if (!_infoSessions.equals(_existingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "sessions(com.example.dlmsconfigurator.core.data.SessionEntity).\n"
                  + " Expected:\n" + _infoSessions + "\n"
                  + " Found:\n" + _existingSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsOperations = new HashMap<String, TableInfo.Column>(16);
        _columnsOperations.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("sequenceNo", new TableInfo.Column("sequenceNo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("opType", new TableInfo.Column("opType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("obisCode", new TableInfo.Column("obisCode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("classId", new TableInfo.Column("classId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("attributeOrMethod", new TableInfo.Column("attributeOrMethod", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("startTime", new TableInfo.Column("startTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("endTime", new TableInfo.Column("endTime", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("errorMessage", new TableInfo.Column("errorMessage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("attemptNumber", new TableInfo.Column("attemptNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("maxAttemptsConfigured", new TableInfo.Column("maxAttemptsConfigured", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("rawRequestHex", new TableInfo.Column("rawRequestHex", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("rawResponseHex", new TableInfo.Column("rawResponseHex", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOperations.put("decodedValue", new TableInfo.Column("decodedValue", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOperations = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysOperations.add(new TableInfo.ForeignKey("sessions", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesOperations = new HashSet<TableInfo.Index>(1);
        _indicesOperations.add(new TableInfo.Index("index_operations_sessionId", false, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        final TableInfo _infoOperations = new TableInfo("operations", _columnsOperations, _foreignKeysOperations, _indicesOperations);
        final TableInfo _existingOperations = TableInfo.read(db, "operations");
        if (!_infoOperations.equals(_existingOperations)) {
          return new RoomOpenHelper.ValidationResult(false, "operations(com.example.dlmsconfigurator.core.data.OperationEntity).\n"
                  + " Expected:\n" + _infoOperations + "\n"
                  + " Found:\n" + _existingOperations);
        }
        final HashMap<String, TableInfo.Column> _columnsAuthEvents = new HashMap<String, TableInfo.Column>(4);
        _columnsAuthEvents.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAuthEvents.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAuthEvents.put("result", new TableInfo.Column("result", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAuthEvents.put("authMethod", new TableInfo.Column("authMethod", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAuthEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAuthEvents = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAuthEvents = new TableInfo("auth_events", _columnsAuthEvents, _foreignKeysAuthEvents, _indicesAuthEvents);
        final TableInfo _existingAuthEvents = TableInfo.read(db, "auth_events");
        if (!_infoAuthEvents.equals(_existingAuthEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "auth_events(com.example.dlmsconfigurator.core.data.AuthEventEntity).\n"
                  + " Expected:\n" + _infoAuthEvents + "\n"
                  + " Found:\n" + _existingAuthEvents);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "7e3f0c011adddc866b890ce62467ced4", "75052a262f223d32589ea8766353e9ad");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions","operations","auth_events");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `sessions`");
      _db.execSQL("DELETE FROM `operations`");
      _db.execSQL("DELETE FROM `auth_events`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
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
    _typeConvertersMap.put(SessionDao.class, SessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(OperationDao.class, OperationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AuthEventDao.class, AuthEventDao_Impl.getRequiredConverters());
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
  public SessionDao sessionDao() {
    if (_sessionDao != null) {
      return _sessionDao;
    } else {
      synchronized(this) {
        if(_sessionDao == null) {
          _sessionDao = new SessionDao_Impl(this);
        }
        return _sessionDao;
      }
    }
  }

  @Override
  public OperationDao operationDao() {
    if (_operationDao != null) {
      return _operationDao;
    } else {
      synchronized(this) {
        if(_operationDao == null) {
          _operationDao = new OperationDao_Impl(this);
        }
        return _operationDao;
      }
    }
  }

  @Override
  public AuthEventDao authEventDao() {
    if (_authEventDao != null) {
      return _authEventDao;
    } else {
      synchronized(this) {
        if(_authEventDao == null) {
          _authEventDao = new AuthEventDao_Impl(this);
        }
        return _authEventDao;
      }
    }
  }
}
