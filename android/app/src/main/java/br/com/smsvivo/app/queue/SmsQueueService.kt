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
import br.com.smsvivo.app.database.QueueItemEntity
import br.com.smsvivo.app.database.QueueStates
import br.com.smsvivo.app.database.SmsDatabase
import br.com.smsvivo.app.receivers.SmsResultReceiver
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SmsQueueService : Service() {
    companion object { const val ACTION_START = "br.com.smsvivo.app.queue.START"; const val ACTION_PAUSE = "br.com.smsvivo.app.queue.PAUSE"; const val ACTION_STOP = "br.com.smsvivo.app.queue.STOP"; private const val CHANNEL = "sms_queue"; private const val NOTIFICATION_ID = 4101 }
    private val running = AtomicBoolean(false); private val paused = AtomicBoolean(false); private val executor = Executors.newSingleThreadExecutor(); private val database by lazy { SmsDatabase.get(this) }
    private var intervalMs: Long = 0; private var batchSize: Int = 0; private var pendingCount = 0; private var processedCount = 0
    override fun onCreate() { super.onCreate(); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("intervalMs") == true) intervalMs = intent.getLongExtra("intervalMs", 0)
        if (intent?.hasExtra("batchSize") == true) batchSize = intent.getIntExtra("batchSize", 0).coerceAtLeast(0)
        when (intent?.action) { ACTION_PAUSE -> paused.set(true); ACTION_STOP -> { paused.set(true); executor.execute { database.queue().cancelPending(); refreshNotificationCounts(); stopSelf() }; return START_NOT_STICKY }; else -> startProcessing() }
        updateNotification(); return START_STICKY
    }
    private fun startProcessing() {
        if (running.getAndSet(true)) { paused.set(false); return }
        paused.set(false)
        val type = if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(), type) else startForeground(NOTIFICATION_ID, notification())
        executor.execute {
            try { var processed = 0; refreshNotificationCounts(); while (!paused.get() && (batchSize <= 0 || processed < batchSize)) { val item = database.queue().claimNextPending(System.currentTimeMillis()) ?: break; sendItem(item); processed += 1; refreshNotificationCounts(); if (intervalMs > 0) Thread.sleep(intervalMs) } }
            finally { running.set(false); if (!paused.get()) stopSelf() }
        }
    }
    private fun sendItem(item: QueueItemEntity) {
        try {
            val active = getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList?.any { it.subscriptionId == item.subscriptionId } == true
            if (!active) throw IllegalStateException("SIM selecionado não está ativo")
            val sms = getSystemService(SmsManager::class.java).createForSubscriptionId(item.subscriptionId)
            val parts = sms.divideMessage(item.body)
            val sent = PendingIntent.getBroadcast(this, item.requestId.hashCode(), Intent(this, SmsResultReceiver::class.java).setAction(SmsResultReceiver.ACTION_SENT).putExtra("requestId", item.requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val delivered = PendingIntent.getBroadcast(this, item.requestId.hashCode() + 1, Intent(this, SmsResultReceiver::class.java).setAction(SmsResultReceiver.ACTION_DELIVERED).putExtra("requestId", item.requestId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (parts.size == 1) sms.sendTextMessage(item.destination, null, item.body, sent, delivered) else sms.sendMultipartTextMessage(item.destination, null, parts, ArrayList(List(parts.size) { sent }), ArrayList(List(parts.size) { delivered }))
        }
        catch (error: Exception) { database.queue().markResult(item.requestId, QueueStates.FAILED, -1, error.message, System.currentTimeMillis()) }
    }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Fila de SMS", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification { val pause = PendingIntent.getService(this, 1, Intent(this, SmsQueueService::class.java).setAction(if (paused.get()) ACTION_START else ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val stop = PendingIntent.getService(this, 2, Intent(this, SmsQueueService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("SMS Vivo").setContentText("$processedCount processadas · $pendingCount pendentes").setContentIntent(open).setOngoing(true).addAction(NotificationCompat.Action.Builder(0, if (paused.get()) "Retomar" else "Pausar", pause).build()).addAction(NotificationCompat.Action.Builder(0, "Interromper", stop).build()).build() }
    private fun refreshNotificationCounts() { val q = database.queue(); val total = q.total(); pendingCount = q.count(QueueStates.PENDING); processedCount = total - pendingCount - q.count(QueueStates.PROCESSING); updateNotification() }
    private fun updateNotification() { if (running.get()) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { paused.set(true); executor.shutdownNow(); super.onDestroy() }
}
