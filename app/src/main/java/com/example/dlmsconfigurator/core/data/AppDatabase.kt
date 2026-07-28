package com.example.dlmsconfigurator.core.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [SessionEntity::class, OperationEntity::class, AuthEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun operationDao(): OperationDao
    abstract fun authEventDao(): AuthEventDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "dlms_configurator.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, secureKeyStore).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            System.loadLibrary("sqlcipher")

            // The passphrase is stored as a hex string. Decode it to raw bytes so that
            // SQLCipher uses the actual 256-bit key, not its UTF-8 byte representation.
            val passphrase = secureKeyStore.getDatabasePassphrase()
            val passphraseBytes = hexToBytes(passphrase)
            val factory = SupportOpenHelperFactory(passphraseBytes)

            return tryBuildDatabase(context, factory)
                ?: run {
                    // Passphrase mismatch (e.g. EncryptedSharedPreferences was reset and a new
                    // passphrase was generated, but the old DB still exists on disk).
                    // The stored data is unrecoverable, so delete the stale DB and start fresh.
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
                    .fallbackToDestructiveMigration()
                    .build()

                // Force the DB connection open NOW so SQLCipher validates the key immediately
                // rather than deferring to the first query (which would crash later).
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                Log.e(TAG, "DB open error: ${e.message}", e)
                null
            }
        }

        private fun deleteDatabase(context: Context) {
            try {
                // Delete the main DB file and its WAL/SHM companions
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

