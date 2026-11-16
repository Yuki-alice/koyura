package dev.reader.data.extension

import dev.reader.domain.extension.ExtensionFailure
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraCatalogEntry
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val KEY = "example_source"

private fun scriptV1() = """
class ExampleSource extends ComicSource {
    name = "Example";
    key = "$KEY";
    version = "1.0.0";
    minAppVersion = "1.16.0";
    url = "https://example.invalid/sources/example.js";
    comic = {
        loadInfo(id) { return { title: "v1:" + id }; },
        onClickTag() { return null; },
    };
}
""".trimIndent()

private fun scriptV2() = """
class ExampleSource extends ComicSource {
    name = "Example";
    key = "$KEY";
    version = "2.0.0";
    minAppVersion = "1.16.0";
    url = "https://example.invalid/sources/example.js";
    comic = {
        loadInfo(id) { return { title: "v2:" + id }; },
        onClickTag() { return null; },
    };
}
""".trimIndent()

private fun scriptBrokenInit() = """
class ExampleSource extends ComicSource {
    name = "Example";
    key = "$KEY";
    version = "9.9.9";
    minAppVersion = "1.16.0";
    url = "https://example.invalid/sources/example.js";
    async init() { throw new Error("init boom"); }
    comic = {
        loadInfo(id) { return { title: "broken:" + id }; },
        onClickTag() { return null; },
    };
}
""".trimIndent()

private fun scriptOtherKey() = """
class OtherSource extends ComicSource {
    name = "Other";
    key = "other_key";
    version = "1.0.0";
    minAppVersion = "1.16.0";
    comic = {
        loadInfo(id) { return { title: "other:" + id }; },
        onClickTag() { return null; },
    };
}
""".trimIndent()

class ExtensionServiceTest {
    private val environment = VeneraEnvironment("1.16.0", "zh_CN", "test")

    private fun noHttpClient() = HttpClient(
        MockEngine { error("unexpected HTTP: ${it.url}") },
    ) { applyReaderDefaults() }

