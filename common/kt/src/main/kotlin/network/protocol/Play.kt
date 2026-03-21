package io.github.maxsh001.aphelion.network.protocol

import io.github.maxsh001.aphelion.utils.Vec2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
@SerialName(MouseUpdatePacket.PACKET_ID)
class MouseUpdatePacket private constructor(
    val position: Vec2,
) : ServerBoundPacket(0.0) {
    companion object {
        const val PACKET_ID = "MOUSE_UPDATE"
    }
}
@Serializable
@SerialName(SplitPacket.PACKET_ID)
class SplitPacket private constructor() : ServerBoundPacket(0.0) {
    companion object {
        const val PACKET_ID = "SPLIT"
    }
}

@Serializable
@SerialName(SpawnEntityPacket.PACKET_ID)
class SpawnEntityPacket(
    val uniqueId: Uuid,
    val position: Vec2,
    val velocity: Vec2,
    val radius: Float,
) : ClientBoundPacket() {
    companion object {
        const val PACKET_ID = "ADD_ENTITY"
    }
}
@Serializable
@SerialName(UpdateEntityPositionPacket.PACKET_ID)
class UpdateEntityPositionPacket(
    val uniqueId: Uuid,
    val position: Vec2,
) {
    companion object {
        const val PACKET_ID = "SET_ENTITY_POSITION"
    }
}
@Serializable
@SerialName(UpdateEntityVelocityPacket.PACKET_ID)
class UpdateEntityVelocityPacket(
    val uniqueId: Uuid,
    val velocity: Vec2,
) {
    companion object {
        const val PACKET_ID = "SET_ENTITY_VELOCITY"
    }
}

@Serializable
@SerialName(EntityDataPacket.PACKET_ID)
class EntityDataPacket(
    val uniqueId: Uuid,
    vararg val data: EntityMetadataEntry,
) {
    companion object {
        const val PACKET_ID = "SET_ENTITY_DATA"
    }
}
@Serializable
sealed class EntityMetadataEntry() {
    class Color(val color: io.github.maxsh001.aphelion.utils.Color) : EntityMetadataEntry()
}
@Serializable
@SerialName(RemoveEntityPacket.PACKET_ID)
class RemoveEntityPacket(
    val uniqueId: Uuid,
) : ClientBoundPacket() {
    companion object {
        const val PACKET_ID = "REMOVE_ENTITY"
    }
}
