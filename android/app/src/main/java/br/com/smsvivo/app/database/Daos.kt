package br.com.smsvivo.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(contact: ContactEntity): Long
    @Update fun update(contact: ContactEntity)
    @Query("SELECT * FROM contacts WHERE normalizedPhone = :phone LIMIT 1") fun byNormalizedPhone(phone: String): ContactEntity?
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE") fun all(): List<ContactEntity>
    @Query("SELECT * FROM contacts WHERE id = :id") fun find(id: Long): ContactEntity?
    @Query("DELETE FROM contacts WHERE id IN (:ids)") fun delete(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM contacts") fun count(): Int
}

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(item: QueueItemEntity): Long
    @Update fun update(item: QueueItemEntity)
    @Query("SELECT * FROM queue_items WHERE requestId = :requestId LIMIT 1") fun byRequestId(requestId: String): QueueItemEntity?
    @Query("SELECT * FROM queue_items WHERE state = :state ORDER BY createdAt, id") fun byState(state: String): List<QueueItemEntity>
    @Query("SELECT * FROM queue_items WHERE state = 'PENDING' ORDER BY createdAt, id") fun pending(): List<QueueItemEntity>
    @Query("UPDATE queue_items SET state = 'PROCESSING', processingAt = :now, attemptCount = attemptCount + 1 WHERE id = :id AND state = 'PENDING'") fun claimPending(id: Long, now: Long): Int
    @Transaction fun claimNextPending(now: Long): QueueItemEntity? { val item = pending().firstOrNull() ?: return null; return if (claimPending(item.id, now) == 1) byRequestId(item.requestId) else null }
    @Query("SELECT COUNT(*) FROM queue_items WHERE state = :state") fun count(state: String): Int
    @Query("SELECT COUNT(*) FROM queue_items") fun total(): Int
    @Query("SELECT MIN(createdAt) FROM queue_items") fun firstCreatedAt(): Long?
    @Query("SELECT MAX(COALESCE(deliveredAt, sentAt, createdAt)) FROM queue_items") fun lastActivityAt(): Long?
    @Query("SELECT * FROM queue_items ORDER BY createdAt DESC LIMIT :limit") fun recent(limit: Int): List<QueueItemEntity>
    @Query("SELECT * FROM queue_items ORDER BY createdAt DESC") fun allHistory(): List<QueueItemEntity>
    @Query("UPDATE queue_items SET state = 'UNKNOWN', failureMessage = 'Processo interrompido antes da confirmação; revisão manual necessária' WHERE state = 'PROCESSING' AND processingAt IS NOT NULL AND processingAt < :cutoff") fun recoverStaleProcessing(cutoff: Long): Int
    @Query("UPDATE queue_items SET state = :state, failureCode = :code, failureMessage = :message, sentAt = :sentAt WHERE requestId = :requestId") fun markResult(requestId: String, state: String, code: Int?, message: String?, sentAt: Long?)
    @Query("UPDATE queue_items SET sentParts = sentParts + 1, state = CASE WHEN sentParts + 1 >= partsExpected THEN 'SENT' ELSE state END, sentAt = CASE WHEN sentParts + 1 >= partsExpected THEN :at ELSE sentAt END WHERE requestId = :requestId AND state NOT IN ('FAILED', 'UNKNOWN', 'CANCELLED', 'DELIVERED')") fun markSentPart(requestId: String, at: Long): Int
    @Query("UPDATE queue_items SET deliveredParts = deliveredParts + 1, state = CASE WHEN deliveredParts + 1 >= partsExpected AND sentParts >= partsExpected THEN 'DELIVERED' ELSE state END, deliveredAt = CASE WHEN deliveredParts + 1 >= partsExpected AND sentParts >= partsExpected THEN :at ELSE deliveredAt END WHERE requestId = :requestId AND state NOT IN ('FAILED', 'UNKNOWN', 'CANCELLED')") fun markDeliveredPart(requestId: String, at: Long): Int
    @Query("UPDATE queue_items SET state = 'DELIVERED', deliveredAt = :at WHERE requestId = :requestId") fun markDelivered(requestId: String, at: Long)
    @Query("UPDATE queue_items SET state = 'CANCELLED', failureMessage = 'Cancelado pelo usuário' WHERE state = 'PENDING'") fun cancelPending(): Int
}

@Dao
interface SmsPartCallbackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insert(callback: SmsPartCallbackEntity): Long
}

@Dao
interface DiagnosticDao { @Insert fun insert(event: DiagnosticEventEntity): Long; @Query("SELECT * FROM diagnostic_events ORDER BY createdAt DESC LIMIT :limit") fun recent(limit: Int): List<DiagnosticEventEntity> }

@Dao
interface ListDao {
    @Insert fun insert(list: ContactListEntity): Long
    @Update fun update(list: ContactListEntity)
    @Query("SELECT * FROM contact_lists ORDER BY name COLLATE NOCASE") fun all(): List<ContactListEntity>
    @Query("SELECT * FROM contact_lists WHERE id = :id LIMIT 1") fun find(id: Long): ContactListEntity?
    @Query("DELETE FROM contact_lists WHERE id = :id") fun delete(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun addMember(member: ContactListMembershipEntity)
    @Query("DELETE FROM contact_list_memberships WHERE listId = :listId AND contactId IN (:contactIds)") fun removeMembers(listId: Long, contactIds: List<Long>)
    @Query("DELETE FROM contact_list_memberships WHERE listId = :listId") fun removeAllMembers(listId: Long)
    @Transaction fun replaceMembers(list: ContactListEntity, contactIds: List<Long>) { update(list); removeAllMembers(list.id); contactIds.forEach { addMember(ContactListMembershipEntity(list.id, it)) } }
    @Query("SELECT contactId FROM contact_list_memberships WHERE listId = :listId") fun memberIds(listId: Long): List<Long>
}

@Dao
interface TemplateDao {
    @Insert fun insert(template: MessageTemplateEntity): Long
    @Update fun update(template: MessageTemplateEntity)
    @Query("SELECT * FROM message_templates ORDER BY name COLLATE NOCASE") fun all(): List<MessageTemplateEntity>
    @Query("SELECT * FROM message_templates WHERE id = :id") fun find(id: Long): MessageTemplateEntity?
    @Query("DELETE FROM message_templates WHERE id = :id") fun delete(id: Long)
}

@Dao
interface CampaignDao {
    @Insert fun insert(campaign: CampaignEntity): Long
    @Update fun update(campaign: CampaignEntity)
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC") fun all(): List<CampaignEntity>
    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1") fun find(id: Long): CampaignEntity?
}
