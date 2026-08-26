package com.example.dlmsconfigurator.core.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SessionEntity::class,
        OperationEntity::class,
        AuthEventEntity::class,
        DeviceEntity::class,
        AssociationObjectEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun operationDao(): OperationDao
    abstract fun authEventDao(): AuthEventDao
    abstract fun deviceDao(): DeviceDao
    abstract fun associationObjectDao(): AssociationObjectDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "dlms_configurator.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 1→2: Adds the `devices` and `association_objects` tables
         * without touching the existing sessions / operations / auth_events tables.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `devices` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `commSettingsJson` TEXT NOT NULL,
                        `clientAddress` INTEGER NOT NULL DEFAULT 16,
                        `serverAddress` INTEGER NOT NULL DEFAULT 1,
                        `security` TEXT NOT NULL DEFAULT 'none',
                        `interfaceType` TEXT NOT NULL DEFAULT 'HDLC',
                        `logicalNameReferencing` INTEGER NOT NULL DEFAULT 1,
                        `passwordKeyRef` TEXT,
                        `systemTitleKeyRef` TEXT,
                        `authKeyRef` TEXT,
                        `encKeyRef` TEXT,
                        `ciphering` INTEGER NOT NULL DEFAULT 0,
                        `invocationCounterObis` TEXT,
                        `useInvocationCounter` INTEGER NOT NULL DEFAULT 0,
                        `lastConnectedAt` INTEGER,
                        `lastKnownMeterSerial` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `association_objects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `deviceId` INTEGER NOT NULL,
                        `classId` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL DEFAULT 0,
                        `obisCode` TEXT NOT NULL,
                        `className` TEXT NOT NULL DEFAULT '',
                        `attrAccessJson` TEXT NOT NULL DEFAULT '{}',
                        `methodAccessJson` TEXT NOT NULL DEFAULT '{}',
                        `cachedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`deviceId`) REFERENCES `devices`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_association_objects_deviceId` ON `association_objects`(`deviceId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `authenticationRole` TEXT NOT NULL DEFAULT 'PC'")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `addressType` TEXT NOT NULL DEFAULT 'Default'")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `logicalServer` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `physicalServer` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `securitySuite` TEXT NOT NULL DEFAULT 'Suite0'")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `invocationCounterInitial` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `retryIntervalMs` INTEGER NOT NULL DEFAULT 1000")
            }
        }

        fun getInstance(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, secureKeyStore).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            System.loadLibrary("sqlcipher")

            val passphrase = secureKeyStore.getDatabasePassphrase()
            val passphraseBytes = hexToBytes(passphrase)
            val factory = SupportOpenHelperFactory(passphraseBytes)

            return tryBuildDatabase(context, factory)
                ?: run {
                    Log.w(TAG, "DB open failed — possible passphrase mismatch. Deleting stale DB and rebuilding.")
                    deleteDatabase(context)
                    tryBuildDatabase(context, factory)
                        ?: throw IllegalStateException("Failed to open database even after deleting stale file.")
                }
        }

        private fun tryBuildDatabase(
            context: Context,
            factory: SupportOpenHelperFactory
        ): AppDatabase? {
            return try {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                // Force the DB connection open NOW so SQLCipher validates the key immediately
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                Log.e(TAG, "DB open error: ${e.message}", e)
                null
            }
        }

        private fun deleteDatabase(context: Context) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm"))
                    .forEach { if (it.exists()) it.delete() }
                Log.i(TAG, "Stale database files deleted.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete stale DB files: ${e.message}", e)
            }
        }

        /** Decode a hex string (e.g. "aabbcc…") to its raw byte array. */
        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.replace(" ", "").replace(":", "")
            if (clean.isEmpty()) return ByteArray(0)
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
