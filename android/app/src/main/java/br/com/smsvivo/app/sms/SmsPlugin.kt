package br.com.smsvivo.app.sms

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import br.com.smsvivo.app.database.DiagnosticEventEntity
import br.com.smsvivo.app.database.QueueItemEntity
import br.com.smsvivo.app.database.QueueStates
import br.com.smsvivo.app.database.SmsDatabase
import br.com.smsvivo.app.database.ContactEntity
import br.com.smsvivo.app.database.ContactListEntity
import br.com.smsvivo.app.database.ContactListMembershipEntity
import br.com.smsvivo.app.database.MessageTemplateEntity
import br.com.smsvivo.app.database.CampaignEntity
import br.com.smsvivo.app.database.ContactPatch
import br.com.smsvivo.app.database.mergeContact
import br.com.smsvivo.app.database.SmsResultPhase
import br.com.smsvivo.app.database.AuthorizationEventEntity
import br.com.smsvivo.app.receivers.SmsResultReceiver
import br.com.smsvivo.app.queue.SmsQueueService
import java.util.UUID

@CapacitorPlugin(name = "Sms", permissions = [Permission(alias = "sms", strings = [Manifest.permission.SEND_SMS]), Permission(alias = "phoneState", strings = [Manifest.permission.READ_PHONE_STATE]), Permission(alias = "notifications", strings = [Manifest.permission.POST_NOTIFICATIONS])])
class SmsPlugin : Plugin() {
    companion object { private const val ACTION_RESULT = SmsResultReceiver.ACTION_UI_RESULT; private const val PROCESSING_RECOVERY_GRACE_MS = 60_000L }
    private val database by lazy { SmsDatabase.get(context) }

