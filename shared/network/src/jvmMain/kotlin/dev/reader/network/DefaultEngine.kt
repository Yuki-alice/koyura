package dev.reader.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    applyReaderDefaults()
}
