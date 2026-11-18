package br.com.smsvivo.app.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueTimingPolicyTest {
    @Test fun thirtySecondIntervalStartsAfterConclusiveResult() {
        var clock = 1_000L
        val policy = QueueTimingPolicy { clock }
        assertEquals(0L, policy.delayUntilNextRequest(null, 30_000L))
        clock = 19_000L
        assertEquals(21_000L, policy.delayUntilNextRequest(10_000L, 30_000L))
        clock = 39_999L
        assertEquals(1L, policy.delayUntilNextRequest(10_000L, 30_000L))
        clock = 40_000L
        assertEquals(0L, policy.delayUntilNextRequest(10_000L, 30_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIntervalIsRejected() { QueueTimingPolicy { 0L }.delayUntilNextRequest(null, -1L) }
}
