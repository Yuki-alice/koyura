package dev.reader.data.extension

import dev.reader.domain.extension.ExtensionFailure
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
