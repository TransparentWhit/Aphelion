package io.github.maxsh001.aphelion

import io.github.maxsh001.aphelion.utils.AphelionDuration
import io.github.maxsh001.aphelion.utils.Nameable
import io.github.maxsh001.aphelion.utils.Priority
import io.github.maxsh001.aphelion.utils.UniquelyIdentifiable
import io.github.maxsh001.aphelion.utils.sync.readWriteLocked
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

@ConsistentCopyVisibility
data class Task private constructor(
    internal val executor: suspend (ScheduledTask) -> Unit,
    val delay: AphelionDuration,
    val interval: AphelionDuration,
    val priority: Priority,
) {
    val isRepeating get() = interval.isPositive()
    init {
        require(!delay.isNegative()) { "Delay must not be negative" }
        require(!interval.isNegative()) { "Interval must not be negative" }
    }
    constructor(priority: Priority = Priority.DEFAULT, executor: suspend (ScheduledTask) -> Unit) : this(executor, AphelionDuration.Tick(0), AphelionDuration.Tick(0), priority)
    constructor(priority: Priority = Priority.DEFAULT, delay: AphelionDuration.Time = AphelionDuration.Time(Duration.ZERO), interval: AphelionDuration.Time = AphelionDuration.Time(Duration.ZERO), executor: suspend (ScheduledTask) -> Unit) : this(executor, delay as AphelionDuration, interval as AphelionDuration, priority)
    constructor(priority: Priority = Priority.DEFAULT, delay: AphelionDuration.Tick = AphelionDuration.Tick(0), interval: AphelionDuration.Tick = AphelionDuration.Tick(0), executor: suspend (ScheduledTask) -> Unit) : this(executor, delay as AphelionDuration, interval as AphelionDuration, priority)
}
class ScheduledTask internal constructor(
    val task: Task,
    override val name: String,
    override val uniqueId: Uuid = Uuid.random(),
) : Nameable, UniquelyIdentifiable {
    internal val state = State.IDLE.readWriteLocked()
    suspend fun isCancelled() = when (state.read()) {
        State.CANCELLED, State.CANCELLED_RUNNING -> true
        State.FINISHED, State.IDLE, State.RUNNING -> false
    }
    suspend fun cancel() = state.write { when (get()) {
        State.CANCELLED, State.CANCELLED_RUNNING, State.FINISHED -> false
        State.IDLE -> {
            set(State.CANCELLED)
            true
        }
        State.RUNNING -> {
            set(State.CANCELLED_RUNNING)
            true
        }
    }}
    internal suspend fun execute(): Boolean {
        if (state.write { when (get()) {
            State.CANCELLED -> true
            State.IDLE -> {
                set(State.RUNNING)
                false
            }
            else -> error("Unexpected task state ${get()}")
        }}) return false
        try {
            task.executor(this)
        } catch (t: Throwable) {
            // todo
        }
        return state.write { when (get()) {
            State.RUNNING -> {
                if (task.interval.isPositive()) {
                    set(State.IDLE); true
                } else {
                    set(State.FINISHED); false
                }
            }
            State.CANCELLED_RUNNING -> {
                set(State.CANCELLED)
                false
            }
            else -> error("Unexpected task state ${get()}")
        }}
    }
    override fun hashCode() = uniqueId.hashCode()
    override fun equals(other: Any?) = other is ScheduledTask && other.uniqueId == uniqueId
    enum class State {
        /**
         * The task is not executing and will not begin execution in the future.
         */
        CANCELLED,
        /**
         * The task is currently executing, but future executions are cancelled and will not occur.
         */
        CANCELLED_RUNNING,
        /**
         * The task is not repeating, and the task finished executing.
         */
        FINISHED,
        /**
         * The task is currently not executing, but may begin execution in the future.
         */
        IDLE,
        /**
         * The task is currently executing.
         */
        RUNNING,
    }
}

interface SchedulerHolder {
    val scheduler: Scheduler
}
interface Scheduler : SchedulerHolder {
    override val scheduler get() = this
    suspend fun findTask(id: Uuid): ScheduledTask?
    suspend operator fun get(id: Uuid) = findTask(id)
    suspend fun findTasks(pattern: String): Set<ScheduledTask>
    suspend fun getTasks(): Set<ScheduledTask>
    suspend fun submit(task: Task, name: String = "ScheduledTask"): ScheduledTask
    suspend operator fun plusAssign(task: Task) { submit(task) }
}

object NoopScheduler : Scheduler {
    override suspend fun getTasks() = emptySet<ScheduledTask>()
    override suspend fun findTask(id: Uuid) = null
    override suspend fun findTasks(pattern: String) = emptySet<ScheduledTask>()
    override suspend fun submit(task: Task, name: String) = throw UnsupportedOperationException()
}

