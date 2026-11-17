package br.com.smsvivo.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ContactEntity::class, ContactListEntity::class, ContactListMembershipEntity::class, CampaignEntity::class, MessageTemplateEntity::class, QueueItemEntity::class, SendAttemptEntity::class, SettingEntity::class, DiagnosticEventEntity::class, SmsPartCallbackEntity::class], version = 3, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun queue(): QueueDao
    abstract fun diagnostics(): DiagnosticDao
    abstract fun lists(): ListDao
    abstract fun templates(): TemplateDao
    abstract fun campaigns(): CampaignDao
    abstract fun smsPartCallbacks(): SmsPartCallbackDao

    fun recordPartCallback(callback: SmsPartCallbackEntity): QueueItemEntity? {
        beginTransaction()
        return try {
            val result = if (smsPartCallbacks().insert(callback) == -1L) null else {
                when (callback.phase) {
                    SmsResultPhase.SENT -> queue().markSentPart(callback.requestId, callback.createdAt)
                    SmsResultPhase.DELIVERED -> queue().markDeliveredPart(callback.requestId, callback.createdAt)
                }
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
        fun get(context: Context): SmsDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, SmsDatabase::class.java, "sms-vivo.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it } }
    }
}

object SmsResultPhase { const val SENT = "SENT"; const val DELIVERED = "DELIVERED" }
