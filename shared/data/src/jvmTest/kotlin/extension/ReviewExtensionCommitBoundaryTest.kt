package app.koyura.data.extension

import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ReviewExtensionCommitBoundaryTest {
    @Test
    fun cancellingDuringCandidateInitDoesNotCommitNewRevision() = runTest {
        val initStarted = CompletableDeferred<Unit>()
        val candidate = script("2.0.0", "new", "async init() { await Network.get('https://example.invalid/init'); }")
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/init") {
                initStarted.complete(Unit)
                awaitCancellation()
            }
            respond(candidate, HttpStatusCode.OK)
        }) { applyReaderDefaults() }
        val disk = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-cancel-init-${Uuid.random()}"
        val driver = createTestDriver()
        val service = ExtensionService(driver, disk, root, client, VeneraEnvironment("1.16.0", "zh_CN", "test"))
        try {
            service.open()
            service.restore()
            service.installFromContent("Review", script("1.0.0", "old"), "review")
            val update = launch { service.update("review") }
            initStarted.await()
            update.cancelAndJoin()
            assertEquals("1.0.0", service.listInstalled().single().version, "Cancelled init must preserve the committed revision")
            assertEquals(JsonPrimitive("old"), service.call("review", VeneraCallback.LoadInfo, JsonArray(emptyList())).jsonObject["title"])
        } finally {
            service.close()
            driver.close()
            client.close()
            disk.deleteRecursively(root)
        }
    }

    @Test
    fun rejectedCandidateWithSameVersionCannotDestroyInstalledScript() = runTest {
        val candidate = script("1.0.0", "new", "async init() { throw new Error('candidate failed'); }")
        val client = HttpClient(MockEngine { respond(candidate, HttpStatusCode.OK) }) { applyReaderDefaults() }
        val disk = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-same-version-${Uuid.random()}"
        val driver = createTestDriver()
        val service = ExtensionService(driver, disk, root, client, VeneraEnvironment("1.16.0", "zh_CN", "test"))
        try {
            service.open()
            service.restore()
            val old = service.installFromContent("Review", script("1.0.0", "old"), "review")
            assertFailsWith<Exception> { service.update("review") }
            assertTrue(disk.exists(root / old.fileName), "Failed replacement must not delete the existing script")
            assertEquals(1, service.restore().registered)
            assertEquals(JsonPrimitive("old"), service.call("review", VeneraCallback.LoadInfo, JsonArray(emptyList())).jsonObject["title"])
        } finally {
            service.close()
            driver.close()
            client.close()
            disk.deleteRecursively(root)
        }
    }

    private fun script(version: String, title: String, init: String = "") = """
        class ReviewSource extends ComicSource {
            name = "Review";
            key = "review";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/review.js";
            $init
            comic = { loadInfo() { return { title: "$title" }; } };
        }
    """.trimIndent()
}
