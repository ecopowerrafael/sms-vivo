package br.com.smsvivo.app.queue

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import br.com.smsvivo.app.MainActivity
import br.com.smsvivo.app.R
import br.com.smsvivo.app.database.DiagnosticEventEntity
import br.com.smsvivo.app.database.QueueItemEntity
import br.com.smsvivo.app.database.QueueStates
import br.com.smsvivo.app.database.SettingEntity
import br.com.smsvivo.app.database.SmsDatabase
import br.com.smsvivo.app.receivers.SmsResultReceiver
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SmsQueueService : Service() {
    companion object {
        const val ACTION_START = "br.com.smsvivo.app.queue.START"; const val ACTION_PAUSE = "br.com.smsvivo.app.queue.PAUSE"; const val ACTION_STOP = "br.com.smsvivo.app.queue.STOP"
        const val SETTING_PAUSE_REASON = "queue.pause.reason"; const val PAUSE_ANDROID_LIMIT = "ANDROID_LIMIT"; const val PAUSE_UNKNOWN = "UNKNOWN_RESULT"
        private const val CHANNEL = "sms_queue"; private const val NOTIFICATION_ID = 4101; private const val RESULT_TIMEOUT_MS = 120_000L
        val isActive = AtomicBoolean(false)
    }

    private enum class SendOutcome { SENT, FAILED, UNKNOWN }
    private val running = AtomicBoolean(false); private val paused = AtomicBoolean(false); private val executor = Executors.newSingleThreadExecutor(); private val database by lazy { SmsDatabase.get(this) }
    private var intervalMs: Long = 0; private var batchSize: Int = 0; private var pendingCount = 0; private var processedCount = 0; private var currentRequestId: String? = null

    override fun onCreate() { super.onCreate(); isActive.set(true); createChannel(); val recovered = database.queue().recoverStaleProcessing(System.currentTimeMillis()); if (recovered > 0) persistPause(PAUSE_UNKNOWN, "Fila pausada: havia uma mensagem sem confirmação após a reinicialização.") }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("intervalMs") == true) intervalMs = intent.getLongExtra("intervalMs", -1L)
        if (intent?.hasExtra("batchSize") == true) batchSize = intent.getIntExtra("batchSize", 0).coerceAtLeast(0)
        when (intent?.action) { ACTION_PAUSE -> paused.set(true); ACTION_STOP -> { paused.set(true); executor.execute { database.queue().cancelPending(); refreshNotificationCounts(); stopSelf() }; return START_NOT_STICKY }; else -> startProcessing() }
        updateNotification(); return START_STICKY
    }

    private fun startProcessing() {
        if (database.settings().find(SETTING_PAUSE_REASON) != null) { paused.set(true); updateNotification(); return }
        if (running.getAndSet(true)) { paused.set(false); return }
        paused.set(false)
        val type = if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), type) else startForeground(NOTIFICATION_ID, notification())
        executor.execute {
            var lastConclusiveAt: Long? = null; val processedByCampaign = mutableMapOf<Long, Int>(); var processed = 0
            try {
                refreshNotificationCounts()
                while (!paused.get() && (batchSize <= 0 || processed < batchSize)) {
                    val item = claimNextEligible(processedByCampaign) ?: break; currentRequestId = item.requestId; refreshNotificationCounts()
                    val outcome = sendItemAndAwait(item)
                    currentRequestId = null
                    if (outcome != SendOutcome.SENT) break
                    lastConclusiveAt = System.currentTimeMillis(); item.campaignId?.let { processedByCampaign[it] = (processedByCampaign[it] ?: 0) + 1 }; processed++; refreshNotificationCounts()
                    val configuredInterval = item.campaignId?.let { database.campaigns().find(it)?.intervalMs } ?: item.requestIntervalMs
                    if (configuredInterval == null || configuredInterval < 0) { persistPause(PAUSE_UNKNOWN, "Fila pausada: intervalo ausente ou inválido."); break }
                    val delay = QueueTimingPolicy { System.currentTimeMillis() }.delayUntilNextRequest(lastConclusiveAt, configuredInterval)
                    if (delay > 0 && !sleepWhileRespectingPause(delay)) break
                }
            } finally { currentRequestId = null; running.set(false); refreshNotificationCounts(); if (!paused.get()) stopSelf() }
        }
    }

    private fun claimNextEligible(processedByCampaign: MutableMap<Long, Int>): QueueItemEntity? {
        val queue = database.queue()
        for (candidate in queue.pending()) {
            val campaign = candidate.campaignId?.let { database.campaigns().find(it) }; val cap = campaign?.batchSize?.takeIf { it > 0 } ?: batchSize
            if (candidate.campaignId != null && cap > 0 && (processedByCampaign[candidate.campaignId] ?: 0) >= cap) continue
            if (queue.claimPending(candidate.id, System.currentTimeMillis()) == 1) return queue.byRequestId(candidate.requestId)
        }
        return null
    }

    private fun sendItemAndAwait(item: QueueItemEntity): SendOutcome {
        try {
            val active = getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList?.any { it.subscriptionId == item.subscriptionId } == true
            if (!active) throw IllegalStateException("SIM selecionado não está ativo")
            val sms = getSystemService(SmsManager::class.java).createForSubscriptionId(item.subscriptionId); val parts = sms.divideMessage(item.body); database.queue().update(item.copy(partsExpected = parts.size))
            fun callback(action: String, partIndex: Int, offset: Int): PendingIntent = PendingIntent.getBroadcast(this, item.requestId.hashCode() * 31 + partIndex * 2 + offset, Intent(this, SmsResultReceiver::class.java).setAction(action).putExtra("requestId", item.requestId).putExtra("partIndex", partIndex), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val requestAt = System.currentTimeMillis(); val configuredInterval = item.campaignId?.let { database.campaigns().find(it)?.intervalMs } ?: item.requestIntervalMs
            if (configuredInterval == null || configuredInterval < 0) throw IllegalStateException("Intervalo da solicitação ausente ou inválido")
            database.queue().markSmsRequested(item.requestId, requestAt, configuredInterval); database.diagnostics().insert(DiagnosticEventEntity(category = "SMS_REQUEST", message = "SmsManager solicitado requestId=${item.requestId} campaignId=${item.campaignId} intervalMs=$configuredInterval parts=${parts.size}", createdAt = requestAt))
            if (parts.size == 1) sms.sendTextMessage(item.destination, null, item.body, callback(SmsResultReceiver.ACTION_SENT, 0, 0), callback(SmsResultReceiver.ACTION_DELIVERED, 0, 1)) else sms.sendMultipartTextMessage(item.destination, null, parts, ArrayList(parts.indices.map { callback(SmsResultReceiver.ACTION_SENT, it, 0) }), ArrayList(parts.indices.map { callback(SmsResultReceiver.ACTION_DELIVERED, it, 1) }))
        } catch (error: Exception) { database.queue().markResult(item.requestId, QueueStates.FAILED, -1, error.message, System.currentTimeMillis()); return SendOutcome.FAILED }
        val deadline = System.currentTimeMillis() + RESULT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val current = database.queue().byRequestId(item.requestId) ?: return SendOutcome.UNKNOWN
            when (current.state) { QueueStates.SENT, QueueStates.DELIVERED -> return SendOutcome.SENT; QueueStates.FAILED -> { if (current.failureCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED) persistPause(PAUSE_ANDROID_LIMIT, "Envios pausados: o Android atingiu o limite permitido de SMS."); return SendOutcome.FAILED }; QueueStates.UNKNOWN -> return SendOutcome.UNKNOWN }
            Thread.sleep(100)
        }
        database.queue().markUnknown(item.requestId, System.currentTimeMillis(), "Timeout aguardando confirmação SENT do Android; reenvio automático bloqueado"); persistPause(PAUSE_UNKNOWN, "Fila pausada: uma mensagem não recebeu confirmação conclusiva do Android."); return SendOutcome.UNKNOWN
    }

    private fun sleepWhileRespectingPause(delayMs: Long): Boolean { var remaining = delayMs; while (remaining > 0 && !paused.get()) { val chunk = remaining.coerceAtMost(250L); Thread.sleep(chunk); remaining -= chunk }; return !paused.get() }
    private fun persistPause(reason: String, message: String) { database.settings().upsert(SettingEntity(SETTING_PAUSE_REASON, reason)); database.diagnostics().insert(DiagnosticEventEntity(category = "QUEUE_PAUSED", message = message)) ; paused.set(true); updateNotification() }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Fila de SMS", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification { val pause = PendingIntent.getService(this, 1, Intent(this, SmsQueueService::class.java).setAction(if (paused.get()) ACTION_START else ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val stop = PendingIntent.getService(this, 2, Intent(this, SmsQueueService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("SMS Vivo").setContentText("$processedCount processadas · $pendingCount pendentes").setContentIntent(open).setOngoing(true).addAction(NotificationCompat.Action.Builder(0, if (paused.get()) "Retomar" else "Pausar", pause).build()).addAction(NotificationCompat.Action.Builder(0, "Interromper", stop).build()).build() }
    private fun refreshNotificationCounts() { val q = database.queue(); val total = q.total(); pendingCount = q.count(QueueStates.PENDING); processedCount = total - pendingCount - q.count(QueueStates.PROCESSING); updateNotification() }
    private fun updateNotification() { if (running.get()) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { isActive.set(false); paused.set(true); executor.shutdownNow(); super.onDestroy() }
}
