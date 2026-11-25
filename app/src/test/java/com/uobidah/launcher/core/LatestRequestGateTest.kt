package com.uobidah.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class LatestRequestGateTest {
    @Test fun newerRequestInvalidatesOlderRequest() {
        val gate = LatestRequestGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test fun staleRequestCannotCommit() {
        val gate = LatestRequestGate()
        val old = gate.begin()
        val current = gate.begin()
        var committed = false
        assertFalse(gate.runIfCurrent(old) { committed = true })
        assertTrue(gate.runIfCurrent(current) { committed = true })
        assertTrue(committed)
    }

    @Test fun invalidationStopsCallbacksAfterActivityExit() {
        val gate = LatestRequestGate()
        val token = gate.begin()
        gate.invalidate()
        var called = false
        assertFalse(gate.runIfCurrent(token) { called = true })
        assertFalse(called)
    }

    @Test fun concurrentNewRequestAndCommitAreSerialized() {
        repeat(100) {
            val gate = LatestRequestGate()
            val old = gate.begin()
            val start = CountDownLatch(1)
            val committed = AtomicInteger(0)
            val worker = Thread {
                start.await()
                gate.runIfCurrent(old) { committed.incrementAndGet() }
            }
            worker.start()
            start.countDown()
            gate.begin()
            worker.join()
            assertTrue(committed.get() in 0..1)
            assertFalse(gate.isCurrent(old))
        }
    }
}
