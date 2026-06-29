package com.leobigott.cercamessenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DtnMessageEntity::class,
        PeerEntity::class,
        ContactEntity::class,
        AckEntity::class,
        PredictionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CercaDatabase : RoomDatabase() {
    abstract fun messageDao(): DtnMessageDao
    abstract fun peerDao(): PeerDao
    abstract fun contactDao(): ContactDao
    abstract fun ackDao(): AckDao
    abstract fun predictionDao(): PredictionDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE dtn_messages ADD COLUMN destinationScope TEXT NOT NULL DEFAULT 'DIRECT_CONTACT'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE dtn_messages ADD COLUMN receivedAt INTEGER NOT NULL DEFAULT 0"
                )

                /*
                 * Para mensajes antiguos, usamos timestamp como receivedAt inicial.
                 * Así no se rompen las conversaciones existentes.
                 */
                database.execSQL(
                    "UPDATE dtn_messages SET receivedAt = timestamp WHERE receivedAt = 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: CercaDatabase? = null

        fun getInstance(context: Context): CercaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CercaDatabase::class.java,
                    "cerca_messenger.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
