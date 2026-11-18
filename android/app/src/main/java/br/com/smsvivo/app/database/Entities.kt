package br.com.smsvivo.app.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object QueueStates {
    const val PENDING = "PENDING"; const val PROCESSING = "PROCESSING"; const val SENT = "SENT"; const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"; const val UNKNOWN = "UNKNOWN"; const val CANCELLED = "CANCELLED"; const val SKIPPED = "SKIPPED"
}

@Entity(tableName = "contacts", indices = [Index(value = ["normalizedPhone"], unique = true)])
data class ContactEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String, val normalizedPhone: String, val originalPhone: String? = null, val phoneClassification: String? = null, val correctedAt: Long? = null, val company: String? = null, val city: String? = null, val state: String? = null, val customFieldsJson: String? = null, val authorized: Boolean = false, val unsubscribed: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "contact_lists")
data class ContactListEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val description: String? = null, val parentListId: Long? = null, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "contact_list_memberships", primaryKeys = ["listId", "contactId"], indices = [Index(value = ["contactId"])])
data class ContactListMembershipEntity(val listId: Long, val contactId: Long, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "campaigns")
data class CampaignEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val listId: Long? = null, val templateId: Long? = null, val subscriptionId: Int, val intervalMs: Long = 0, val batchSize: Int? = null, val state: String = QueueStates.PENDING, val createdAt: Long = System.currentTimeMillis(), val startedAt: Long? = null, val finishedAt: Long? = null)

@Entity(tableName = "message_templates")
data class MessageTemplateEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val body: String, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "queue_items", indices = [Index(value = ["requestId"], unique = true), Index(value = ["state"]), Index(value = ["campaignId"])])
data class QueueItemEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val requestId: String, val campaignId: Long? = null, val contactId: Long? = null, val destination: String, val body: String, val subscriptionId: Int, val state: String = QueueStates.PENDING, val createdAt: Long = System.currentTimeMillis(), val processingAt: Long? = null, val sentAt: Long? = null, val deliveredAt: Long? = null, val failureCode: Int? = null, val failureMessage: String? = null, val attemptCount: Int = 0, val partsExpected: Int = 1, val sentParts: Int = 0, val deliveredParts: Int = 0)

@Entity(tableName = "sms_part_callbacks", primaryKeys = ["requestId", "partIndex", "phase"], indices = [Index(value = ["requestId"])])
data class SmsPartCallbackEntity(val requestId: String, val partIndex: Int, val phase: String, val resultCode: Int, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "send_attempts", indices = [Index(value = ["queueItemId"])])
data class SendAttemptEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val queueItemId: Long, val attemptNumber: Int, val startedAt: Long, val finishedAt: Long? = null, val state: String, val resultCode: Int? = null, val errorMessage: String? = null)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "diagnostic_events", indices = [Index(value = ["createdAt"])])
data class DiagnosticEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val category: String, val code: Int? = null, val message: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "authorization_events", indices = [Index(value = ["contactId"]), Index(value = ["createdAt"])])
data class AuthorizationEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val contactId: Long, val authorized: Boolean, val source: String, val createdAt: Long = System.currentTimeMillis())
