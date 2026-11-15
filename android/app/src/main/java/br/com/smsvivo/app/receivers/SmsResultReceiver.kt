package br.com.smsvivo.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.smsvivo.app.database.QueueStates
import br.com.smsvivo.app.database.SmsDatabase
import br.com.smsvivo.app.database.DiagnosticEventEntity

class SmsResultReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SENT = "br.com.smsvivo.app.SMS_SENT"
        const val ACTION_DELIVERED = "br.com.smsvivo.app.SMS_DELIVERED"
        const val ACTION_UI_RESULT = "br.com.smsvivo.app.SMS_RESULT_EVENT"
    }
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("requestId") ?: return
        val code = resultCode
        val state = if (intent.action == ACTION_DELIVERED) QueueStates.DELIVERED else if (code == android.app.Activity.RESULT_OK) QueueStates.SENT else QueueStates.FAILED
        val pending = goAsync()
        Thread {
            try {
                val database = SmsDatabase.get(context)
                val now = System.currentTimeMillis()
                val queue = database.queue()
                val item = queue.byRequestId(requestId) ?: return@Thread
                val finalState = if (state == QueueStates.DELIVERED) {
                    queue.markDeliveredPart(requestId, now)
                    queue.byRequestId(requestId)?.state
                } else if (state == QueueStates.SENT) {
                    queue.markSentPart(requestId, now)
                    queue.byRequestId(requestId)?.state
                } else {
                    queue.markResult(requestId, state, code, "Código Android $code", now)
                    state
                }
                database.diagnostics().insert(DiagnosticEventEntity(category = "SMS_RESULT", code = code, message = "$state:$requestId"))
                if (finalState == QueueStates.SENT || finalState == QueueStates.DELIVERED || finalState == QueueStates.FAILED) {
                    context.sendBroadcast(Intent(ACTION_UI_RESULT).setPackage(context.packageName).apply { putExtra("requestId", requestId); putExtra("state", finalState); putExtra("code", code); putExtra("sentAt", java.time.Instant.now().toString()) })
                }
            } finally { pending.finish() }
        }.start()
    }
}
