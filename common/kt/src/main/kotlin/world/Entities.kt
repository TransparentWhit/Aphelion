package io.github.maxsh001.aphelion.world

import io.github.maxsh001.aphelion.TickedScheduler
import io.github.maxsh001.aphelion.utils.Color
import io.github.maxsh001.aphelion.utils.UniquelyIdentifiable
import io.github.maxsh001.aphelion.utils.Vec2
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.uuid.Uuid

@Serializable
abstract class Entity protected constructor(
    @Required
    override val uniqueId: Uuid = Uuid.random()
): Tickable<StarSystem>, UniquelyIdentifiable {
    override val children get() = emptySequence<Nothing>()
    @Transient
    override val scheduler: TickedScheduler = TickedScheduler()
    abstract var position: Vec2
    abstract val velocity: Vec2
    abstract val radius: Float
    val mass: Float inline get() = radius * radius
    override suspend fun onTick(context: StarSystem) {
        position += velocity
    }
    protected fun within(other: Entity) = (position - other.position).length <= min(radius, other.radius)
    override fun equals(other: Any?) = other is Entity && uniqueId == other.uniqueId
    override fun hashCode() = uniqueId.hashCode()
}
@Serializable
class Food(
    override var position: Vec2,
    override val velocity: Vec2,
    override val radius: Float,
    val color: Color,
) : Entity()
@Serializable
class Cell private constructor(
    override var position: Vec2,
    override var velocity: Vec2,
    override var radius: Float,
) : Entity() {
    override suspend fun onTick(context: StarSystem) {
        velocity = Vec2.ZERO
        for (entity in context.entities) { //todo optimize
            if (within(entity) && when (entity) {
                is Food -> true
                is Cell -> entity != this //todo more logic here
                else -> false
            }) {
                radius = sqrt(mass + entity.mass)
                context.removeEntity(entity)
            }
        }
        super.onTick(context)
    }
}
