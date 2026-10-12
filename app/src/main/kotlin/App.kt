package library

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port) {
        module(config)
    }.start(wait = true)
}
