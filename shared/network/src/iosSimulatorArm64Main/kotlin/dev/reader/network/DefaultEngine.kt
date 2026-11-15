package dev.reader.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createDefaultHttpClient(): HttpClient = HttpClient(Darwin) {
    applyReaderDefaults()
}