    private fun hasActiveSubscription(subscriptionId: Int): Boolean {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        return manager?.activeSubscriptionInfoList?.any { it.subscriptionId == subscriptionId } == true
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val requestId = intent.getStringExtra("requestId") ?: return
            val state = intent.getStringExtra("state") ?: return
            val result = JSObject().apply { put("requestId", requestId); put("state", state); put("code", intent.getIntExtra("code", 0)); put("sentAt", intent.getStringExtra("sentAt") ?: java.time.Instant.now().toString()) }
            notifyListeners("smsResult", result)
        }
    }

    override fun load() {
        super.load()
        if (!SmsQueueService.isActive.get()) Thread { database.queue().recoverStaleProcessing(System.currentTimeMillis() - PROCESSING_RECOVERY_GRACE_MS) }.start()
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
    }

    @PluginMethod fun diagnose(call: PluginCall) {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val canSend = telephony?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val phoneStateGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val subscriptions = JSObject(); if (phoneStateGranted) manager?.activeSubscriptionInfoList?.forEach { info -> subscriptions.put(info.subscriptionId.toString(), JSObject().apply { put("id", info.subscriptionId); put("displayName", info.displayName.toString()); put("carrierName", info.carrierName?.toString()); put("slotIndex", info.simSlotIndex) }) }
        val notifications = if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
        call.resolve(JSObject().apply { put("canSendSms", canSend); put("sendSmsGranted", granted); put("phoneStateGranted", phoneStateGranted); put("notificationGranted", notifications); put("subscriptions", if (phoneStateGranted) subscriptionsToArray(manager) else org.json.JSONArray()) })
    }

    private fun subscriptionsToArray(manager: SubscriptionManager?): org.json.JSONArray {
        val array = org.json.JSONArray(); manager?.activeSubscriptionInfoList?.forEach { info -> array.put(JSObject().apply { put("id", info.subscriptionId); put("displayName", info.displayName.toString()); put("carrierName", info.carrierName?.toString()); put("slotIndex", info.simSlotIndex) }.toString()) }
        val normalized = org.json.JSONArray(); for (i in 0 until array.length()) normalized.put(org.json.JSONObject(array.getString(i))); return normalized
    }

    @PluginMethod fun requestSendPermission(call: PluginCall) { requestPermissionForAlias("sms", call, "smsPermission") }
    @PluginMethod fun requestPhoneStatePermission(call: PluginCall) { requestPermissionForAlias("phoneState", call, "phoneStatePermission") }
    @PluginMethod fun requestNotificationPermission(call: PluginCall) { requestPermissionForAlias("notifications", call, "notificationPermission") }

    @PluginMethod fun listContacts(call: PluginCall) {
        Thread { val array = org.json.JSONArray(); database.contacts().all().forEach { c -> array.put(JSObject().apply { put("id", c.id); put("name", c.name); put("phone", c.phone); put("normalizedPhone", c.normalizedPhone); put("originalPhone", c.originalPhone); put("phoneClassification", c.phoneClassification); put("correctedAt", c.correctedAt); put("company", c.company); put("city", c.city); put("state", c.state); put("customFieldsJson", c.customFieldsJson); put("authorized", c.authorized); put("unsubscribed", c.unsubscribed) }) }; call.resolve(JSObject().apply { put("contacts", array) }) }.start()
    }

    @PluginMethod fun listContactLists(call: PluginCall) { Thread { val lists = org.json.JSONArray(); database.lists().all().forEach { list -> lists.put(JSObject().apply { put("id", list.id); put("name", list.name); put("description", list.description); put("parentListId", list.parentListId); put("contactIds", org.json.JSONArray(database.lists().memberIds(list.id))) }) }; call.resolve(JSObject().apply { put("lists", lists) }) }.start() }
    @PluginMethod fun createContactList(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val id = database.lists().insert(ContactListEntity(name = name, description = call.getString("description"))); val ids = call.getArray("contactIds"); if (ids != null) (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { value -> value > 0 } }.forEach { database.lists().addMember(ContactListMembershipEntity(id, it)) }; call.resolve(JSObject().apply { put("id", id) }) }.start() }
    @PluginMethod fun updateContactList(call: PluginCall) { val id = call.getLong("id") ?: return call.reject("id é obrigatório"); val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val current = database.lists().find(id) ?: return@Thread call.reject("Lista não encontrada"); val ids = call.getArray("contactIds"); val contactIds = if (ids == null) database.lists().memberIds(id) else (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { value -> value > 0 } }; database.lists().replaceMembers(ContactListEntity(id = id, name = name, description = call.getString("description"), parentListId = current.parentListId, createdAt = current.createdAt), contactIds); call.resolve(JSObject().apply { put("updated", true) }) }.start() }
    @PluginMethod fun deleteContactList(call: PluginCall) { val id = call.getLong("id") ?: return call.reject("id é obrigatório"); Thread { database.lists().removeAllMembers(id); database.lists().delete(id); call.resolve(JSObject().apply { put("deleted", true) }) }.start() }
    @PluginMethod fun listTemplates(call: PluginCall) { Thread { val templates = org.json.JSONArray(); database.templates().all().forEach { t -> templates.put(JSObject().apply { put("id", t.id); put("name", t.name); put("body", t.body) }) }; call.resolve(JSObject().apply { put("templates", templates) }) }.start() }
    @PluginMethod fun saveTemplate(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); val body = call.getString("body").orEmpty(); if (name.isBlank() || body.isBlank()) return call.reject("Nome e corpo do modelo são obrigatórios"); Thread { val id = call.getLong("id"); if (id == null) database.templates().insert(MessageTemplateEntity(name = name, body = body)) else database.templates().update(MessageTemplateEntity(id = id, name = name, body = body)); call.resolve(JSObject().apply { put("saved", true) }) }.start() }
    @PluginMethod fun deleteTemplate(call: PluginCall) { val id = call.getLong("id") ?: return call.reject("id é obrigatório"); Thread { database.templates().delete(id); call.resolve(JSObject().apply { put("deleted", true) }) }.start() }
    @PluginMethod fun listCampaigns(call: PluginCall) { Thread { val campaigns = org.json.JSONArray(); database.campaigns().all().forEach { c -> campaigns.put(JSObject().apply { put("id", c.id); put("name", c.name); put("state", c.state); put("subscriptionId", c.subscriptionId); put("listId", c.listId); put("createdAt", c.createdAt) }) }; call.resolve(JSObject().apply { put("campaigns", campaigns) }) }.start() }
    @PluginMethod fun createCampaign(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); val subscriptionId = call.getInt("subscriptionId") ?: return call.reject("subscriptionId é obrigatório"); if (name.isBlank()) return call.reject("Nome da campanha é obrigatório"); Thread { if (!hasActiveSubscription(subscriptionId)) return@Thread call.reject("SIM selecionado não está ativo"); val id = database.campaigns().insert(CampaignEntity(name = name, listId = call.getLong("listId"), templateId = call.getLong("templateId"), subscriptionId = subscriptionId, intervalMs = call.getLong("intervalMs") ?: 0L, batchSize = call.getInt("batchSize"))); call.resolve(JSObject().apply { put("id", id) }) }.start() }

    @PluginMethod fun authorizeContacts(call: PluginCall) { val ids = call.getArray("ids") ?: return call.reject("ids é obrigatório"); val authorized = call.getBoolean("authorized") ?: false; val source = call.getString("source")?.trim().orEmpty().ifBlank { "bulk-ui" }; Thread { val values = (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { id -> id > 0 } }; database.beginTransaction(); try { val now = System.currentTimeMillis(); val updated = database.contacts().updateAuthorization(values, authorized, now); database.authorizationEvents().insertAll(values.map { AuthorizationEventEntity(contactId = it, authorized = authorized, source = source, createdAt = now) }); database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("updated", updated) }) } finally { database.endTransaction() } }.start() }

    @PluginMethod fun createImportLists(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val contactIds = jsonIds(call.getArray("contactIds")); val corrected = jsonIds(call.getArray("correctedIds")); val incomplete = jsonIds(call.getArray("incompleteIds")); val invalid = jsonIds(call.getArray("invalidIds")); database.beginTransaction(); try { val rootId = database.lists().insert(ContactListEntity(name = name)); val sublists = mutableListOf<Long>(); fun add(label: String, ids: List<Long>) { if (ids.isNotEmpty()) { val id = database.lists().insert(ContactListEntity(name = "$name ($label)", parentListId = rootId)); ids.distinct().forEach { database.lists().addMember(ContactListMembershipEntity(id, it)) }; sublists += id } }; contactIds.distinct().forEach { database.lists().addMember(ContactListMembershipEntity(rootId, it)) }; add("Números corrigidos", corrected); add("Números incompletos", incomplete); add("Fixos e inválidos", invalid); database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("id", rootId); put("sublistIds", org.json.JSONArray(sublists)) }) } finally { database.endTransaction() } }.start() }

    private fun jsonIds(array: org.json.JSONArray?): List<Long> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optLong(it, -1).takeIf { id -> id > 0 } }

    @PluginMethod fun upsertContact(call: PluginCall) {
        val name = call.getString("name")?.trim().orEmpty(); val phone = call.getString("phone")?.trim().orEmpty(); val normalized = call.getString("normalizedPhone")?.trim().orEmpty(); val requestedId = call.getLong("id")
        if (name.isBlank() || normalized.isBlank()) return call.reject("Nome e telefone são obrigatórios")
        Thread { val data = call.data; val originalPhone = call.getString("originalPhone"); val byNormalized = database.contacts().byNormalizedPhone(normalized); val byOriginal = originalPhone?.let { database.contacts().byOriginalPhone(it) }; if (requestedId == null && byNormalized != null && byOriginal == null && byNormalized.originalPhone != originalPhone) return@Thread call.reject("Telefone já pertence a outro contato; revisão manual necessária"); val old = requestedId?.let { database.contacts().find(it) } ?: byNormalized ?: byOriginal; val contact = mergeContact(old, ContactPatch(name, phone, normalized, originalPhone, data.has("originalPhone"), call.getString("phoneClassification"), data.has("phoneClassification"), call.getLong("correctedAt"), data.has("correctedAt"), call.getString("company"), data.has("company"), call.getString("city"), data.has("city"), call.getString("state"), data.has("state"), call.getString("customFieldsJson"), data.has("customFieldsJson"), call.getBoolean("authorized"), data.has("authorized"), call.getBoolean("unsubscribed"), data.has("unsubscribed"))); val id = if (old == null) database.contacts().insert(contact) else { database.contacts().update(contact); old.id }; call.resolve(JSObject().apply { put("saved", id > 0); put("id", id) }) }.start()
    }

    @PluginMethod fun deleteContacts(call: PluginCall) {
        val ids = call.getArray("ids") ?: return call.reject("ids é obrigatório")
        Thread { val values = (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { id -> id > 0 } }; database.contacts().delete(values); call.resolve(JSObject().apply { put("deleted", values.size) }) }.start()
    }

    @PluginMethod fun queueStats(call: PluginCall) { Thread { val q = database.queue(); val total = q.total(); val first = q.firstCreatedAt(); val last = q.lastActivityAt(); val elapsedMs = if (first != null && last != null && last >= first) last - first else 0L; val processed = total - q.count(QueueStates.PENDING) - q.count(QueueStates.PROCESSING); val ratePerMinute = if (elapsedMs > 0) processed.toDouble() * 60000.0 / elapsedMs else 0.0; call.resolve(JSObject().apply { put("pending", q.count(QueueStates.PENDING)); put("processing", q.count(QueueStates.PROCESSING)); put("sent", q.count(QueueStates.SENT)); put("delivered", q.count(QueueStates.DELIVERED)); put("failed", q.count(QueueStates.FAILED)); put("unknown", q.count(QueueStates.UNKNOWN)); put("cancelled", q.count(QueueStates.CANCELLED)); put("skipped", q.count(QueueStates.SKIPPED)); put("processed", processed); put("total", total); put("elapsedMs", elapsedMs); put("ratePerMinute", ratePerMinute); put("contacts", database.contacts().count()) }) }.start() }
    @PluginMethod fun listHistory(call: PluginCall) { Thread { val array = org.json.JSONArray(); database.queue().allHistory().forEach { item -> array.put(JSObject().apply { put("requestId", item.requestId); put("state", item.state); put("code", item.failureCode); put("message", item.failureMessage); put("sentAt", java.time.Instant.ofEpochMilli(item.sentAt ?: item.createdAt).toString()); put("deliveredAt", item.deliveredAt?.let { java.time.Instant.ofEpochMilli(it).toString() }); put("destination", item.destination); put("campaignId", item.campaignId); put("listId", item.campaignId?.let { database.campaigns().find(it)?.listId }); put("subscriptionId", item.subscriptionId) }) }; call.resolve(JSObject().apply { put("results", array) }) }.start() }
    @PluginMethod fun listDiagnostics(call: PluginCall) { Thread { val array = org.json.JSONArray(); database.diagnostics().recent(200).forEach { event -> array.put(JSObject().apply { put("id", event.id); put("category", event.category); put("code", event.code); put("message", event.message); put("createdAt", event.createdAt) }) }; call.resolve(JSObject().apply { put("events", array) }) }.start() }

    @PluginMethod fun enqueue(call: PluginCall) {
        val requestId = call.getString("requestId") ?: UUID.randomUUID().toString(); val destination = call.getString("destination")?.trim().orEmpty(); val body = call.getString("body").orEmpty(); val subscriptionId = call.getInt("subscriptionId") ?: return call.reject("subscriptionId é obrigatório"); val contactId = call.getLong("contactId"); val campaignId = call.getLong("campaignId")
        if (destination.isBlank() || body.isBlank()) return call.reject("destination e body são obrigatórios")
        Thread { if (!hasActiveSubscription(subscriptionId)) return@Thread call.reject("SIM selecionado não está ativo"); if (campaignId != null && database.campaigns().find(campaignId) == null) return@Thread call.reject("Campanha não encontrada"); if (database.queue().byRequestId(requestId) != null) return@Thread call.reject("requestId duplicado"); if (contactId != null) { val contact = database.contacts().find(contactId); if (contact == null || !contact.authorized || contact.unsubscribed) return@Thread call.reject("Contato não autorizado ou descadastrado") }; val partsExpected = context.getSystemService(SmsManager::class.java).divideMessage(body).size; val id = database.queue().insert(QueueItemEntity(requestId = requestId, campaignId = campaignId, contactId = contactId, destination = destination, body = body, subscriptionId = subscriptionId, partsExpected = partsExpected)); call.resolve(JSObject().apply { put("queued", id > 0); put("queueItemId", id); put("requestId", requestId) }) }.start()
    }

    @PluginMethod fun startQueue(call: PluginCall) { val intervalMs = call.getLong("intervalMs") ?: 0L; val batchSize = call.getInt("batchSize") ?: 0; val intent = Intent(context, SmsQueueService::class.java).setAction(SmsQueueService.ACTION_START).putExtra("intervalMs", intervalMs).putExtra("batchSize", batchSize); if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent); call.resolve(JSObject().apply { put("started", true); put("intervalMs", intervalMs); put("batchSize", batchSize) }) }
    @PluginMethod fun pauseQueue(call: PluginCall) { context.startService(Intent(context, SmsQueueService::class.java).setAction(SmsQueueService.ACTION_PAUSE)); call.resolve(JSObject().apply { put("paused", true) }) }
    @PluginMethod fun stopQueue(call: PluginCall) { context.startService(Intent(context, SmsQueueService::class.java).setAction(SmsQueueService.ACTION_STOP)); call.resolve(JSObject().apply { put("stopped", true) }) }

    @PluginMethod fun send(call: PluginCall) {
        val subscriptionId = call.getInt("subscriptionId") ?: return call.reject("subscriptionId é obrigatório")
        val destination = call.getString("destination")?.trim() ?: return call.reject("destination é obrigatório")
        val body = call.getString("body") ?: return call.reject("body é obrigatório")
        val requestId = call.getString("requestId") ?: UUID.randomUUID().toString()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return call.reject("Permissão SEND_SMS não concedida")
        Thread {
            try {
                if (!hasActiveSubscription(subscriptionId)) return@Thread call.reject("SIM selecionado não está ativo")
                val existing = database.queue().byRequestId(requestId)
                if (existing != null) return@Thread call.reject("requestId já processado; reenvio bloqueado")
                val sms = context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                val parts = sms.divideMessage(body)
                val itemId = database.queue().insert(QueueItemEntity(requestId = requestId, destination = destination, body = body, subscriptionId = subscriptionId, state = QueueStates.PROCESSING, processingAt = System.currentTimeMillis(), partsExpected = parts.size, attemptCount = 1))
                if (itemId == -1L) return@Thread call.reject("Não foi possível persistir a solicitação")
                database.diagnostics().insert(DiagnosticEventEntity(category = "SMS_REQUEST", message = "Solicitação persistida para $requestId"))
                fun callback(action: String, partIndex: Int, offset: Int): PendingIntent = PendingIntent.getBroadcast(context, requestId.hashCode() * 31 + partIndex * 2 + offset, Intent(context, SmsResultReceiver::class.java).setAction(action).putExtra("requestId", requestId).putExtra("partIndex", partIndex), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                if (parts.size == 1) sms.sendTextMessage(destination, null, body, callback(SmsResultReceiver.ACTION_SENT, 0, 0), callback(SmsResultReceiver.ACTION_DELIVERED, 0, 1)) else sms.sendMultipartTextMessage(destination, null, parts, ArrayList(parts.indices.map { callback(SmsResultReceiver.ACTION_SENT, it, 0) }), ArrayList(parts.indices.map { callback(SmsResultReceiver.ACTION_DELIVERED, it, 1) }))
                call.resolve(JSObject().apply { put("accepted", true); put("requestId", requestId); put("queueItemId", itemId) })
            } catch (error: Exception) {
                database.queue().markResult(requestId, QueueStates.FAILED, -1, error.message, System.currentTimeMillis())
                database.diagnostics().insert(DiagnosticEventEntity(category = "SMS_EXCEPTION", code = -1, message = error.message ?: "Falha desconhecida"))
                call.reject("Falha ao solicitar SMS: ${error.message}", error)
            }
        }.start()
    }
}
