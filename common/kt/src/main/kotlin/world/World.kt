package io.github.maxsh001.aphelion.world

import io.github.maxsh001.aphelion.SchedulerHolder
import io.github.maxsh001.aphelion.TickedScheduler
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface Tickable<C> : SchedulerHolder {
    val children: Sequence<Tickable<C>>
    val mode get() = Mode.SEQUENTIAL
    @OptIn(ObsoleteCoroutinesApi::class)
    suspend fun tick(context: C, tickDuration: Long): Unit = coroutineScope {
        when (mode) {
            Mode.ASYNCHRONOUS -> {
                for (child in children) {
                    launch {
                        child.tick(context, tickDuration)
                    }
                }
            }
            Mode.SEQUENTIAL, Mode.PARALLEL -> {}
        }
        val ticker = ticker(tickDuration, 0)
        while (ticker.receiveCatching().isSuccess) {
            doTick(context)
        }
    }
    private suspend fun doTick(context: C) {
        (scheduler as? TickedScheduler)?.tick()
        when (mode) {
            Mode.SEQUENTIAL -> {
                for (child in children) {
                    child.doTick(context)
                }
            }
            Mode.PARALLEL -> coroutineScope {
                for (child in children) {
                    launch {
                        child.doTick(context)
                    }
                }
            }
            Mode.ASYNCHRONOUS -> {}
        }
        onTick(context)
    }
    suspend fun onTick(context: C)
    enum class Mode {
        SEQUENTIAL,
        PARALLEL,
        ASYNCHRONOUS,
    }
}

open class StarSystem : Tickable<StarSystem> {
    companion object {
        const val ARENA_SIZE = 8192f
    }
    override val scheduler = TickedScheduler()
    val entities = mutableSetOf<Entity>()
    final override val children get() = sequence {
        yieldAll(entities)
    }
    override suspend fun onTick(context: StarSystem) {
    }
    open suspend fun spawnEntity(entity: Entity) {
        entities += entity
    }
    open suspend fun removeEntity(entity: Entity) {
        entities -= entity
    }
}
