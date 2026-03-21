package io.github.maxsh001.aphelion.utils.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

interface ReadWriteLock {
    suspend fun readLock()
    suspend fun readUnlock()
    suspend fun writeLock()
    suspend fun writeUnlock()
}

fun ReadWriteLock(): ReadWriteLock = FairReadWriteLock()

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> ReadWriteLock.withReadLock(action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    readLock()
    return try {
        action()
    } finally {
        readUnlock()
    }
}
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> ReadWriteLock.withWriteLock(action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    writeLock()
    return try {
        action()
    } finally {
        writeUnlock()
    }
}

internal class FairReadWriteLock : ReadWriteLock {
    private val mutex = Mutex()
    private var nextTicket = 0
    private var currentServing = 0
    private var activeReaders = 0
    private var activeWriter = false
    private val waiters = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private val areWriters = mutableMapOf<Int, Boolean>()
    private fun canProceed(isWriter: Boolean) = if (isWriter) {
        activeReaders == 0 && !activeWriter
    } else {
        !activeWriter
    }
    private suspend fun acquireLock(isWriter: Boolean) {
        if (isWriter) {
            activeWriter = true
        } else {
            activeReaders++
        }
        currentServing++
        tryWakeNextWaiters()
    }
    private suspend fun acquireLockOrQueue(ticket: Int, isWriter: Boolean) = mutex.withLock {
        if (canProceed(isWriter) && currentServing == ticket) {
            acquireLock(isWriter)
            null
        } else {
            val waiter = CompletableDeferred<Unit>()
            waiters[ticket] = waiter
            waiter
        }
    }
    private suspend fun waitForTurn(ticket: Int, isWriter: Boolean, waiter: CompletableDeferred<Unit>) {
        try {
            waiter.await() // When the waiter is woken, the mutex is locked
            waiters.remove(ticket)
            areWriters.remove(ticket)
            acquireLock(isWriter)
            mutex.unlock()
        } catch (exception: CancellationException) {
            mutex.withLock {
                if (waiters[ticket] === waiter) {
                    waiters.remove(ticket)
                    areWriters.remove(ticket)
                    if (currentServing == ticket) {
                        currentServing++
                        tryWakeNextWaiters()
                    }
                }
            }
            throw exception
        }
    }
    private suspend fun lock(isWriter: Boolean) {
        val ticket: Int
        mutex.withLock {
            ticket = nextTicket++
            areWriters[ticket] = isWriter
        }
        acquireLockOrQueue(ticket, isWriter)?.let {
            waitForTurn(ticket, isWriter, it)
        }
    }
    private suspend fun unlock(isWriter: Boolean) = mutex.withLock {
        if (isWriter) {
            check(activeWriter) { "Write lock underflow" }
            activeWriter = false
        } else {
            check(activeReaders > 0) { "Read lock underflow" }
            activeReaders--
        }
        tryWakeNextWaiters()
    }
    private suspend fun tryWakeNextWaiters() {
        while (true) {
            val waiter = waiters[currentServing] ?: break
            val isWriter = areWriters[currentServing] ?: break
            if (!waiter.isActive) {
                waiters.remove(currentServing)
                areWriters.remove(currentServing)
                currentServing++
                continue
            }
            if (canProceed(isWriter)) {
                waiter.complete(Unit) // The waiter unlocks the mutex when it is done
                mutex.lock()
                if (isWriter) {
                    break
                }
            } else {
                break
            }
        }
    }
    override suspend fun readLock() = lock(false)
    override suspend fun writeLock() = lock(true)
    override suspend fun readUnlock() = unlock(false)
    override suspend fun writeUnlock() = unlock(true)
}
