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
    @Query("SELECT * FROM contacts WHERE originalPhone = :phone LIMIT 1") fun byOriginalPhone(phone: String): ContactEntity?
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE") fun all(): List<ContactEntity>
    @Query("SELECT * FROM contacts WHERE id = :id") fun find(id: Long): ContactEntity?
    @Query("SELECT * FROM contacts WHERE id IN (:ids)") fun findByIds(ids: List<Long>): List<ContactEntity>
    @Query("DELETE FROM contacts WHERE id IN (:ids)") fun delete(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM contacts") fun count(): Int
    @Query("UPDATE contacts SET authorized = :authorized, updatedAt = :now WHERE id IN (:ids) AND unsubscribed = 0") fun updateAuthorization(ids: List<Long>, authorized: Boolean, now: Long): Int
    @Query("UPDATE contacts SET phone = :phone, normalizedPhone = :normalizedPhone, phoneClassification = :phoneClassification, correctedAt = :correctedAt, updatedAt = :now WHERE id = :id") fun updateCorrectedPhone(id: Long, phone: String, normalizedPhone: String, phoneClassification: String, correctedAt: Long, now: Long): Int
    @Query("SELECT DISTINCT contactId FROM queue_items WHERE contactId IN (:ids) AND state IN ('PENDING', 'PROCESSING')") fun activeQueueContactIds(ids: List<Long>): List<Long>
    @Query("SELECT * FROM contacts WHERE (:query = '' OR lower(name) LIKE '%' || lower(:query) || '%' OR lower(phone) LIKE '%' || lower(:query) || '%' OR lower(normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(company, '')) LIKE '%' || lower(:query) || '%') ORDER BY name COLLATE NOCASE, id LIMIT :limit OFFSET :offset") fun page(query: String, limit: Int, offset: Int): List<ContactEntity>
    @Query("SELECT COUNT(*) FROM contacts WHERE (:query = '' OR lower(name) LIKE '%' || lower(:query) || '%' OR lower(phone) LIKE '%' || lower(:query) || '%' OR lower(normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(company, '')) LIKE '%' || lower(:query) || '%')") fun count(query: String): Int
    @Query("SELECT c.* FROM contacts c INNER JOIN contact_list_memberships m ON m.contactId = c.id WHERE m.listId = :listId AND (:query = '' OR lower(c.name) LIKE '%' || lower(:query) || '%' OR lower(c.phone) LIKE '%' || lower(:query) || '%' OR lower(c.normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(c.company, '')) LIKE '%' || lower(:query) || '%') ORDER BY c.name COLLATE NOCASE, c.id LIMIT :limit OFFSET :offset") fun pageByList(listId: Long, query: String, limit: Int, offset: Int): List<ContactEntity>
    @Query("SELECT COUNT(*) FROM contacts c INNER JOIN contact_list_memberships m ON m.contactId = c.id WHERE m.listId = :listId AND (:query = '' OR lower(c.name) LIKE '%' || lower(:query) || '%' OR lower(c.phone) LIKE '%' || lower(:query) || '%' OR lower(c.normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(c.company, '')) LIKE '%' || lower(:query) || '%')") fun countByList(listId: Long, query: String): Int
    @Query("SELECT COUNT(*) FROM contacts WHERE authorized = 1 AND unsubscribed = 0 AND phoneClassification = 'MOBILE_VALID'") fun countEligible(): Int
    @Query("SELECT COUNT(*) FROM contacts c INNER JOIN contact_list_memberships m ON m.contactId = c.id WHERE m.listId = :listId AND c.authorized = 1 AND c.unsubscribed = 0 AND c.phoneClassification = 'MOBILE_VALID'") fun countEligibleByList(listId: Long): Int
    @Query("SELECT c.* FROM contacts c INNER JOIN contact_list_memberships m ON m.contactId = c.id WHERE m.listId = :listId AND c.authorized = 1 AND c.unsubscribed = 0 AND c.phoneClassification = 'MOBILE_VALID' AND (:query = '' OR lower(c.name) LIKE '%' || lower(:query) || '%' OR lower(c.phone) LIKE '%' || lower(:query) || '%' OR lower(c.normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(c.company, '')) LIKE '%' || lower(:query) || '%') ORDER BY c.name COLLATE NOCASE, c.id LIMIT :limit OFFSET :offset") fun eligiblePageByList(listId: Long, query: String, limit: Int, offset: Int): List<ContactEntity>
    @Query("SELECT * FROM contacts c WHERE c.authorized = 1 AND c.unsubscribed = 0 AND c.phoneClassification = 'MOBILE_VALID' AND (:query = '' OR lower(c.name) LIKE '%' || lower(:query) || '%' OR lower(c.phone) LIKE '%' || lower(:query) || '%' OR lower(c.normalizedPhone) LIKE '%' || lower(:query) || '%' OR lower(COALESCE(c.company, '')) LIKE '%' || lower(:query) || '%') ORDER BY c.name COLLATE NOCASE, c.id LIMIT :limit OFFSET :offset") fun eligiblePage(query: String, limit: Int, offset: Int): List<ContactEntity>
}

