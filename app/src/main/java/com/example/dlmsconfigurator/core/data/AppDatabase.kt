package com.example.dlmsconfigurator.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, secureKeyStore).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, secureKeyStore: SecureKeyStore): AppDatabase {
            System.loadLibrary("sqlcipher")
            
            val passphrase = secureKeyStore.getDatabasePassphrase()
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dlms_configurator.db"
            )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
