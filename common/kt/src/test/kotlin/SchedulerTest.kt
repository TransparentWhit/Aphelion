package io.github.maxsh001.aphelion

import io.github.maxsh001.aphelion.utils.AphelionDuration
import io.github.maxsh001.aphelion.utils.Priority
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

class TaskTest {
    @Test
    fun `reject negative delay`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Task(delay = AphelionDuration.Tick(-1)) {}
        }
        assertFailsWith<IllegalArgumentException> {
            Task(delay = AphelionDuration.Time((-1).seconds)) {}
        }
    }
    @Test
    fun `reject negative interval`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Task(interval = AphelionDuration.Tick(-1)) {}
        }
        assertFailsWith<IllegalArgumentException> {
            Task(interval = AphelionDuration.Time((-1).seconds)) {}
        }
    }
    @Test
    fun `isRepeating property`() = runTest {
        val nonRepeatingTask = Task {}
        assertFalse(nonRepeatingTask.isRepeating)
        val repeatingTimeTask = Task(interval = AphelionDuration.Time(1.seconds)) {}
        assertTrue(repeatingTimeTask.isRepeating)
        val repeatingTickTask = Task(interval = AphelionDuration.Tick(1)) {}
        assertTrue(repeatingTickTask.isRepeating)
    }
}

class ScheduledTaskTest {
    @Test
    fun `equality on unique id`() = runTest {
        val task = Task {}
        val scheduledTask1 = ScheduledTask(task, "Task1")
        val scheduledTask2 = ScheduledTask(task, "Task2")
        assertNotEquals(scheduledTask1, scheduledTask2)
        assertEquals(scheduledTask1, scheduledTask1)
        assertNotEquals(scheduledTask1.hashCode(), scheduledTask2.hashCode())
    }
    @Test
    fun `exception handling`() = runTest {
        ScheduledTask(Task {
            throw RuntimeException("Test exception")
        }, "TestTask").execute()
    }
    @Test
    fun cancellation() = runTest {
        val scheduledTask = ScheduledTask(Task {}, "TestTask")
        scheduledTask.cancel()
        val result = scheduledTask.execute()
        assertFalse(result)
    }
    @Test
    fun `cancellation state transitions`() = runTest {
        val scheduledTask = ScheduledTask(Task {}, "TestTask")
        assertFalse(scheduledTask.isCancelled())
        assertTrue(scheduledTask.cancel())
        assertTrue(scheduledTask.isCancelled())
        assertFalse(scheduledTask.cancel())
        assertFalse(scheduledTask.cancel())
    }
    @Test
    fun `cancellation during execution`() = runTest {
        var executionStarted = false
        var executionFinished = false
        val task = Task { scheduledTask ->
            executionStarted = true
            scheduledTask.cancel()
            executionFinished = true
        }
        val scheduledTask = ScheduledTask(task, "CancellableTask")
        val result = scheduledTask.execute()
        assertTrue(executionStarted)
        assertTrue(executionFinished)
        assertFalse(result)
        assertTrue(scheduledTask.isCancelled())
    }
    @Test
    fun `cancellation during execution state transition`() = runTest {
        var executionCount = 0
        val task = Task { scheduledTask ->
            executionCount++
            if (executionCount == 1) {
                scheduledTask.cancel()
            }
        }
        val scheduledTask = ScheduledTask(task, "CancellableTask")
        assertFalse(scheduledTask.execute())
        assertEquals(1, executionCount)
        assertEquals(ScheduledTask.State.CANCELLED, scheduledTask.state.read())
        assertTrue(scheduledTask.isCancelled())
        assertFalse(scheduledTask.cancel())
    }
    @Test
    fun `non-repeating task execution`() = runTest {
        var executed = false
        val task = Task {
            executed = true
        }
        val scheduledTask = ScheduledTask(task, "TestTask")
        scheduledTask.execute()
        assertTrue(executed)
    }
    @Test
    fun `non-repeating task state transitions`() = runTest {
        val scheduledTask = ScheduledTask(Task {}, "NonRepeatingTask")
        assertEquals(ScheduledTask.State.IDLE, scheduledTask.state.read())
        assertFalse(scheduledTask.isCancelled())
        scheduledTask.execute()
        assertEquals(ScheduledTask.State.FINISHED, scheduledTask.state.read())
        assertFalse(scheduledTask.isCancelled())
        assertFalse(scheduledTask.cancel())
    }
    @Test
    fun `repeating task state transitions`() = runTest {
        var executionCount = 0
        val scheduledTask = ScheduledTask(Task(interval = AphelionDuration.Tick(1)) {
            executionCount++
        }, "RepeatingTask")
        assertEquals(ScheduledTask.State.IDLE, scheduledTask.state.read())
        assertTrue(scheduledTask.execute())
        assertEquals(1, executionCount)
        assertEquals(ScheduledTask.State.IDLE, scheduledTask.state.read())
        assertTrue(scheduledTask.execute())
        assertEquals(2, executionCount)
        assertTrue(scheduledTask.cancel())
        assertFalse(scheduledTask.execute())
        assertEquals(ScheduledTask.State.CANCELLED, scheduledTask.state.read())
        assertEquals(2, executionCount)
    }
}

