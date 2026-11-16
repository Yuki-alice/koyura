package app.koyura.data.extension

import app.koyura.data.session.openExtensionSession
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val SESSION_KEY = "example_source"

private fun sessionScript(version: String, init: String = "") = """
    class ExampleSource extends ComicSource {
        name = "Example";
        key = "$SESSION_KEY";
        version = "$version";
        minAppVersion = "1.16.0";
        url = "https://example.invalid/sources/example.js";
        $init
        comic = {
            loadInfo(id) { this.n = (this.n || 0) + 1; return { title: "v$version:" + id + ":" + this.n }; },
            onClickTag() { return null; },
        };
    }
""".trimIndent()

private fun quietClient() = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) {
    applyReaderDefaults()
}

class ExtensionSessionTest {
    private val environment = VeneraEnvironment("1.16.0", "zh_CN", "test")

    @Test
    fun startupFailureReleasesAndDoubleCloseIsSafe() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-session-fail-${Uuid.random()}"
        val failingFs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun createDirectory(dir: Path, mustCreate: Boolean) {
                throw IOException("no disk")
            }
        }
        val driver = createTestDriver()
        val client = quietClient()
        assertFailsWith<IOException> {
            openExtensionSession(driver, failingFs, root, client, environment)
        }
        // Nothing half-opened may block a later attempt.
        val goodDriver = createTestDriver()
        val goodClient = quietClient()
        val session = openExtensionSession(goodDriver, FileSystem.SYSTEM, root, goodClient, environment)
        try {
            assertFalse(session.isClosed)
            assertTrue(session.report.registered == 0)
        } finally {
            session.close()
            assertTrue(session.isClosed)
            session.close()
            assertTrue(session.isClosed)
        }
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test
    fun closeReopenAndCallAgainInstalledSource() = runTest {
        val tag = "reopen-${Uuid.random()}"
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-session-$tag"
        val appDir = root / "app"
        val first = openExtensionSession(
            createFileBackedTestDriver(root, tag),
            FileSystem.SYSTEM,
            appDir,
            quietClient(),
            environment,
        )
        try {
            first.service.installFromContent("Example", sessionScript("1.0.0"), SESSION_KEY)
            assertEquals("v1.0.0:x:1", callTitle(first.service, "x"))
        } finally {
            first.close()
            assertTrue(first.isClosed)
            first.close()
        }
        val second = openExtensionSession(
            createFileBackedTestDriver(root, tag),
            FileSystem.SYSTEM,
            appDir,
            quietClient(),
            environment,
        )
        try {
            assertEquals(1, second.report.registered)
            assertEquals("1.0.0", second.service.listInstalled().single().version)
            // A fresh registration from disk: the counter restarts.
            assertEquals("v1.0.0:x:1", callTitle(second.service, "x"))
        } finally {
            second.close()
        }
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test
    fun cancelledOpenReleasesAndReopenWorks() = runTest {
        val tag = "cancelopen-${Uuid.random()}"
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-session-$tag"
        val appDir = root / "app"
        val gatedScript = """
            class ExampleSource extends ComicSource {
                name = "Example";
                key = "$SESSION_KEY";
                version = "1.0.0";
                minAppVersion = "1.16.0";
                url = "https://example.invalid/sources/example.js";
                async init() { await Network.get('https://example.invalid/gate'); }
                comic = {
                    loadInfo(id) { return { title: "v1.0.0:" + id }; },
                    onClickTag() { return null; },
                };
            }
        """.trimIndent()
        val passing = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) {
            applyReaderDefaults()
        }
        val reachedInit = CompletableDeferred<Unit>()
        val hanging = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/gate") {
                reachedInit.complete(Unit)
                awaitCancellation()
            }
            respond("ok", HttpStatusCode.OK)
        }) { applyReaderDefaults() }
        val seed = openExtensionSession(
            createFileBackedTestDriver(root, tag),
            FileSystem.SYSTEM,
            appDir,
            passing,
            environment,
        )
        try {
            seed.service.installFromContent("Example", gatedScript, SESSION_KEY)
        } finally {
            seed.close()
            passing.close()
        }
        val opening = async {
            openExtensionSession(
                createFileBackedTestDriver(root, tag),
                FileSystem.SYSTEM,
                appDir,
                hanging,
                environment,
            )
        }
        reachedInit.await()
        opening.cancelAndJoin()
        val reopened = openExtensionSession(
            createFileBackedTestDriver(root, tag),
            FileSystem.SYSTEM,
            appDir,
            quietClient(),
            environment,
        )
        try {
            assertEquals(1, reopened.report.registered)
            assertEquals("v1.0.0:x", callTitle(reopened.service, "x"))
        } finally {
            reopened.close()
            hanging.close()
        }
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test
    fun sessionReportsRestoredRevisions() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-session-${Uuid.random()}"
        val session = openExtensionSession(
            createTestDriver(),
            FileSystem.SYSTEM,
            root / "app",
            quietClient(),
            environment,
        )
        try {
            assertTrue(session.report.failures.isEmpty())
            session.service.installFromContent("Example", sessionScript("1.0.0"), SESSION_KEY)
            assertEquals(listOf(SESSION_KEY), session.service.listInstalled().map { it.sourceKey })
        } finally {
            session.close()
        }
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    private suspend fun callTitle(service: ExtensionService, id: String) =
        service.call(SESSION_KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(id))))
            .jsonObject.getValue("title").jsonPrimitive.content
}
