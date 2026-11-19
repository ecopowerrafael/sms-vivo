package br.com.smsvivo.app.sms

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.ContactsContract
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
import br.com.smsvivo.app.database.SettingEntity
import br.com.smsvivo.app.receivers.SmsResultReceiver
import br.com.smsvivo.app.queue.SmsQueueService
import java.util.UUID

@CapacitorPlugin(name = "Sms", permissions = [Permission(alias = "sms", strings = [Manifest.permission.SEND_SMS]), Permission(alias = "phoneState", strings = [Manifest.permission.READ_PHONE_STATE]), Permission(alias = "notifications", strings = [Manifest.permission.POST_NOTIFICATIONS]), Permission(alias = "contacts", strings = [Manifest.permission.READ_CONTACTS])])
class SmsPlugin : Plugin() {
    companion object { private const val ACTION_RESULT = SmsResultReceiver.ACTION_UI_RESULT; private const val PROCESSING_RECOVERY_GRACE_MS = 60_000L }
    private val database by lazy { SmsDatabase.get(context) }

    private fun hasActiveSubscription(subscriptionId: Int): Boolean {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        return manager?.activeSubscriptionInfoList?.any { it.subscriptionId == subscriptionId } == true
    }

    /** Capacitor's getLong only accepts JSON Long values; JavaScript integers arrive as Integer. */
    private fun longArg(call: PluginCall, name: String): Long? {
        return when (val value = call.data.opt(name)) {
            null, org.json.JSONObject.NULL -> null
            is Number -> {
                val number = value.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0) null else value.toLong()
            }
            is String -> value.trim().toLongOrNull()
            else -> null
        }
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
        if (!SmsQueueService.isActive.get()) Thread { val recovered = database.queue().recoverStaleProcessing(System.currentTimeMillis() - PROCESSING_RECOVERY_GRACE_MS); if (recovered > 0) database.settings().upsert(SettingEntity(SmsQueueService.SETTING_PAUSE_REASON, SmsQueueService.PAUSE_UNKNOWN)) }.start()
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
    @PluginMethod fun requestContactsPermission(call: PluginCall) { requestPermissionForAlias("contacts", call, "contactsPermission") }

