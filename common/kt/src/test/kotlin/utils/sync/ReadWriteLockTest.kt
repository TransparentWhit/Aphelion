package io.github.maxsh001.aphelion.utils.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class FairReadWriteLockTest {
    private lateinit var lock: FairReadWriteLock
    @BeforeEach
    fun init() {
        lock = FairReadWriteLock()
    }
    @Test
    fun `concurrent readers`() = runTest {
        val readersInCriticalSection = AtomicInteger(0)
        val maxConcurrentReaders = AtomicInteger(0)
        val readerJobs = List(10) {
            launch {
                lock.withReadLock {
                    val current = readersInCriticalSection.incrementAndGet()
                    maxConcurrentReaders.updateAndGet { max -> maxOf(max, current) }
                    delay(10)
                    readersInCriticalSection.decrementAndGet()
                }
            }
        }
        readerJobs.joinAll()
        assertTrue(maxConcurrentReaders.get() > 1, "Multiple readers should be able to read concurrently")
    }
    @Test
    fun `exclusive writers`() = runTest {
        var readersInCriticalSection = 0
        var writersInCriticalSection = 0
        val writerJob = launch {
            lock.withWriteLock {
                writersInCriticalSection++
                assertEquals(1, writersInCriticalSection, "Only one writer should be in critical section")
                assertEquals(0, readersInCriticalSection, "No readers should be in critical section during write")
                delay(100)
                writersInCriticalSection--
            }
        }
        delay(10)
        val readerJob = launch {
            lock.withReadLock {
                readersInCriticalSection++
            }
        }
        writerJob.join()
        readerJob.join()
        assertEquals(0, writersInCriticalSection)
        assertEquals(1, readersInCriticalSection)
    }
    @Test
    fun `mutually exclusive writers`() = runTest {
        var concurrentWriters = 0
        val maxConcurrentWriters = AtomicInteger(0)
        val writerJobs = List(5) {
            launch {
                lock.withWriteLock {
                    val current = concurrentWriters++
                    maxConcurrentWriters.updateAndGet { max -> maxOf(max, current + 1) }
                    delay(50)
                    concurrentWriters--
                }
            }
        }
        writerJobs.joinAll()
        assertEquals(1, maxConcurrentWriters.get(), "Only one writer should be in critical section at a time")
    }
    @Test
    fun `readers wait for writer`() = runTest {
        var readValue = 0
        val readerStarted = CompletableDeferred<Unit>()
        val writerProceed = CompletableDeferred<Unit>()
        val writerJob = launch {
            lock.withWriteLock {
                readValue = 42
                readerStarted.complete(Unit)
                writerProceed.await()
            }
        }
        readerStarted.await()
        val readerJob = launch {
            lock.withReadLock {
                assertEquals(42, readValue, "Reader should see value written by writer")
            }
        }
        assertFalse(readerJob.isCompleted, "Reader should wait for writer to release lock")
        writerProceed.complete(Unit)
        writerJob.join()
        readerJob.join()
    }
    @Test
    fun `writers wait for readers`() = runTest {
        var value = 0
        val readerProceed = CompletableDeferred<Unit>()
        val readerStarted = CompletableDeferred<Unit>()
        val readerJob = launch {
            lock.withReadLock {
                readerStarted.complete(Unit)
                readerProceed.await()
            }
        }
        readerStarted.await()
        val writerJob = launch {
            lock.withWriteLock {
                value = 100
            }
        }
        delay(50)
        assertFalse(writerJob.isCompleted, "Writer should wait for readers to release lock")
        readerProceed.complete(Unit)
        readerJob.join()
        writerJob.join()
        assertEquals(100, value, "Writer should eventually update the value")
    }
    @Test
    fun `readers wait after writer`() = runTest {
        val executionOrder = mutableListOf<String>()
        val readerStarted = CompletableDeferred<Unit>()
        val writerStarted = CompletableDeferred<Unit>()
        val reader1 = launch {
            lock.withReadLock {
                executionOrder.add("reader1")
                readerStarted.complete(Unit)
                delay(100)
            }
        }
        readerStarted.await()
        val writer = launch {
            writerStarted.complete(Unit)
            lock.withWriteLock {
                executionOrder.add("writer")
            }
        }
        writerStarted.await()
        delay(10)
        val reader2 = launch {
            lock.withReadLock {
                executionOrder.add("reader2")
            }
        }
        reader1.join()
        writer.join()
        reader2.join()
        assertEquals(listOf("reader1", "writer", "reader2"), executionOrder)
    }
    @Test
    fun `readUnlock underflow`() = runTest {
        assertFailsWith<IllegalStateException> {
            lock.readUnlock()
        }
    }
    @Test
    fun `writeUnlock underflow`() = runTest {
        assertFailsWith<IllegalStateException> {
            lock.writeUnlock()
        }
    }
    @Test
    fun `cancellation releases read lock`() = runTest {
        var readersInCriticalSection = 0
        val readerJob = launch {
            lock.withReadLock {
                readersInCriticalSection++
                delay(10.seconds)
            }
        }
        delay(100)
        assertEquals(1, readersInCriticalSection)
        readerJob.cancelAndJoin()
        val writerJob = launch {
            lock.withWriteLock {
                readersInCriticalSection = 100
            }
        }
        writerJob.join()
        assertEquals(100, readersInCriticalSection, "Writer should have acquired lock after reader cancellation")
    }
    @Test
    fun `cancellation releases write lock`() = runTest {
        var value = 0
        val writerJob = launch {
            lock.withWriteLock {
                value = 50
                delay(10.seconds)
            }
        }
        delay(100)
        assertEquals(50, value)
        writerJob.cancelAndJoin()
        val readerJob = launch {
            lock.withReadLock {
                assertEquals(50, value)
            }
        }
        readerJob.join()
    }
    @Test
    fun `stress test`() = runTest {
        var counter = 0
        val iterations = 50
        val readers = 5
        val writers = 3
        val readerJobs = List(readers) {
            launch {
                repeat(iterations) {
                    lock.withReadLock {
                        assertTrue(counter >= 0)
                        delay(1)
                    }
                }
            }
        }
        val writerJobs = List(writers) {
            launch {
                repeat(iterations) {
                    lock.withWriteLock {
                        val current = counter
                        delay(1)
                        counter = current + 1
                    }
                }
            }
        }
        readerJobs.joinAll()
        writerJobs.joinAll()
        assertEquals(writers * iterations, counter, "Counter should reflect all write operations")
    }
    @Test
    fun `lock unlock sequence`() = runTest {
        lock.readLock()
        lock.readLock()
        lock.readUnlock()
        lock.readUnlock()
        lock.writeLock()
        lock.writeUnlock()
        lock.readLock()
        lock.readUnlock()
    }
}
