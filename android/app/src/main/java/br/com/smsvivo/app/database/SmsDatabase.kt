package br.com.smsvivo.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ContactEntity::class, ContactListEntity::class, ContactListMembershipEntity::class, CampaignEntity::class, MessageTemplateEntity::class, QueueItemEntity::class, SendAttemptEntity::class, SettingEntity::class, DiagnosticEventEntity::class], version = 2, exportSchema = true)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun queue(): QueueDao
    abstract fun diagnostics(): DiagnosticDao
    abstract fun lists(): ListDao
    abstract fun templates(): TemplateDao
    abstract fun campaigns(): CampaignDao
    companion object {
        @Volatile private var instance: SmsDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(database: SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS contact_list_memberships (listId INTEGER NOT NULL, contactId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(listId, contactId))"); database.execSQL("CREATE INDEX IF NOT EXISTS index_contact_list_memberships_contactId ON contact_list_memberships(contactId)") } }
        fun get(context: Context): SmsDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, SmsDatabase::class.java, "sms-vivo.db").addMigrations(MIGRATION_1_2).build().also { instance = it } }
    }
}