abstract class StandardScheduler : Scheduler {
    protected val tasks = mutableMapOf<Uuid, ScheduledTask>().readWriteLocked()
    override suspend fun getTasks() = tasks.read { get().values.toSet() }
    override suspend fun findTask(id: Uuid) = tasks.read { get()[id] }
    override suspend fun findTasks(pattern: String) = runCatching {
        Pattern.compile(pattern)
    }.fold({ compiledPattern ->
        tasks.read { get().values.filter { compiledPattern.matcher(it.name).matches() }.toSet() }
    }, {
        emptySet()
    })
}

/**
 * Does not respect [Task.priority].
 *
 * Does not support [AphelionDuration.Tick].
 */
object AsyncScheduler : StandardScheduler() {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisor)
    @OptIn(ObsoleteCoroutinesApi::class)
    override suspend fun submit(task: Task, name: String): ScheduledTask {
        require(task.interval is AphelionDuration.Time) { "AsyncScheduler does not support specifying time by ticks" }
        val scheduled = ScheduledTask(task, name)
        tasks.write {
            get()[scheduled.uniqueId] = scheduled
        }
        scope.launch {
            val ticker = ticker(task.interval.duration.inWholeMilliseconds, (task.delay as AphelionDuration.Time).duration.inWholeMilliseconds)
            while (ticker.receiveCatching().isSuccess) {
                if (!scheduled.execute()) {
                    tasks.write {
                        get() -= scheduled.uniqueId
                    }
                    break
                }
            }
            ticker.cancel()
        }
        return scheduled
    }
}

class TickedScheduler(
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
) : StandardScheduler() {
    private var currentTick = 0
    private val timeTasks = PriorityQueue<ScheduledTimeTask>()
    private val tickTasks = PriorityQueue<ScheduledTickTask>()
    override suspend fun submit(task: Task, name: String): ScheduledTask {
        val scheduled = ScheduledTask(task, name)
        tasks.write {
            get()[scheduled.uniqueId] = scheduled
            when (task.delay) {
                is AphelionDuration.Time -> timeTasks += ScheduledTimeTask(scheduled, timeSource.markNow() + task.delay.duration)
                is AphelionDuration.Tick -> tickTasks += ScheduledTickTask(scheduled, currentTick + task.delay.tick)
            }
        }
        return scheduled
    }
    suspend fun tick() {
        tasks.write {
            val now = timeSource.markNow()
            val executions = mutableListOf<TickedSchedulerTask>()
            while (timeTasks.isNotEmpty() && timeTasks.peek().scheduledExecution <= now) {
                executions += timeTasks.poll()
            }
            while (tickTasks.isNotEmpty() && tickTasks.peek().scheduledExecution == currentTick) {
                executions += tickTasks.poll()
            }
            executions.sortWith(TickedSchedulerTask.orderByPriority)
            val rescheduledTimeTasks = mutableListOf<ScheduledTimeTask>()
            val rescheduledTickTasks = mutableListOf<ScheduledTickTask>()
            for (task in executions) {
                if (task.task.execute()) {
                    when (task) {
                        is ScheduledTimeTask -> rescheduledTimeTasks += task.next(now)
                        is ScheduledTickTask -> rescheduledTickTasks += task.next()
                    }
                } else {
                    get() -= task.task.uniqueId
                }
            }
            timeTasks += rescheduledTimeTasks
            tickTasks += rescheduledTickTasks
            currentTick++
        }
    }
    private sealed class TickedSchedulerTask {
        abstract val task: ScheduledTask
        companion object {
            val orderByPriority = compareBy<TickedSchedulerTask> { it.task.task.priority }
        }
    }
    private class ScheduledTimeTask(
        override val task: ScheduledTask,
        val scheduledExecution: ComparableTimeMark,
        val interval: Duration = (task.task.interval as AphelionDuration.Time).duration
    ) : TickedSchedulerTask(), Comparable<ScheduledTimeTask> {
        fun next(currentTime: ComparableTimeMark) = ScheduledTimeTask(task, currentTime + interval, interval)
        override fun compareTo(other: ScheduledTimeTask) = scheduledExecution.compareTo(other.scheduledExecution)
    }
    private class ScheduledTickTask(
        override val task: ScheduledTask,
        val scheduledExecution: Int,
        val interval: Int = (task.task.interval as AphelionDuration.Tick).tick
    ) : TickedSchedulerTask(), Comparable<ScheduledTickTask> {
        fun next() = ScheduledTickTask(task, scheduledExecution + interval, interval)
        override fun compareTo(other: ScheduledTickTask) = scheduledExecution.compareTo(other.scheduledExecution)
    }
}
