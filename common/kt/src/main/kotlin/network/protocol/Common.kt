package io.github.maxsh001.aphelion.network.protocol

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerBoundPacket(
    open val timestamp: Double,
)

@Serializable
sealed class ClientBoundPacket()
