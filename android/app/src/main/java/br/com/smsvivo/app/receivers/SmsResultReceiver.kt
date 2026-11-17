package br.com.smsvivo.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.smsvivo.app.database.QueueStates
import br.com.smsvivo.app.database.SmsDatabase
import br.com.smsvivo.app.database.DiagnosticEventEntity
import br.com.smsvivo.app.database.SmsPartCallbackEntity
import br.com.smsvivo.app.database.SmsResultPhase

class SmsResultReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SENT = "br.com.smsvivo.app.SMS_SENT"
        const val ACTION_DELIVERED = "br.com.smsvivo.app.SMS_DELIVERED"
        const val ACTION_UI_RESULT = "br.com.smsvivo.app.SMS_RESULT_EVENT"
    }
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("requestId") ?: return
        val code = resultCode
        val isDeliveredCallback = intent.action == ACTION_DELIVERED
        val state = if (isDeliveredCallback && code == android.app.Activity.RESULT_OK) QueueStates.DELIVERED else if (!isDeliveredCallback && code == android.app.Activity.RESULT_OK) QueueStates.SENT else QueueStates.FAILED
        val pending = goAsync()
        Thread {
            try {
                val database = SmsDatabase.get(context)
                val now = System.currentTimeMillis()
                val queue = database.queue()
                val item = queue.byRequestId(requestId) ?: return@Thread
                val partIndex = intent.getIntExtra("partIndex", 0).coerceAtLeast(0)
                val finalState = if (state == QueueStates.DELIVERED || state == QueueStates.SENT) {
                    val phase = if (state == QueueStates.DELIVERED) SmsResultPhase.DELIVERED else SmsResultPhase.SENT
                    database.recordPartCallback(SmsPartCallbackEntity(requestId, partIndex, phase, code, now))?.state
                } else {
                    val phase = if (isDeliveredCallback) SmsResultPhase.DELIVERY_FAILED else SmsResultPhase.SENT_FAILED
                    database.recordPartFailure(SmsPartCallbackEntity(requestId, partIndex, phase, code, now))?.state
                }
                database.diagnostics().insert(DiagnosticEventEntity(category = "SMS_RESULT", code = code, message = "$state:$requestId"))
                if (finalState == QueueStates.SENT || finalState == QueueStates.DELIVERED || finalState == QueueStates.FAILED) {
                    context.sendBroadcast(Intent(ACTION_UI_RESULT).setPackage(context.packageName).apply { putExtra("requestId", requestId); putExtra("state", finalState); putExtra("code", code); putExtra("sentAt", java.time.Instant.now().toString()) })
                }
            } finally { pending.finish() }
        }.start()
    }
}