    @PluginMethod fun getDeviceContacts(call: PluginCall) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return call.reject("Permissão READ_CONTACTS não concedida")
        Thread {
            val array = org.json.JSONArray()
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE)
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE")?.use { cursor ->
                val contactId = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID); val phoneId = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID); val name = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); val number = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER); val type = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                while (cursor.moveToNext()) array.put(JSObject().apply { put("sourceId", "${cursor.getLong(contactId)}:${cursor.getLong(phoneId)}"); put("contactId", cursor.getLong(contactId)); put("name", cursor.getString(name) ?: "Sem nome"); put("phone", cursor.getString(number) ?: ""); put("type", cursor.getInt(type)) })
            }
            call.resolve(JSObject().apply { put("contacts", array) })
        }.start()
    }

    @PluginMethod fun listContacts(call: PluginCall) {
        Thread { val array = org.json.JSONArray(); database.contacts().all().forEach { c -> array.put(JSObject().apply { put("id", c.id); put("name", c.name); put("phone", c.phone); put("normalizedPhone", c.normalizedPhone); put("originalPhone", c.originalPhone); put("phoneClassification", c.phoneClassification); put("correctedAt", c.correctedAt); put("company", c.company); put("city", c.city); put("state", c.state); put("customFieldsJson", c.customFieldsJson); put("authorized", c.authorized); put("unsubscribed", c.unsubscribed) }) }; call.resolve(JSObject().apply { put("contacts", array) }) }.start()
    }

    @PluginMethod fun listContactLists(call: PluginCall) { Thread { val lists = org.json.JSONArray(); database.lists().all().forEach { list -> lists.put(JSObject().apply { put("id", list.id); put("name", list.name); put("description", list.description); put("parentListId", list.parentListId); put("contactIds", org.json.JSONArray(database.lists().memberIds(list.id))) }) }; call.resolve(JSObject().apply { put("lists", lists) }) }.start() }
    @PluginMethod fun createContactList(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val id = database.lists().insert(ContactListEntity(name = name, description = call.getString("description"))); val ids = call.getArray("contactIds"); if (ids != null) (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { value -> value > 0 } }.forEach { database.lists().addMember(ContactListMembershipEntity(id, it)) }; call.resolve(JSObject().apply { put("id", id) }) }.start() }
    @PluginMethod fun updateContactList(call: PluginCall) { val id = longArg(call, "id") ?: return call.reject("id é obrigatório"); val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val current = database.lists().find(id) ?: return@Thread call.reject("Lista não encontrada"); val ids = call.getArray("contactIds"); val contactIds = if (ids == null) database.lists().memberIds(id) else (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { value -> value > 0 } }; database.lists().replaceMembers(ContactListEntity(id = id, name = name, description = call.getString("description"), parentListId = current.parentListId, createdAt = current.createdAt), contactIds); call.resolve(JSObject().apply { put("updated", true) }) }.start() }
    @PluginMethod fun deleteContactList(call: PluginCall) { val id = longArg(call, "id") ?: return call.reject("id é obrigatório"); Thread { database.lists().removeAllMembers(id); database.lists().delete(id); call.resolve(JSObject().apply { put("deleted", true) }) }.start() }
    @PluginMethod fun listTemplates(call: PluginCall) { Thread { val templates = org.json.JSONArray(); database.templates().all().forEach { t -> templates.put(JSObject().apply { put("id", t.id); put("name", t.name); put("body", t.body) }) }; call.resolve(JSObject().apply { put("templates", templates) }) }.start() }
    @PluginMethod fun saveTemplate(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); val body = call.getString("body").orEmpty(); if (name.isBlank() || body.isBlank()) return call.reject("Nome e corpo do modelo são obrigatórios"); Thread { val id = longArg(call, "id"); if (id == null) database.templates().insert(MessageTemplateEntity(name = name, body = body)) else database.templates().update(MessageTemplateEntity(id = id, name = name, body = body)); call.resolve(JSObject().apply { put("saved", true) }) }.start() }
    @PluginMethod fun deleteTemplate(call: PluginCall) { val id = longArg(call, "id") ?: return call.reject("id é obrigatório"); Thread { database.templates().delete(id); call.resolve(JSObject().apply { put("deleted", true) }) }.start() }
    @PluginMethod fun listCampaigns(call: PluginCall) { Thread { val campaigns = org.json.JSONArray(); database.campaigns().all().forEach { c -> campaigns.put(JSObject().apply { put("id", c.id); put("name", c.name); put("state", c.state); put("subscriptionId", c.subscriptionId); put("listId", c.listId); put("createdAt", c.createdAt) }) }; call.resolve(JSObject().apply { put("campaigns", campaigns) }) }.start() }
    @PluginMethod fun createCampaign(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); val subscriptionId = call.getInt("subscriptionId") ?: return call.reject("subscriptionId é obrigatório"); val intervalMs = longArg(call, "intervalMs") ?: return call.reject("intervalMs é obrigatório"); if (name.isBlank()) return call.reject("Nome da campanha é obrigatório"); if (intervalMs < 0) return call.reject("intervalMs inválido"); Thread { try { if (!hasActiveSubscription(subscriptionId)) return@Thread call.reject("SIM selecionado não está ativo"); val id = database.campaigns().insert(CampaignEntity(name = name, listId = longArg(call, "listId"), templateId = longArg(call, "templateId"), subscriptionId = subscriptionId, intervalMs = intervalMs, batchSize = call.getInt("batchSize"))); if (id <= 0) return@Thread call.reject("Não foi possível persistir a campanha"); call.resolve(JSObject().apply { put("id", id); put("intervalMs", intervalMs) }) } catch (error: Exception) { call.reject("Falha ao criar campanha: ${error.message ?: "erro desconhecido"}", error) } }.start() }

    @PluginMethod fun authorizeContacts(call: PluginCall) { val ids = call.getArray("ids") ?: return call.reject("ids é obrigatório"); val authorized = call.getBoolean("authorized") ?: false; val source = call.getString("source")?.trim().orEmpty().ifBlank { "bulk-ui" }; Thread { val values = (0 until ids.length()).mapNotNull { ids.optLong(it, -1).takeIf { id -> id > 0 } }; database.beginTransaction(); try { val now = System.currentTimeMillis(); val updated = database.contacts().updateAuthorization(values, authorized, now); database.authorizationEvents().insertAll(values.map { AuthorizationEventEntity(contactId = it, authorized = authorized, source = source, createdAt = now) }); database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("updated", updated) }) } finally { database.endTransaction() } }.start() }

    @PluginMethod fun createImportLists(call: PluginCall) { val name = call.getString("name")?.trim().orEmpty(); if (name.isBlank()) return call.reject("Nome da lista é obrigatório"); Thread { val contactIds = jsonIds(call.getArray("contactIds")); val corrected = jsonIds(call.getArray("correctedIds")); val incomplete = jsonIds(call.getArray("incompleteIds")); val invalid = jsonIds(call.getArray("invalidIds")); database.beginTransaction(); try { val rootId = database.lists().insert(ContactListEntity(name = name)); val sublists = mutableListOf<Long>(); fun add(label: String, ids: List<Long>) { if (ids.isNotEmpty()) { val id = database.lists().insert(ContactListEntity(name = "$name ($label)", parentListId = rootId)); ids.distinct().forEach { database.lists().addMember(ContactListMembershipEntity(id, it)) }; sublists += id } }; contactIds.distinct().forEach { database.lists().addMember(ContactListMembershipEntity(rootId, it)) }; add("Números corrigidos", corrected); add("Números incompletos", incomplete); add("Fixos e inválidos", invalid); database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("id", rootId); put("sublistIds", org.json.JSONArray(sublists)) }) } finally { database.endTransaction() } }.start() }

    private fun jsonIds(array: org.json.JSONArray?): List<Long> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optLong(it, -1).takeIf { id -> id > 0 } }

    @PluginMethod fun upsertContact(call: PluginCall) {
        val name = call.getString("name")?.trim().orEmpty(); val phone = call.getString("phone")?.trim().orEmpty(); val normalized = call.getString("normalizedPhone")?.trim().orEmpty(); val requestedId = longArg(call, "id")
        if (name.isBlank() || normalized.isBlank()) return call.reject("Nome e telefone são obrigatórios")
        Thread { val data = call.data; val originalPhone = call.getString("originalPhone"); val byNormalized = database.contacts().byNormalizedPhone(normalized); val byOriginal = originalPhone?.let { database.contacts().byOriginalPhone(it) }; if (requestedId == null && byNormalized != null && byOriginal == null && byNormalized.originalPhone != originalPhone) return@Thread call.reject("Telefone já pertence a outro contato; revisão manual necessária"); val old = requestedId?.let { database.contacts().find(it) } ?: byNormalized ?: byOriginal; val contact = mergeContact(old, ContactPatch(name, phone, normalized, originalPhone, data.has("originalPhone"), call.getString("phoneClassification"), data.has("phoneClassification"), longArg(call, "correctedAt"), data.has("correctedAt"), call.getString("company"), data.has("company"), call.getString("city"), data.has("city"), call.getString("state"), data.has("state"), call.getString("customFieldsJson"), data.has("customFieldsJson"), call.getBoolean("authorized"), data.has("authorized"), call.getBoolean("unsubscribed"), data.has("unsubscribed"))); val id = if (old == null) database.contacts().insert(contact) else { database.contacts().update(contact); old.id }; call.resolve(JSObject().apply { put("saved", id > 0); put("id", id) }) }.start()
    }

    @PluginMethod fun deleteContacts(call: PluginCall) {
        val ids = call.getArray("ids") ?: return call.reject("ids é obrigatório")
        Thread { val values = jsonIds(ids).distinct(); val blocked = database.contacts().activeQueueContactIds(values); val deletable = values.filterNot { blocked.contains(it) }; database.beginTransaction(); try { if (deletable.isNotEmpty()) { database.lists().removeMembersFromAll(deletable); database.contacts().delete(deletable) }; database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("deleted", deletable.size); put("blocked", blocked.size) }) } finally { database.endTransaction() } }.start()
    }

    @PluginMethod fun removeContactsFromList(call: PluginCall) {
        val listId = longArg(call, "listId") ?: return call.reject("listId é obrigatório"); val ids = call.getArray("ids") ?: return call.reject("ids é obrigatório")
        Thread { val values = jsonIds(ids); database.lists().removeMembers(listId, values); call.resolve(JSObject().apply { put("removed", values.size) }) }.start()
    }

    @PluginMethod fun correctLegacyContacts(call: PluginCall) {
        val corrections = call.getArray("corrections") ?: return call.reject("corrections é obrigatório")
        Thread {
            val now = System.currentTimeMillis(); var corrected = 0; var conflicts = 0; var failed = 0; val correctedIds = mutableListOf<Long>()
            database.beginTransaction()
            try {
                for (index in 0 until corrections.length()) {
                    val item = corrections.optJSONObject(index) ?: continue; val id = item.optLong("id", -1); val phone = item.optString("phone").trim(); val normalized = item.optString("normalizedPhone").trim()
                    if (id <= 0 || phone.isBlank() || normalized.isBlank()) { failed++; continue }
                    val current = database.contacts().find(id); val conflict = database.contacts().byNormalizedPhone(normalized)
                    if (current == null || (conflict != null && conflict.id != id)) { if (conflict != null && conflict.id != id) conflicts++ else failed++; continue }
                    if (current.correctedAt != null && current.phone == phone && current.normalizedPhone == normalized) { correctedIds += id; continue }
                    if (database.contacts().updateCorrectedPhone(id, phone, normalized, "MOBILE_VALID", now, now) == 1) { corrected++; correctedIds += id } else failed++
                }
                val allLists = database.lists().all(); val roots = allLists.filter { it.parentListId == null }
                roots.forEach { root ->
                    val children = allLists.filter { it.parentListId == root.id }; val incomplete = children.firstOrNull { it.name.endsWith("(Números incompletos)") }; val correctedList = children.firstOrNull { it.name.endsWith("(Números corrigidos)") } ?: if (correctedIds.isNotEmpty()) ContactListEntity(name = "${root.name} (Números corrigidos)", parentListId = root.id).let { copy -> database.lists().insert(copy).let { id -> database.lists().find(id)!! } } else null
                    if (incomplete != null && correctedIds.isNotEmpty()) database.lists().removeMembers(incomplete.id, correctedIds)
                    if (correctedList != null && correctedIds.isNotEmpty()) correctedIds.distinct().forEach { database.lists().addMember(ContactListMembershipEntity(correctedList.id, it)) }
                }
                database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("corrected", corrected); put("conflicts", conflicts); put("failed", failed); put("selected", correctedIds.distinct().size) })
            } finally { database.endTransaction() }
        }.start()
    }

    @PluginMethod fun importDeviceContacts(call: PluginCall) {
        val contacts = call.getArray("contacts") ?: return call.reject("contacts é obrigatório")
        Thread {
            var imported = 0; var existing = 0; var skipped = 0; val importedIds = mutableListOf<Long>(); database.beginTransaction()
            try {
                for (index in 0 until contacts.length()) {
                    val item = contacts.optJSONObject(index) ?: continue; val name = item.optString("name").trim().ifBlank { "Contato da agenda" }; val phone = item.optString("phone").trim(); val normalized = item.optString("normalizedPhone").trim(); if (phone.isBlank() || normalized.isBlank()) { skipped++; continue }
                    val old = database.contacts().byNormalizedPhone(normalized); val original = old?.originalPhone ?: phone; val classification = old?.phoneClassification ?: item.optString("phoneClassification").ifBlank { null }; val entity = mergeContact(old, ContactPatch(name, phone, normalized, original, true, classification, true, null, false, null, false, null, false, null, false, null, false, null, false, null, false)); val id = if (old == null) database.contacts().insert(entity) else { database.contacts().update(entity); old.id }; if (old == null) imported++ else existing++; importedIds += id
                }
                database.setTransactionSuccessful(); call.resolve(JSObject().apply { put("imported", imported); put("existing", existing); put("skipped", skipped); put("ids", org.json.JSONArray(importedIds.distinct())) })
            } finally { database.endTransaction() }
        }.start()
    }

    @PluginMethod fun queueStats(call: PluginCall) { Thread { val q = database.queue(); val total = q.total(); val first = q.firstCreatedAt(); val last = q.lastActivityAt(); val elapsedMs = if (first != null && last != null && last >= first) last - first else 0L; val pending = q.byState(QueueStates.PENDING); val ineligiblePending = pending.count { item -> item.contactId?.let { database.contacts().find(it) }?.let { !SmsEligibility.isEligible(it) } == true }; val processed = total - q.count(QueueStates.PENDING) - q.count(QueueStates.PROCESSING); val current = q.byState(QueueStates.PROCESSING).firstOrNull(); val pauseReason = database.settings().find(SmsQueueService.SETTING_PAUSE_REASON)?.value; val ratePerMinute = if (elapsedMs > 0) processed.toDouble() * 60000.0 / elapsedMs else 0.0; call.resolve(JSObject().apply { put("pending", pending.size); put("ineligiblePending", ineligiblePending); put("processing", q.count(QueueStates.PROCESSING)); put("sent", q.count(QueueStates.SENT)); put("delivered", q.count(QueueStates.DELIVERED)); put("failed", q.count(QueueStates.FAILED)); put("unknown", q.count(QueueStates.UNKNOWN)); put("cancelled", q.count(QueueStates.CANCELLED)); put("skipped", q.count(QueueStates.SKIPPED)); put("processed", processed); put("total", total); put("elapsedMs", elapsedMs); put("ratePerMinute", ratePerMinute); put("contacts", database.contacts().count()); put("pauseReason", pauseReason); put("currentRequestId", current?.requestId); put("currentDestination", current?.destination); put("currentCampaignId", current?.campaignId); put("currentSmsRequestedAt", current?.smsRequestedAt); put("currentRequestIntervalMs", current?.requestIntervalMs) }) }.start() }
    @PluginMethod fun listHistory(call: PluginCall) { Thread { val array = org.json.JSONArray(); database.queue().allHistory().forEach { item -> array.put(JSObject().apply { put("requestId", item.requestId); put("state", item.state); put("code", item.failureCode); put("message", item.failureMessage); put("createdAt", java.time.Instant.ofEpochMilli(item.createdAt).toString()); put("sentAt", java.time.Instant.ofEpochMilli(item.sentAt ?: item.createdAt).toString()); put("deliveredAt", item.deliveredAt?.let { java.time.Instant.ofEpochMilli(it).toString() }); put("smsRequestedAt", item.smsRequestedAt?.let { java.time.Instant.ofEpochMilli(it).toString() }); put("lastCallbackAt", item.lastCallbackAt?.let { java.time.Instant.ofEpochMilli(it).toString() }); put("unknownAt", item.unknownAt?.let { java.time.Instant.ofEpochMilli(it).toString() }); put("requestIntervalMs", item.requestIntervalMs); put("partsExpected", item.partsExpected); put("sentParts", item.sentParts); put("deliveredParts", item.deliveredParts); put("lastCallbackCode", item.lastCallbackCode); put("destination", item.destination); put("campaignId", item.campaignId); put("listId", item.campaignId?.let { database.campaigns().find(it)?.listId }); put("subscriptionId", item.subscriptionId) }) }; call.resolve(JSObject().apply { put("results", array) }) }.start() }
    @PluginMethod fun listDiagnostics(call: PluginCall) { Thread { val array = org.json.JSONArray(); database.diagnostics().recent(200).forEach { event -> array.put(JSObject().apply { put("id", event.id); put("category", event.category); put("code", event.code); put("message", event.message); put("createdAt", event.createdAt) }) }; call.resolve(JSObject().apply { put("events", array) }) }.start() }

    @PluginMethod fun enqueue(call: PluginCall) {
        val requestId = call.getString("requestId") ?: UUID.randomUUID().toString(); val destination = call.getString("destination")?.trim().orEmpty(); val body = call.getString("body").orEmpty(); val subscriptionId = call.getInt("subscriptionId") ?: return call.reject("subscriptionId é obrigatório"); val contactId = longArg(call, "contactId"); val campaignId = longArg(call, "campaignId")
        if (destination.isBlank() || body.isBlank()) return call.reject("destination e body são obrigatórios")
        Thread { try { if (!hasActiveSubscription(subscriptionId)) return@Thread call.reject("SIM selecionado não está ativo"); val campaign = campaignId?.let { database.campaigns().find(it) }; if (campaignId != null && campaign == null) return@Thread call.reject("Campanha não encontrada"); if (database.queue().byRequestId(requestId) != null) return@Thread call.reject("requestId duplicado"); if (campaignId != null && contactId == null) return@Thread call.reject("contactId é obrigatório para itens de campanha"); if (contactId != null) { val contact = database.contacts().find(contactId); if (contact == null || !SmsEligibility.isEligible(contact)) return@Thread call.reject("Contato não autorizado, descadastrado ou sem celular completo válido para SMS") }; if (!SmsEligibility.isCompleteMobile(destination)) return@Thread call.reject("Destino não possui celular completo válido para SMS"); val sms = context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId); val partsExpected = sms.divideMessage(body).size; val id = database.queue().insert(QueueItemEntity(requestId = requestId, campaignId = campaignId, contactId = contactId, destination = destination, body = body, subscriptionId = subscriptionId, partsExpected = partsExpected, requestIntervalMs = campaign?.intervalMs)); if (id <= 0) return@Thread call.reject("Não foi possível persistir o item da fila"); call.resolve(JSObject().apply { put("queued", true); put("queueItemId", id); put("requestId", requestId); put("campaignId", campaignId); put("requestIntervalMs", campaign?.intervalMs) }) } catch (error: Exception) { call.reject("Falha ao adicionar contato à fila: ${error.message ?: "erro desconhecido"}", error) } }.start()
    }

    @PluginMethod fun startQueue(call: PluginCall) { val intervalMs = longArg(call, "intervalMs") ?: return call.reject("intervalMs é obrigatório"); if (intervalMs < 0) return call.reject("intervalMs inválido"); Thread { val pause = database.settings().find(SmsQueueService.SETTING_PAUSE_REASON); if (pause != null) return@Thread call.reject("Fila pausada: ${pause.value}. Revise os resultados e use retomar explicitamente."); val intent = Intent(context, SmsQueueService::class.java).setAction(SmsQueueService.ACTION_START).putExtra("intervalMs", intervalMs).putExtra("batchSize", call.getInt("batchSize") ?: 0); if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent); call.resolve(JSObject().apply { put("started", true); put("intervalMs", intervalMs); put("batchSize", call.getInt("batchSize") ?: 0) }) }.start() }
    @PluginMethod fun resumeQueue(call: PluginCall) { Thread { database.settings().delete(SmsQueueService.SETTING_PAUSE_REASON); val intent = Intent(context, SmsQueueService::class.java).setAction(SmsQueueService.ACTION_START).putExtra("intervalMs", 0L).putExtra("batchSize", 0); if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent); call.resolve(JSObject().apply { put("resumed", true) }) }.start() }
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
