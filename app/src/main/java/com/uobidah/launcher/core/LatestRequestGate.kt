package com.uobidah.launcher.core

/**
 * Thread-safe last-request-wins gate for asynchronous UI/data loads.
 * runIfCurrent() serializes the final commit with begin()/invalidate(), so an older
 * response cannot win a race between its last token check and the actual file/UI commit.
 */
class LatestRequestGate {
    private val lock = Any()
    private var generation = 0L

    fun begin(): Long = synchronized(lock) { ++generation }

    fun invalidate(): Long = synchronized(lock) { ++generation }

    fun isCurrent(token: Long): Boolean = synchronized(lock) { token == generation }

    fun runIfCurrent(token: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (token != generation) return@synchronized false
        action()
        true
    }
}
