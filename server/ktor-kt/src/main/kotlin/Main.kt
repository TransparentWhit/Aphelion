package io.github.maxsh001.aphelion.server

import io.github.maxsh001.aphelion.server.network.configureRouting
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>) {
    val server = Server()
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = { configureRouting(server) }
    ).start(wait = true)
}
