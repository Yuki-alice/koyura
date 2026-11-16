package app.koyura.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/**
 * Shared Ktor defaults for reader text HTTP.
 *
 * Mirrors the upstream Venera-Next timeouts (15s connect / request / socket) and
 * requires [HttpClientConfig.expectSuccess] to be false so HTTP 4xx/5xx are
 * returned to scripts instead of thrown.
 *
 * Platform engines are provided per target:
 * - Android + desktop JVM: OkHttp (`ktor-client-okhttp`)
 * - iOS (arm64 + simulator arm64): Darwin (`ktor-client-darwin`)
 */
fun HttpClientConfig<*>.applyReaderDefaults() {
    expectSuccess = false
    followRedirects = true
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000L
        requestTimeoutMillis = 15_000L
        socketTimeoutMillis = 15_000L
    }
}

/**
 * Creates the platform-default [HttpClient] with [applyReaderDefaults].
 *
 * Actuals live in androidMain/jvmMain (OkHttp) and iosArm64Main/iosSimulatorArm64Main (Darwin).
 */
expect fun createDefaultHttpClient(): HttpClient
