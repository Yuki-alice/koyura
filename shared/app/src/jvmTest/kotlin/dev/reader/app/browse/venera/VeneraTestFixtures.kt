package dev.reader.app.browse.venera

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.app.browse.BrowseService
import dev.reader.app.browse.venera.VeneraBrowseService
import dev.reader.data.extension.ExtensionService
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Shared fixtures for tests that run the production Venera stack (real SQLDelight, real scripts,
 * real QuickJS, Ktor MockEngine responses).
 */
internal object VeneraFixtures {
    const val ExpectedScriptSha256 = "41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d"
    const val IndexUrl = "https://repo.example/index.json"
    const val ScriptUrl = "https://example.invalid/sources/example.js"

    fun upstreamScriptBytes(): ByteArray {
        val fromProperty = System.getProperty("reader.venera.example")
            ?.let(::File)?.takeIf { it.isFile }?.readBytes()
        val bytes = fromProperty
            ?: VeneraFixtures::class.java.getResource("/venera/minimal_source.js")!!.readBytes()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }
        assert(ExpectedScriptSha256 == actual) { "upstream minimal_source.js must match" }
        return bytes
    }

    fun catalogJson(scriptUrl: String = ScriptUrl): String = """
        {"key":"example_source","name":"Example Source","version":"1.0.0",
          "url":"$scriptUrl","description":"Example"}
    """.trimIndent().let { "[$it]" }

    fun searchJson(): String = """
        {"items":[{"id":"demo","title":"星图","author":"作者",
          "cover":"https://example.invalid/cover.jpg","tags":["冒险"]}],"totalPages":3}
    """.trimIndent()

    fun detailJson(): String = """
        {"title":"星图","author":"作者","cover":"https://example.invalid/cover.jpg",
         "description":"简介","updatedOn":"2026-09-01",
         "chapters":[{"id":"2","title":"第二章"},{"id":"1","title":"第一章"}]}
    """.trimIndent()
}

internal class VeneraHarness(val script: ByteArray = VeneraFixtures.upstreamScriptBytes()) {
    var indexFailures = 0
    val engine = MockEngine { request ->
        val url = request.url
        when {
            url.encodedPath == "/index.json" -> {
                if (indexFailures > 0) {
                    indexFailures -= 1
                    respond("boom", HttpStatusCode.InternalServerError)
                } else {
                    respond(
                        VeneraFixtures.catalogJson(),
                        HttpStatusCode.OK,
                        headersOf("Content-Type", "application/json"),
                    )
                }
            }
            url.host == "example.invalid" && url.encodedPath == "/sources/example.js" ->
                respond(script, HttpStatusCode.OK)
            url.host == "example.invalid" && url.encodedPath == "/api/search" ->
                respond(VeneraFixtures.searchJson(), HttpStatusCode.OK)
            url.host == "example.invalid" && url.encodedPath == "/api/comics/demo" ->
                respond(VeneraFixtures.detailJson(), HttpStatusCode.OK)
            else -> respond("not found", HttpStatusCode.NotFound)
        }
    }
    val tempDir: File = java.nio.file.Files.createTempDirectory("reader-venera").toFile()
    val scriptsDir = File(tempDir, "scripts").apply { mkdirs() }
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExtensionDb.Schema.create(it) }
    val client = HttpClient(engine) { applyReaderDefaults() }
    val service = ExtensionService(
        driver,
        FileSystem.SYSTEM,
        scriptsDir.toOkioPath(),
        client,
        VeneraEnvironment("1.16.0", "zh_CN", "test"),
    )

    fun browse(): dev.reader.app.browse.BrowseService = VeneraBrowseService(service)

    fun installedScriptSha(): String {
        val file = scriptsDir.listFiles()!!.single { it.name.endsWith(".js") }
        return MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }
    }

    fun close() {
        runBlocking { service.close() }
        client.close()
        driver.close()
        tempDir.deleteRecursively()
    }
}
