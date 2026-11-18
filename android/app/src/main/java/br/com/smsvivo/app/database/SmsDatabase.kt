package br.com.smsvivo.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ContactEntity::class, ContactListEntity::class, ContactListMembershipEntity::class, CampaignEntity::class, MessageTemplateEntity::class, QueueItemEntity::class, SendAttemptEntity::class, SettingEntity::class, DiagnosticEventEntity::class, SmsPartCallbackEntity::class, AuthorizationEventEntity::class], version = 6, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun queue(): QueueDao
    abstract fun diagnostics(): DiagnosticDao
    abstract fun lists(): ListDao
    abstract fun templates(): TemplateDao
    abstract fun campaigns(): CampaignDao
    abstract fun smsPartCallbacks(): SmsPartCallbackDao
    abstract fun authorizationEvents(): AuthorizationEventDao
    abstract fun settings(): SettingDao

    fun recordPartCallback(callback: SmsPartCallbackEntity): QueueItemEntity? {
        beginTransaction()
        return try {
            val result = if (smsPartCallbacks().insert(callback) == -1L) null else {
                when (callback.phase) {
                    SmsResultPhase.SENT -> queue().markSentPart(callback.requestId, callback.createdAt, callback.resultCode)
                    SmsResultPhase.DELIVERED -> queue().markDeliveredPart(callback.requestId, callback.createdAt, callback.resultCode)
                }
                queue().byRequestId(callback.requestId)
            }
            setTransactionSuccessful()
            result
        } finally { endTransaction() }
    }

    fun recordPartFailure(callback: SmsPartCallbackEntity): QueueItemEntity? {
        beginTransaction()
        return try {
            val result = if (smsPartCallbacks().insert(callback) == -1L) null else {
                queue().markResult(callback.requestId, QueueStates.FAILED, callback.resultCode, "Código Android ${callback.resultCode}", callback.createdAt)
                queue().byRequestId(callback.requestId)
            }
            setTransactionSuccessful()
            result
        } finally { endTransaction() }
    }

    companion object {
        @Volatile private var instance: SmsDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS contact_list_memberships (listId INTEGER NOT NULL, contactId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(listId, contactId))"); database.execSQL("CREATE INDEX IF NOT EXISTS index_contact_list_memberships_contactId ON contact_list_memberships(contactId)") } }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS sms_part_callbacks (requestId TEXT NOT NULL, partIndex INTEGER NOT NULL, phase TEXT NOT NULL, resultCode INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(requestId, partIndex, phase))"); database.execSQL("CREATE INDEX IF NOT EXISTS index_sms_part_callbacks_requestId ON sms_part_callbacks(requestId)") } }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE contacts ADD COLUMN originalPhone TEXT"); database.execSQL("ALTER TABLE contacts ADD COLUMN phoneClassification TEXT"); database.execSQL("ALTER TABLE contacts ADD COLUMN correctedAt INTEGER"); database.execSQL("ALTER TABLE contact_lists ADD COLUMN parentListId INTEGER") } }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS authorization_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contactId INTEGER NOT NULL, authorized INTEGER NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL)"); database.execSQL("CREATE INDEX IF NOT EXISTS index_authorization_events_contactId ON authorization_events(contactId)"); database.execSQL("CREATE INDEX IF NOT EXISTS index_authorization_events_createdAt ON authorization_events(createdAt)") } }
        private val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("ALTER TABLE queue_items ADD COLUMN requestIntervalMs INTEGER"); database.execSQL("ALTER TABLE queue_items ADD COLUMN smsRequestedAt INTEGER"); database.execSQL("ALTER TABLE queue_items ADD COLUMN lastCallbackAt INTEGER"); database.execSQL("ALTER TABLE queue_items ADD COLUMN lastCallbackCode INTEGER"); database.execSQL("ALTER TABLE queue_items ADD COLUMN unknownAt INTEGER") } }
        fun get(context: Context): SmsDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, SmsDatabase::class.java, "sms-vivo.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it } }
    }
}

object SmsResultPhase { const val SENT = "SENT"; const val DELIVERED = "DELIVERED"; const val SENT_FAILED = "SENT_FAILED"; const val DELIVERY_FAILED = "DELIVERY_FAILED" }
