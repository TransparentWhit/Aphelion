package io.github.maxsh001.aphelion.server

import io.github.maxsh001.aphelion.Task
import io.github.maxsh001.aphelion.network.protocol.ClientBoundPacket
import io.github.maxsh001.aphelion.network.protocol.RemoveEntityPacket
import io.github.maxsh001.aphelion.network.protocol.SpawnEntityPacket
import io.github.maxsh001.aphelion.utils.AphelionDuration
import io.github.maxsh001.aphelion.utils.Color
import io.github.maxsh001.aphelion.utils.Nameable
import io.github.maxsh001.aphelion.utils.UniquelyIdentifiable
import io.github.maxsh001.aphelion.utils.Vec2
import io.github.maxsh001.aphelion.world.Entity
import io.github.maxsh001.aphelion.world.Food
import io.github.maxsh001.aphelion.world.StarSystem
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
open class Player(
    override val uniqueId: Uuid,
    override val name: String,
    val color: Color,
    val cells: List<Uuid>,
    val targetDirection: Vec2,
) : UniquelyIdentifiable, Nameable {}

class ServerPlayer(
    val connection: WebSocketServerSession,
    uniqueId: Uuid,
    name: String,
    color: Color,
    cells: List<Uuid>,
    targetDirection: Vec2,
) : Player(uniqueId, name, color, cells, targetDirection) {
    suspend fun send(vararg packets: ClientBoundPacket) {
        for (packet in packets) {
            connection.sendSerialized(packet)
        }
    }
}

class Server {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val world = ServerStarSystem(this)
    val players = ConcurrentHashMap<Uuid, ServerPlayer>()
    init {
        scope.launch {
            world.tick(world, 50)
        }
    }
    suspend fun broadcast(vararg packets: ClientBoundPacket) = supervisorScope {
        players.map { (_, player) -> async {
            player.send(*packets)
        }}.awaitAll()
    }
    fun playerJoined(connection: WebSocketServerSession, uniqueId: Uuid, name: String) {
        players[uniqueId] = ServerPlayer(connection, uniqueId, name, "#00ffff", mutableListOf(), Vec2.ZERO)
    }
}

class ServerStarSystem(
    private val server: Server,
) : StarSystem() {
    init {
        server.scope.launch {
            scheduler += Task(interval = AphelionDuration.Tick(2000)) {
                repeat(100) {
                    spawnEntity(Food(Vec2(ARENA_SIZE * Math.random().toFloat(), ARENA_SIZE * Math.random().toFloat()), Vec2(1f, 0f), 10f, "#ffffff"))
                }
            }
        }
    }
    override suspend fun spawnEntity(entity: Entity) {
        entities += entity
        server.broadcast(SpawnEntityPacket(entity.uniqueId, entity.position, entity.velocity, entity.radius))
    }
    override suspend fun removeEntity(entity: Entity) {
        super.removeEntity(entity)
        server.broadcast(RemoveEntityPacket(entity.uniqueId))
    }
}
