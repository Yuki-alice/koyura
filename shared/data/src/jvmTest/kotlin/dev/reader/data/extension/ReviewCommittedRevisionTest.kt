package dev.reader.data.extension

import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraCatalogEntry
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ReviewCommittedRevisionTest {
    @Test
    fun cancellingCandidateKeepsExistingInstanceAndDoesNotRunOldInitAgain() = runTest {
        val oldInitHits = AtomicInteger()
        val waitingForCandidate = CompletableDeferred<Unit>()
        val old = source("1.0.0", "async init() { await Network.get('https://example.invalid/old-init'); }")
        val candidate = source("2.0.0", "async init() { await Network.get('https://example.invalid/new-init'); }")
        val mock = MockEngine { request ->
            when (request.url.encodedPath) {
                "/old-init" -> {
                    oldInitHits.incrementAndGet()
                    respond("ok", HttpStatusCode.OK)
                }
                "/new-init" -> {
                    waitingForCandidate.complete(Unit)
                    awaitCancellation()
                }
                else -> respond(candidate, HttpStatusCode.OK)
            }
        }
        withService(mock) { service, _ ->
            service.installFromContent("Review", old, "review")
            assertEquals("1", counter(service))
            val update = launch { service.update("review") }
            waitingForCandidate.await()
            update.cancelAndJoin()
            assertEquals("1.0.0", service.listInstalled().single().version)
            assertEquals(1 to "2", oldInitHits.get() to counter(service), "The exact old instance must survive cancellation")
        }
    }

    @Test
    fun identicalCandidateFailureCannotDeleteCurrentRevisionFile() = runTest {
        val rejectInit = AtomicBoolean(false)
        val unchanged = source("1.0.0", """
            async init() {
                const response = await Network.get('https://example.invalid/init');
                if (response.status !== 200) throw new Error('init failed');
            }
        """.trimIndent())
        val mock = MockEngine { request ->
            if (request.url.encodedPath == "/init") {
                respond("init", if (rejectInit.get()) HttpStatusCode.InternalServerError else HttpStatusCode.OK)
            } else {
                respond(unchanged, HttpStatusCode.OK)
            }
        }
        withService(mock) { service, root ->
            val installed = service.installFromContent("Review", unchanged, "review")
            rejectInit.set(true)
            runCatching { service.update("review") }
            assertTrue(FileSystem.SYSTEM.exists(root / installed.fileName), "The current file is still referenced and must not be deleted")
            assertEquals(unchanged, FileSystem.SYSTEM.read(root / installed.fileName) { readUtf8() })
            rejectInit.set(false)
            assertEquals(1, service.restore().registered)
        }
    }

    @Test
    fun rejectedCandidateLeavesRollbackOriginAndInstalledRowInAgreement() = runTest {
        val base = "https://example.invalid"
        val oldUrl = "$base/old.js"
        val newUrl = "$base/new.js"
        val index = "$base/index.json"
        val published = AtomicBoolean(false)
        val mock = MockEngine { request ->
            val script = when (request.url.encodedPath) {
                "/index.json" -> if (published.get()) {
                    """[{"name":"Review","key":"review","version":"2.0.0","url":"$newUrl"}]"""
                } else {
                    """[{"name":"Review","key":"review","version":"1.0.0","url":"$oldUrl"}]"""
                }
                "/old.js" -> source("1.0.0")
                "/new.js" -> source("2.0.0", "async init() { throw new Error('rejected'); }")
                else -> error("Unexpected request ${request.url}")
            }
            respond(script, HttpStatusCode.OK)
        }
        withService(mock) { service, _ ->
            val repository = service.addRepository("Review", index)
            service.installFromCatalog(repository.id, VeneraCatalogEntry("review", "Review", "1.0.0", oldUrl), index)
            published.set(true)
            assertTrue(runCatching { service.update("review") }.isFailure)
            assertEquals(oldUrl, service.listInstalled().single().originUrl)
            assertEquals(oldUrl, service.originFor("review")?.url, "Rollback must preserve the previously selected repository entry")
        }
    }

    private suspend fun counter(service: ExtensionService): String =
        service.call("review", VeneraCallback.LoadInfo, JsonArray(emptyList()))
            .jsonObject.getValue("title").jsonPrimitive.content

    private suspend fun withService(mock: MockEngine, block: suspend (ExtensionService, Path) -> Unit) {
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-revision-${Uuid.random()}"
        val driver = createTestDriver()
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = ExtensionService(driver, fs, root, client, VeneraEnvironment("1.16.0", "zh_CN", "test"))
        try {
            service.open()
            service.restore()
            block(service, root)
        } finally {
            service.close()
            driver.close()
            client.close()
            fs.deleteRecursively(root)
        }
    }

    private fun source(version: String, init: String = "") = """
        class ReviewSource extends ComicSource {
            name = "Review";
            key = "review";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/review.js";
            counter = 0;
            $init
            comic = { loadInfo: () => ({ title: String(++this.counter) }) };
        }
    """.trimIndent()
}
