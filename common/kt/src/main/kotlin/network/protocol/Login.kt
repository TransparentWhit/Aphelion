package io.github.maxsh001.aphelion.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
@SerialName(LoginStartPacket.PACKET_ID)
class LoginStartPacket private constructor(
    val username: String,
) : ServerBoundPacket(0.0) {
    companion object {
        const val PACKET_ID = "LOGIN_START"
    }
}

@Serializable
@SerialName(LoginSuccessPacket.PACKET_ID)
class LoginSuccessPacket(
    val playerId: Uuid
) : ClientBoundPacket() {
    companion object {
        const val PACKET_ID = "LOGIN_SUCCESS"
    }
}