@Dao
interface AuthorizationEventDao { @Insert fun insertAll(events: List<AuthorizationEventEntity>): List<Long> }

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
    @Query("UPDATE queue_items SET state = 'UNKNOWN', unknownAt = :at, failureMessage = 'Processo interrompido antes da confirmação; revisão manual necessária' WHERE state = 'PROCESSING' AND processingAt IS NOT NULL AND processingAt < :cutoff") fun recoverStaleProcessing(cutoff: Long, at: Long = System.currentTimeMillis()): Int
    @Query("UPDATE queue_items SET smsRequestedAt = :requestedAt, requestIntervalMs = :intervalMs, lastCallbackAt = NULL, lastCallbackCode = NULL WHERE requestId = :requestId") fun markSmsRequested(requestId: String, requestedAt: Long, intervalMs: Long?)
    @Query("UPDATE queue_items SET state = :state, failureCode = :code, failureMessage = :message, sentAt = :sentAt, lastCallbackAt = :sentAt, lastCallbackCode = :code WHERE requestId = :requestId AND state NOT IN ('UNKNOWN', 'CANCELLED', 'DELIVERED')") fun markResult(requestId: String, state: String, code: Int?, message: String?, sentAt: Long?)
    @Query("UPDATE queue_items SET sentParts = sentParts + 1, lastCallbackAt = :at, lastCallbackCode = :code, state = CASE WHEN sentParts + 1 >= partsExpected THEN 'SENT' ELSE state END, sentAt = CASE WHEN sentParts + 1 >= partsExpected THEN :at ELSE sentAt END WHERE requestId = :requestId AND state NOT IN ('FAILED', 'UNKNOWN', 'CANCELLED', 'DELIVERED')") fun markSentPart(requestId: String, at: Long, code: Int): Int
    @Query("UPDATE queue_items SET deliveredParts = deliveredParts + 1, lastCallbackAt = :at, lastCallbackCode = :code, state = CASE WHEN deliveredParts + 1 >= partsExpected AND sentParts >= partsExpected THEN 'DELIVERED' ELSE state END, deliveredAt = CASE WHEN deliveredParts + 1 >= partsExpected AND sentParts >= partsExpected THEN :at ELSE deliveredAt END WHERE requestId = :requestId AND state NOT IN ('FAILED', 'UNKNOWN', 'CANCELLED')") fun markDeliveredPart(requestId: String, at: Long, code: Int): Int
    @Query("UPDATE queue_items SET state = 'UNKNOWN', unknownAt = :at, failureMessage = :message WHERE requestId = :requestId AND state = 'PROCESSING'") fun markUnknown(requestId: String, at: Long, message: String): Int
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
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1") fun find(key: String): SettingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(setting: SettingEntity)
    @Query("DELETE FROM settings WHERE `key` = :key") fun delete(key: String)
}

@Dao
interface ListDao {
    @Insert fun insert(list: ContactListEntity): Long
    @Update fun update(list: ContactListEntity)
    @Query("SELECT * FROM contact_lists ORDER BY name COLLATE NOCASE") fun all(): List<ContactListEntity>
    @Query("SELECT * FROM contact_lists WHERE id = :id LIMIT 1") fun find(id: Long): ContactListEntity?
    @Query("SELECT * FROM contact_lists WHERE origin = :origin ORDER BY id LIMIT 1") fun findByOrigin(origin: String): ContactListEntity?
    @Query("DELETE FROM contact_lists WHERE id = :id") fun delete(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun addMember(member: ContactListMembershipEntity)
    @Query("DELETE FROM contact_list_memberships WHERE listId = :listId AND contactId IN (:contactIds)") fun removeMembers(listId: Long, contactIds: List<Long>)
    @Query("DELETE FROM contact_list_memberships WHERE contactId IN (:contactIds)") fun removeMembersFromAll(contactIds: List<Long>)
    @Query("DELETE FROM contact_list_memberships WHERE listId = :listId") fun removeAllMembers(listId: Long)
    @Transaction fun replaceMembers(list: ContactListEntity, contactIds: List<Long>) { update(list); removeAllMembers(list.id); contactIds.forEach { addMember(ContactListMembershipEntity(list.id, it)) } }
    @Query("SELECT contactId FROM contact_list_memberships WHERE listId = :listId") fun memberIds(listId: Long): List<Long>
    @Query("SELECT listId FROM contact_list_memberships WHERE contactId = :contactId") fun listIdsForContact(contactId: Long): List<Long>
    @Query("SELECT l.id AS id, l.name AS name, l.description AS description, l.parentListId AS parentListId, l.origin AS origin, COUNT(m.contactId) AS totalCount, COALESCE(SUM(CASE WHEN c.authorized = 1 AND c.unsubscribed = 0 AND c.phoneClassification = 'MOBILE_VALID' THEN 1 ELSE 0 END), 0) AS eligibleCount FROM contact_lists l LEFT JOIN contact_list_memberships m ON m.listId = l.id LEFT JOIN contacts c ON c.id = m.contactId GROUP BY l.id ORDER BY l.name COLLATE NOCASE") fun summaries(): List<ContactListSummaryRow>
}

data class ContactListSummaryRow(val id: Long, val name: String, val description: String?, val parentListId: Long?, val origin: String?, val totalCount: Int, val eligibleCount: Int)

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
