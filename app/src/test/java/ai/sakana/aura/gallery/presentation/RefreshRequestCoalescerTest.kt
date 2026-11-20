package ai.sakana.aura.gallery.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshRequestCoalescerTest {
    @Test
    fun `multiple overlapping requests collapse into one follow up pass`() {
        val coalescer = RefreshRequestCoalescer()

        coalescer.requestAnotherPass()
        coalescer.requestAnotherPass()
        coalescer.requestAnotherPass()

        assertTrue(coalescer.consumeQueuedPass())
        assertFalse(coalescer.consumeQueuedPass())
    }

    @Test
    fun `request arriving after a pass is consumed queues the next pass`() {
        val coalescer = RefreshRequestCoalescer()

        coalescer.requestAnotherPass()
        assertTrue(coalescer.consumeQueuedPass())

        coalescer.requestAnotherPass()
        assertTrue(coalescer.consumeQueuedPass())
        assertFalse(coalescer.consumeQueuedPass())
    }

    @Test
    fun `new refresh clears stale queued request before starting`() {
        val coalescer = RefreshRequestCoalescer()
        coalescer.requestAnotherPass()

        coalescer.reset()

        assertFalse(coalescer.consumeQueuedPass())
    }
}
