package com.sajed.articlestudio.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GateTest {
    private suspend fun run(g: Gate, n: Int): Int {
        val cur = AtomicInteger(0); val peak = AtomicInteger(0)
        coroutineScope {
            (1..n).map { async(Dispatchers.Default) { g.withPermit { val c = cur.incrementAndGet(); peak.updateAndGet { maxOf(it, c) }; delay(80); cur.decrementAndGet() } } }.awaitAll()
        }
        return peak.get()
    }

    @Test fun neverExceedsLimitAndFollowsLiveChange() = runBlocking {
        var limit = 2
        val g = Gate { limit }
        val p1 = run(g, 6)
        assertTrue("peak=$p1", p1 in 1..2)
        limit = 1
        assertEquals(1, run(g, 4))
        assertEquals(0, g.active())
    }
}