    private suspend fun openService(
        httpClient: HttpClient,
        driver: app.cash.sqldelight.db.SqlDriver = createTestDriver(),
        scriptsDir: okio.Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}",
    ): ExtensionService {
        val service = ExtensionService(
            driver = driver,
            fileSystem = FileSystem.SYSTEM,
            scriptsDir = scriptsDir,
            httpClient = httpClient,
            environment = environment,
        )
        service.open()
        service.restore()
        return service
    }

    @Test
    fun installCallAndUninstallFromContent() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(noHttpClient(), scriptsDir = scriptsDir)
        try {
            val installed = service.installFromContent("Example", scriptV1(), expectedKey = KEY)
            assertEquals(KEY, installed.sourceKey)
            assertEquals("1.0.0", installed.version)
            assertTrue(FileSystem.SYSTEM.exists(scriptsDir / installed.fileName))
            val title = service.runtime!!.call(
                KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("42"))),
            ).jsonObject["title"]
            assertEquals(JsonPrimitive("v1:42"), title)
            service.uninstall(KEY)
            assertTrue(!FileSystem.SYSTEM.exists(scriptsDir / installed.fileName))
            assertFailsWith<ExtensionFailure.MissingExtension> { service.uninstall(KEY) }
        } finally {
            service.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun keyMismatchLeavesNoSideEffects() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(noHttpClient(), scriptsDir = scriptsDir)
        try {
            assertFailsWith<ExtensionFailure.KeyMismatch> {
                service.installFromContent("Other", scriptOtherKey(), expectedKey = KEY)
            }
            assertTrue((FileSystem.SYSTEM.listOrNull(scriptsDir) ?: emptyList()).isEmpty())
        } finally {
            service.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun initFailureLeavesNoSideEffects() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(noHttpClient(), scriptsDir = scriptsDir)
        try {
            assertFailsWith<ExtensionFailure.InvalidScript> {
                service.installFromContent("Example", scriptBrokenInit(), expectedKey = KEY)
            }
            assertTrue((FileSystem.SYSTEM.listOrNull(scriptsDir) ?: emptyList()).isEmpty())
            assertFailsWith<ExtensionFailure.MissingExtension> { service.uninstall(KEY) }
        } finally {
            service.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun diskFailureKeepsNoRecord() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(noHttpClient(), scriptsDir = scriptsDir)
        try {
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
            FileSystem.SYSTEM.write(scriptsDir) { writeUtf8("blocking") }
            assertFailsWith<ExtensionFailure.StorageFailure> {
                service.installFromContent("Example", scriptV1(), expectedKey = KEY)
            }
        } finally {
            service.close()
            FileSystem.SYSTEM.delete(scriptsDir, mustExist = false)
        }
    }

    @Test
    fun concurrentSameKeyInstallSerializes() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(noHttpClient(), scriptsDir = scriptsDir)
        try {
            val first = async { service.installFromContent("Example", scriptV1(), expectedKey = KEY) }
            val second = async {
                try {
                    service.installFromContent("Example", scriptV1(), expectedKey = KEY)
                    null
                } catch (e: ExtensionFailure.AlreadyInstalled) {
                    e
                }
            }
            first.await()
            val conflict = second.await()
            assertTrue(conflict is ExtensionFailure.AlreadyInstalled)
            val title = service.runtime!!.call(
                KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("7"))),
            ).jsonObject["title"]
            assertEquals(JsonPrimitive("v1:7"), title)
        } finally {
            service.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun restartRecoveryRestoresRegistration() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val driver = createTestDriver()
        val first = openService(noHttpClient(), driver = driver, scriptsDir = scriptsDir)
        try {
            first.installFromContent("Example", scriptV1(), expectedKey = KEY)
        } finally {
            first.close()
        }
        val second = openService(noHttpClient(), driver = driver, scriptsDir = scriptsDir)
        try {
            val title = second.runtime!!.call(
                KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("9"))),
            ).jsonObject["title"]
            assertEquals(JsonPrimitive("v1:9"), title)
        } finally {
            second.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
            driver.close()
        }
    }

    @Test
    fun repoLinkedUpdateReplacesAndFailedReplaceKeepsOld() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        var scriptBytes = scriptV1()
        var catalogBytes = """[{"key":"$KEY","name":"Example","version":"1.0.0","url":"https://example.invalid/sources/example.js"}]"""
        val mock = MockEngine { request ->
            val url = request.url.toString()
            when {
                url == "https://example.invalid/repo/index" -> respond(catalogBytes, HttpStatusCode.OK)
                url == "https://example.invalid/sources/example.js" -> respond(scriptBytes, HttpStatusCode.OK)
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            val repo = service.addRepository("Main", "https://example.invalid/repo/index")
            val installed = service.installFromCatalog(
                repo.id,
                dev.reader.source.runtime.VeneraCatalogEntry(KEY, "Example", "1.0.0", "https://example.invalid/sources/example.js"),
                repo.url,
            )
            assertEquals("1.0.0", installed.version)
            assertTrue(service.checkUpdates().isEmpty())
            // Publish v2.
            scriptBytes = scriptV2()
            catalogBytes = """[{"key":"$KEY","name":"Example","version":"2.0.0","url":"https://example.invalid/sources/example.js"}]"""
            assertEquals(mapOf(KEY to "2.0.0"), service.checkUpdates())
            val updated = service.update(KEY)
            assertEquals("2.0.0", updated.version)
            assertEquals(
                JsonPrimitive("v2:1"),
                service.runtime!!.call(KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("1")))).jsonObject["title"],
            )
            // Broken replacement must keep the old revision live.
            scriptBytes = scriptBrokenInit()
            catalogBytes = """[{"key":"$KEY","name":"Example","version":"3.0.0","url":"https://example.invalid/sources/example.js"}]"""
            assertFailsWith<Exception> { service.update(KEY) }
            assertEquals(
                JsonPrimitive("v2:1"),
                service.runtime!!.call(KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("1")))).jsonObject["title"],
            )
            assertTrue(FileSystem.SYSTEM.read(scriptsDir / updated.fileName) { readUtf8() }.contains("v2:"))
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun installFromUrlAndMissingScriptFailsCleanly() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val mock = MockEngine { request ->
            if (request.url.toString() == "https://example.invalid/sources/example.js") {
                respond(scriptV1(), HttpStatusCode.OK)
            } else {
                respond("gone", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            val installed = service.installFromUrl("https://example.invalid/sources/example.js", expectedKey = KEY)
            assertEquals("1.0.0", installed.version)
            assertFailsWith<ExtensionFailure.StorageFailure> {
                service.installFromUrl("https://example.invalid/sources/missing.js")
            }
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun installRunsInitExactlyOnce() = runTest {
        var hits = 0
        val mock = MockEngine {
            hits++
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            service.installFromContent("Init", scriptWithInit("1.0.0"), expectedKey = KEY)
            assertEquals(1, hits, "installing one source runs its init hook once")
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun failedCandidateKeepsOldInstanceAndMemoryState() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        var served = scriptStateful("1.0.0")
        val mock = MockEngine { respond(served, HttpStatusCode.OK) }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            service.installFromUrl("https://example.invalid/sources/example.js", expectedKey = KEY)
            assertEquals(JsonPrimitive("n:1"), callTitle(service, "a"))
            assertEquals(JsonPrimitive("n:2"), callTitle(service, "b"))
            // Async init failure in the candidate: old instance stays live with state.
            served = scriptStatefulBrokenInit("2.0.0")
            assertFailsWith<Exception> { service.update(KEY) }
            assertEquals(JsonPrimitive("n:3"), callTitle(service, "c"))
            assertEquals("1.0.0", service.listInstalled().single().version)
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun concurrentSameKeyUpdateSerializes() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var served = scriptV1()
        var gateDownloads = false
        val mock = MockEngine { request ->
            if (gateDownloads && request.url.toString() == "https://example.invalid/sources/example.js") {
                started.complete(Unit)
                release.await()
            }
            respond(served, HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            service.installFromUrl("https://example.invalid/sources/example.js", expectedKey = KEY)
            served = scriptV2()
            gateDownloads = true
            val first = async { service.update(KEY) }
            started.await()
            val second = async {
                try {
                    service.update(KEY)
                    null
                } catch (e: ExtensionFailure.UpdateInProgress) {
                    e
                }
            }
            release.complete(Unit)
            first.await()
            assertIs<ExtensionFailure.UpdateInProgress>(second.await())
            assertEquals(JsonPrimitive("v2:1"), callTitle(service, "1"))
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun cancelledUpdateKeepsOldRevision() = runTest {
        val gate = CompletableDeferred<Unit>()
        var blockDownloads = false
        val mock = MockEngine { request ->
            if (blockDownloads && request.url.toString() == "https://example.invalid/sources/example.js") {
                gate.await()
            }
            respond(scriptV1(), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            service.installFromUrl("https://example.invalid/sources/example.js", expectedKey = KEY)
            blockDownloads = true
            val update = launch { service.update(KEY) }
            delay(20)
            update.cancelAndJoin()
            assertEquals(JsonPrimitive("v1:ok"), callTitle(service, "ok"))
            assertEquals("1.0.0", service.listInstalled().single().version)
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun repositoryEditPreviewListCallAndStaleEntry() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val catalogV1 = """[{"key":"$KEY","name":"Example","version":"1.0.0","url":"https://example.invalid/sources/example.js"}]"""
        var catalogServed = catalogV1
        val mock = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.invalid/repo/index" -> respond(catalogServed, HttpStatusCode.OK)
                "https://example.invalid/other/index" -> respond(catalogV1, HttpStatusCode.OK)
                "https://example.invalid/sources/example.js" -> respond(scriptV1(), HttpStatusCode.OK)
                else -> respond("gone", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            val repo = service.addRepository("Main", "https://example.invalid/repo/index")
            // Preview saves nothing.
            val (previewedUrl, catalog) = service.previewCatalog(repo.id)
            assertEquals(repo.url, previewedUrl)
            assertEquals(1, catalog.entries.size)
            assertTrue(service.listInstalled().isEmpty())
            assertNull(service.originFor(KEY))
            // Edit: rename + move URL; preview follows the new URL.
            val edited = service.updateRepository(repo.id, "Main2", "https://example.invalid/other/index")
            assertEquals("Main2", edited.name)
            val (_, catalog2) = service.previewCatalog(repo.id)
            assertEquals(1, catalog2.entries.size)
            // Old preview base is now stale.
            val staleEntry = VeneraCatalogEntry(KEY, "Example", "1.0.0", "https://example.invalid/sources/example.js")
            assertFailsWith<ExtensionFailure.StaleCatalog> {
                service.installFromCatalog(repo.id, staleEntry, "https://example.invalid/repo/index")
            }
            // Install + call + list with the fresh base.
            val installed = service.installFromCatalog(repo.id, staleEntry, edited.url)
            assertEquals(KEY, installed.sourceKey)
            assertEquals(listOf(KEY), service.listInstalled().map { it.sourceKey })
            assertEquals(JsonPrimitive("v1:x"), callTitle(service, "x"))
            assertFailsWith<ExtensionFailure.MissingExtension> {
                service.call("nope", VeneraCallback.LoadInfo, JsonArray(emptyList()))
            }
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun updateCheckReportsFailuresAndAmbiguity() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        var goodCatalog = """
            [{"key":"ka","name":"A","version":"1.0.0","url":"https://example.invalid/a.js"},
             {"key":"kd","name":"D","version":"1.0.0","url":"https://example.invalid/d.js"}]
        """.trimIndent()
        var ambCatalog = """[{"key":"kc","name":"C","version":"1.0.0","url":"https://example.invalid/c.js"}]"""
        val mock = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.invalid/good/index" -> respond(goodCatalog, HttpStatusCode.OK)
                "https://example.invalid/bad/index" -> respond("down", HttpStatusCode.ServiceUnavailable)
                "https://example.invalid/amb/index" -> respond(ambCatalog, HttpStatusCode.OK)
                "https://example.invalid/a.js" -> respond(scriptFor("ka", "1.0.0"), HttpStatusCode.OK)
                "https://example.invalid/b.js" -> respond(scriptFor("kb", "1.0.0"), HttpStatusCode.OK)
                "https://example.invalid/c.js" -> respond(scriptFor("kc", "1.0.0"), HttpStatusCode.OK)
                "https://example.invalid/d.js" -> respond(scriptFor("kd", "1.0.0"), HttpStatusCode.OK)
                else -> respond("gone", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            val good = service.addRepository("Good", "https://example.invalid/good/index")
            val bad = service.addRepository("Bad", "https://example.invalid/bad/index")
            val amb = service.addRepository("Amb", "https://example.invalid/amb/index")
            service.installFromCatalog(good.id, VeneraCatalogEntry("ka", "A", "1.0.0", "https://example.invalid/a.js"), good.url)
            service.installFromCatalog(bad.id, VeneraCatalogEntry("kb", "B", "1.0.0", "https://example.invalid/b.js"), bad.url)
            service.installFromCatalog(amb.id, VeneraCatalogEntry("kc", "C", "1.0.0", "https://example.invalid/c.js"), amb.url)
            service.installFromCatalog(good.id, VeneraCatalogEntry("kd", "D", "1.0.0", "https://example.invalid/d.js"), good.url)
            // Evolve catalogs: ka has an update, kb's repo goes down (already 503),
            // kc's variants no longer match its origin URL, kd disappears.
            goodCatalog = """[{"key":"ka","name":"A","version":"2.0.0","url":"https://example.invalid/a.js"}]"""
            ambCatalog = """[{"key":"kc","name":"C1","version":"2.0.0","url":"https://example.invalid/c1.js"},
                            {"key":"kc","name":"C2","version":"3.0.0","url":"https://example.invalid/c2.js"}]"""
            val report = service.checkUpdatesDetailed()
            assertEquals(mapOf("ka" to "2.0.0"), report.updates)
            assertTrue(report.repoFailures.containsKey(bad.id), "bad repo failure: $report")
            assertEquals(listOf("kc"), report.ambiguousKeys)
            assertEquals(listOf("kd"), report.missingKeys)
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun persistedVersionFollowsActualScriptNotCatalogLabel() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        var servedScript = scriptFor("ka", "1.0.0")
        val mock = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.invalid/repo/index" -> respond(
                    """[{"key":"ka","name":"A","version":"9.9.9","url":"https://example.invalid/a.js"}]""",
                    HttpStatusCode.OK,
                )
                "https://example.invalid/a.js" -> respond(servedScript, HttpStatusCode.OK)
                else -> respond("gone", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            val repo = service.addRepository("Main", "https://example.invalid/repo/index")
            val installed = service.installFromCatalog(
                repo.id,
                VeneraCatalogEntry("ka", "A", "9.9.9", "https://example.invalid/a.js"),
                repo.url,
            )
            assertEquals("1.0.0", installed.version)
            assertEquals("1.0.0", service.listInstalled().single().version)
            // The catalog still advertises 9.9.9, so an update is reported —
            // while the previously committed row keeps the real script version.
            assertEquals(mapOf("ka" to "9.9.9"), service.checkUpdates())
            // Updating to a script whose actual version differs from the entry
            // label persists the script version, not the label.
            servedScript = scriptFor("ka", "2.0.0")
            val updated = service.update("ka")
            assertEquals("2.0.0", updated.version)
            assertEquals("2.0.0", service.listInstalled().single().version)
            assertTrue(updated.fileName.contains("2.0.0-"), "revision file binds the real version: ${updated.fileName}")
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun updateCandidateInitRunsOnce() = runTest {
        var initHits = 0
        val mock = MockEngine { request ->
            if (request.url.encodedPath == "/init") initHits++
            if (request.url.toString() == "https://example.invalid/sources/example.js") {
                respond(scriptWithInit("2.0.0"), HttpStatusCode.OK)
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = openService(client, scriptsDir = scriptsDir)
        try {
            service.installFromContent("Example", scriptV1(), expectedKey = KEY)
            service.update(KEY)
            assertEquals(1, initHits, "a successful update runs the candidate init exactly once")
            assertEquals(JsonPrimitive("ok"), callTitle(service, "x"))
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    @Test
    fun brokenDatabaseFailsCleanlyAndKeepsOldRevision() = runTest {
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val mock = MockEngine { respond(scriptV1(), HttpStatusCode.OK) }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val driver = createTestDriver()
        val service = openService(client, driver = driver, scriptsDir = scriptsDir)
        try {
            service.installFromUrl("https://example.invalid/sources/example.js", expectedKey = KEY)
            driver.close()
            assertFailsWith<ExtensionFailure.StorageFailure> { service.update(KEY) }
            assertEquals(JsonPrimitive("v1:ok"), callTitle(service, "ok"))
            assertEquals(1, FileSystem.SYSTEM.list(scriptsDir).count { it.name.endsWith(".js") })
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    private suspend fun callTitle(service: ExtensionService, id: String) =
        service.runtime!!.call(KEY, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(id)))).jsonObject["title"]

    private fun scriptWithInit(version: String) = """
        class ReviewSource extends ComicSource {
            name = "Review";
            key = "$KEY";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/sources/example.js";
            async init() { await Network.get('https://example.invalid/init'); }
            comic = {
                loadInfo(id) { return { title: "ok" }; },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()

    private fun scriptFor(key: String, version: String) = """
        class CustomSource extends ComicSource {
            name = "Custom $key";
            key = "$key";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/$key.js";
            comic = {
                loadInfo(id) { return { title: "$key:$version" }; },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()

    private fun scriptStateful(version: String) = """
        class ExampleSource extends ComicSource {
            name = "Example";
            key = "$KEY";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/sources/example.js";
            comic = {
                loadInfo(id) { this.n = (this.n || 0) + 1; return { title: "n:" + this.n }; },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()

    private fun scriptStatefulBrokenInit(version: String) = """
        class ExampleSource extends ComicSource {
            name = "Example";
            key = "$KEY";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/sources/example.js";
            async init() { await Network.get('https://example.invalid/init'); throw new Error("late init boom"); }
            comic = {
                loadInfo(id) { this.n = (this.n || 0) + 1; return { title: "n:" + this.n }; },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()
}
