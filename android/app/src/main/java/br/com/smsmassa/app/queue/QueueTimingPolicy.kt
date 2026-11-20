package br.com.smsmassa.app.queue

class QueueTimingPolicy(private val now: () -> Long = { System.currentTimeMillis() }) {
    fun delayUntilNextRequest(lastConclusiveAt: Long?, intervalMs: Long): Long {
        require(intervalMs >= 0) { "intervalMs deve ser maior ou igual a zero" }
        if (lastConclusiveAt == null) return 0
        return (intervalMs - (now() - lastConclusiveAt)).coerceAtLeast(0)
    }
}
