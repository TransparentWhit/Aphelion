@file:OptIn(ExperimentalContracts::class)

package io.github.maxsh001.aphelion.utils.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

sealed interface SyncedValScope<T> {
    fun get(): T
}
sealed interface SyncedVarScope<T> : SyncedValScope<T> {
    fun set(value: T)
}
@PublishedApi
internal class SyncedScope<T>(
    var value: T,
) : SyncedVarScope<T> {
    override fun set(value: T) {
        this.value = value
    }
    override fun get() = value
}

fun <T> T.mutexed(lock: Mutex = Mutex()): Mutexed<T> = Mutexed(lock, this)
fun <T> T.readWriteLocked(lock: ReadWriteLock = ReadWriteLock()): ReadWriteLocked<T> = ReadWriteLocked(lock, this)

class Mutexed<T>(
    private val lock: Mutex,
    private var value: T,
) {
    suspend fun read(): T = lock { get() }
    suspend fun write(value: T) = lock { set(value) }
    suspend fun <R> lock(action: suspend SyncedVarScope<T>.() -> R): R {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        return lock.withLock {
            val scope = SyncedScope(value)
            val result = scope.action()
            value = scope.value
            result
        }
    }
}

class ReadWriteLocked<T>(
    private val lock: ReadWriteLock,
    private var value: T,
) {
    suspend inline fun read(): T = read { get() }
    suspend inline fun <R> read(action: suspend SyncedValScope<T>.() -> R): R {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        return lock.withReadLock {
            SyncedScope(value).action()
        }
    }
    suspend inline fun write(value: T) = write { set(value) }
    suspend fun <R> write(action: suspend SyncedVarScope<T>.() -> R): R {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        return lock.withWriteLock {
            val scope = SyncedScope(value)
            val result = scope.action()
            value = scope.value
            result
        }
    }
}