class NoopSchedulerTest {
    private val scheduler = NoopScheduler
    @Test
    fun `return empty tasks`() = runTest {
        assertTrue(scheduler.getTasks().isEmpty())
        assertNull(scheduler.findTask(Uuid.random()))
        assertTrue(scheduler.findTasks("pattern").isEmpty())
    }
    @Test
    fun `reject submission`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            scheduler.submit(Task {}, "TestTask")
        }
    }
}

class StandardSchedulerTest {
    private lateinit var scheduler: StandardScheduler
    @BeforeEach
    fun init() {
        scheduler = object : StandardScheduler() {
            override suspend fun submit(task: Task, name: String): ScheduledTask {
                val scheduled = ScheduledTask(task, name)
                tasks.write {
                    get()[scheduled.uniqueId] = scheduled
                }
                return scheduled
            }
        }
    }
    @Test
    fun `storing and retrieving tasks`() = runTest {
        val scheduledTask = scheduler.submit(Task {}, "TestTask")
        assertEquals(scheduledTask, scheduler.findTask(scheduledTask.uniqueId))
        assertTrue(scheduler.getTasks().contains(scheduledTask))
    }
    @Test
    fun `finding tasks by pattern`() = runTest {
        val pineappleTask1 = scheduler.submit(Task {}, "PineappleTask")
        val taroTask = scheduler.submit(Task {}, "TaroTask")
        val pineappleTask2 = scheduler.submit(Task {}, "PineapplePieTask")
        val pineappleTasks = scheduler.findTasks("Pineapple.*")
        assertEquals(2, pineappleTasks.size)
        assertTrue(pineappleTasks.contains(pineappleTask1))
        assertTrue(pineappleTasks.contains(pineappleTask2))
        assertFalse(pineappleTasks.contains(taroTask))
    }
    @Test
    fun `empty tasks by invalid pattern`() = runTest {
        scheduler.submit(Task {}, "TestTask")
        val result = scheduler.findTasks("invalid[pattern")
        assertTrue(result.isEmpty())
    }
    @Test
    fun `plusAssign operator`() = runTest {
        scheduler += Task {}
        assertEquals(1, scheduler.getTasks().size)
    }
    @Test
    fun `get operator`() = runTest {
        assertNull(scheduler[Uuid.random()])
        val scheduledTask = scheduler.submit(Task {}, "TestTask")
        assertEquals(scheduledTask, scheduler[scheduledTask.uniqueId])
    }
}

class AsyncSchedulerTest {
    private val scheduler = AsyncScheduler
    @Test
    fun `reject tick-based tasks`() = runTest {
        val tickTask = Task(delay = AphelionDuration.Tick(1)) {}
        assertFailsWith<IllegalArgumentException> {
            scheduler.submit(tickTask, "TickTask")
        }
    }
}

