package io.github.maxsh001.aphelion.server.network

import io.github.maxsh001.aphelion.network.aphelionPacketSerialFormat
import io.github.maxsh001.aphelion.network.protocol.*
import io.github.maxsh001.aphelion.server.Server
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

fun Application.configureRouting(server: Server) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
        exception<IllegalStateException> { call, cause ->
            call.respondText("App in illegal state as ${cause.message}")
        }
    }
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(aphelionPacketSerialFormat)
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        webSocket("/game") {
            val sessionId = Uuid.random()
            log.info("Client connected: $sessionId")
            try {
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            try {
                                when (val packet = aphelionPacketSerialFormat.decodeFromString<ServerBoundPacket>(frame.readText())) {
                                    is LoginStartPacket -> {
                                        log.info("{} joined the game.", packet.username)
                                        sendSerialized(LoginSuccessPacket(sessionId))
                                        server.playerJoined(this, sessionId, packet.username)
                                    }
                                    is MouseUpdatePacket -> {
                                        log.debug("Mouse update: {}", packet.position)
                                    }
                                    is SplitPacket -> {
                                        log.info("Split action received")
                                    }
                                }
                            } catch (exception: Exception) {
                                log.error("Error processing packet: {}", exception as Any)
                            }
                        }
                        else -> {}
                    }
                }
            } catch (exception: Exception) {
                log.error("WebSocket error for {}: {}", sessionId, exception)
            } finally {
                log.info("Client disconnected: {}", sessionId)
            }
        }
    }
}