class TickedSchedulerTest {
    private lateinit var timeSource: TestTimeSource
    private lateinit var scheduler: TickedScheduler
    @BeforeEach
    fun init() {
        timeSource = TestTimeSource()
        scheduler = TickedScheduler(timeSource)
    }
    @Test
    fun `time-based task execution`() = runTest {
        var executed = false
        val task = Task(delay = AphelionDuration.Time(10.milliseconds)) {
            executed = true
        }
        scheduler.submit(task, "TimeTask")
        assertFalse(executed)
        timeSource += 15.milliseconds
        scheduler.tick()
        assertTrue(executed)
    }
    @Test
    fun `tick-based task execution`() = runTest {
        var executionCount = 0
        val task = Task(delay = AphelionDuration.Tick(2)) {
            executionCount++
        }
        scheduler.submit(task, "TickTask")
        scheduler.tick()
        scheduler.tick()
        assertEquals(0, executionCount)
        scheduler.tick()
        assertEquals(1, executionCount)
    }
    @Test
    fun `repeating time-based task`() = runTest {
        var executionCount = 0
        val task = Task(delay = AphelionDuration.Time(10.milliseconds), interval = AphelionDuration.Time(5.milliseconds)) {
            executionCount++
        }
        scheduler.submit(task, "RepeatingTimeTask")
        timeSource += 15.milliseconds
        scheduler.tick()
        assertEquals(1, executionCount)
        timeSource += 10.milliseconds
        scheduler.tick()
        assertEquals(2, executionCount)
    }
    @Test
    fun `repeating tick-based task`() = runTest {
        var executionCount = 0
        val task = Task(delay = AphelionDuration.Tick(1), interval = AphelionDuration.Tick(2)) {
            executionCount++
        }
        scheduler.submit(task, "RepeatingTickTask")
        scheduler.tick()
        assertEquals(0, executionCount)
        scheduler.tick()
        assertEquals(1, executionCount)
        scheduler.tick()
        scheduler.tick()
        assertEquals(2, executionCount)
    }
    @Test
    fun `cancellation during execution`() = runTest {
        var executionCount = 0
        val task = Task(delay = AphelionDuration.Tick(1), interval = AphelionDuration.Tick(1)) { scheduledTask ->
            executionCount++
            if (executionCount == 2) {
                scheduledTask.cancel()
            }
        }
        val scheduledTask = scheduler.submit(task, "CancellableRepeatingTask")
        scheduler.tick()
        scheduler.tick()
        assertEquals(1, executionCount)
        assertFalse(scheduledTask.isCancelled())
        scheduler.tick()
        assertEquals(2, executionCount)
        assertTrue(scheduledTask.isCancelled())
        scheduler.tick()
        assertEquals(2, executionCount)
    }
    @Test
    fun `priority execution`() = runTest {
        val executionOrder = mutableListOf<String>()
        for (priority in Priority.entries.toTypedArray().apply(Array<Priority>::shuffle)) {
            scheduler.submit(Task(priority) {
                executionOrder.add(priority.name)
            }, "${priority.name}Priority")
        }
        scheduler.tick()
        assertEquals(listOf("PRE", "AFTER_PRE", "FIRST", "EARLY", "DEFAULT", "LATE", "LAST", "BEFORE_POST", "POST"), executionOrder)
    }
    @Test
    fun `mixed time and tick tasks`() = runTest {
        var timeExecuted = false
        var tickExecuted = false
        val timeTask = Task(delay = AphelionDuration.Time(10.milliseconds)) {
            timeExecuted = true
        }
        val tickTask = Task(delay = AphelionDuration.Tick(2)) {
            tickExecuted = true
        }
        scheduler.submit(timeTask, "MixedTimeTask")
        scheduler.submit(tickTask, "MixedTickTask")
        scheduler.tick()
        assertFalse(timeExecuted)
        assertFalse(tickExecuted)
        timeSource += 15.milliseconds
        scheduler.tick()
        assertTrue(timeExecuted)
        assertFalse(tickExecuted)
        scheduler.tick()
        assertTrue(tickExecuted)
    }
    @Test
    fun `task removal after completion`() = runTest {
        var executionCount = 0
        val task = Task(delay = AphelionDuration.Tick(1)) {
            executionCount++
        }
        val scheduledTask = scheduler.submit(task, "TestTask")
        scheduler.tick()
        scheduler.tick()
        assertEquals(1, executionCount)
        assertNull(scheduler.findTask(scheduledTask.uniqueId))
        assertFalse(scheduler.getTasks().contains(scheduledTask))
    }
}
