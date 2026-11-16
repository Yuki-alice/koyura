package app.koyura.source.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VeneraSourceRuntimeTest {
    @Test
    fun registersBeforeInitAndAwaitsItsPromise() = runTest {
        withRuntime({ request ->
            assertEquals("example_source", request.sourceKey)
            assertEquals("https://example.invalid/init", request.url)
            VeneraHttpResponse(200, "ready")
        }) { runtime ->
            val source = runtime.register(exampleSource, expectedKey = "example_source")
            assertEquals(listOf(source), runtime.registeredSources())
            assertEquals(
                JsonPrimitive("ready:42"),
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("42"))))
                    .jsonObject["title"],
            )
            assertEquals(
                JsonPrimitive("1.16.0"),
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("42"))))
                    .jsonObject["description"],
            )
            assertTrue(runtime.unregister(source.key))
            assertFalse(runtime.unregister(source.key))
            assertTrue(runtime.registeredSources().isEmpty())
            assertFailsWith<IllegalArgumentException> { runtime.call(source.key, VeneraCallback.LoadInfo) }
            assertEquals(source, runtime.register(exampleSource))
        }
    }

    @Test
    fun rollsBackAfterRejectedInitAndAllowsRetry() = runTest {
        var available = false
        withRuntime({
            if (!available) error("fixture transport failed")
            VeneraHttpResponse(200, "ready")
        }) { runtime ->
            val failure = assertFailsWith<VeneraScriptException> {
                runtime.register(exampleSource, "failed-init.js")
            }
            assertContains(failure.message.orEmpty(), "fixture transport failed")
            assertTrue(runtime.registeredSources().isEmpty())
            available = true
            assertEquals("example_source", runtime.register(exampleSource).key)
        }
    }

    @Test
    fun rejectsWrongKeysDuplicateKeysAndUnsupportedMinimumVersion() = runTest {
        withRuntime({ VeneraHttpResponse(200, "ready") }) { runtime ->
            assertFailsWith<IllegalArgumentException> { runtime.register(exampleSource, expectedKey = "wrong_key") }
            assertFailsWith<IllegalArgumentException> {
                runtime.register(exampleSource.replace("1.16.0", "1.16.0-hotfix"))
            }
            assertTrue(runtime.registeredSources().isEmpty())
            val registered = runtime.register(exampleSource)
            assertFailsWith<IllegalArgumentException> { runtime.register(exampleSource) }
            assertEquals(listOf(registered), runtime.registeredSources())
            assertFailsWith<IllegalArgumentException> { runtime.call(registered.key, VeneraCallback.Search) }
        }
    }

    @Test
    fun tagClickRequiresSynchronousReturn() = runTest {
        withRuntime({ VeneraHttpResponse(200, "ready") }) { runtime ->
            val source = runtime.register(
                exampleSource.replace(
                    "onClickTag()",
                    "async onClickTag()",
                ),
            )
            val error = assertFailsWith<VeneraScriptException> {
                runtime.call(source.key, VeneraCallback.ClickTag)
            }
            assertContains(error.message.orEmpty(), "must return synchronously")
            assertEquals(
                JsonPrimitive("ready:ok"),
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("ok"))))
                    .jsonObject["title"],
            )
        }
    }

    @Test
    fun awaitsThumbnailPromiseLikeVeneraNext() = runTest {
        withRuntime({ request ->
            delay(5)
            val body = if (request.url.endsWith("/init")) "ready" else "https://example.invalid/cover.jpg"
            VeneraHttpResponse(200, body)
        }) { runtime ->
            val source = runtime.register(
                exampleSource.replace(
                    "onThumbnailLoad(url) { return { url }; }",
                    "async onThumbnailLoad(url) { return { url: (await Network.get(url)).body }; }",
                ),
            )
            assertEquals(
                JsonPrimitive("https://example.invalid/cover.jpg"),
                runtime.call(
                    source.key,
                    VeneraCallback.LoadThumbnail,
                    JsonArray(listOf(JsonPrimitive("https://example.invalid/thumbnail"))),
                ).jsonObject["url"],
            )
        }
    }

    @Test
    fun cancelsAHostRequestWithoutPoisoningTheRegistry() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        withRuntime({ request ->
            if (request.url.endsWith("/wait")) {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            VeneraHttpResponse(200, "ready")
        }) { runtime ->
            val source = runtime.register(
                exampleSource.replace("loadInfo(id) {", """
                    async loadInfo(id) {
                        if (id === "wait") await Network.get("https://example.invalid/wait");
                """.trimIndent()),
            )
            val call = launch {
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("wait"))))
            }
            started.await()
            call.cancelAndJoin()
            cancelled.await()
            assertEquals(listOf(source), runtime.registeredSources())
            assertEquals(
                JsonPrimitive("ready:ok"),
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("ok"))))
                    .jsonObject["title"],
            )
        }
    }

    @Test
    fun closeIsIdempotentAndRejectsFurtherCalls() = runTest {
        withRuntime({ VeneraHttpResponse(200, "ready") }) { runtime ->
            runtime.register(exampleSource)
            runtime.close()
            runtime.close()
            assertFailsWith<IllegalStateException> { runtime.registeredSources() }
            assertFailsWith<IllegalStateException> { runtime.register(exampleSource) }
        }
    }

    private suspend fun withRuntime(
        transport: VeneraHttpTransport,
        block: suspend (VeneraSourceRuntime) -> Unit,
    ) {
        val runtime = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "zh_CN", "test"), transport)
        try {
            block(runtime)
        } finally {
            runtime.close()
        }
    }

    private val exampleSource = """
        class ExampleSource extends ComicSource {
            name = "Example";
            key = "example_source";
            version = "1.0.0";
            minAppVersion = "1.16.0";

            async init() {
                if (ComicSource.sources[this.key] !== this) throw new Error("Source missing from registry");
                const response = await Network.get("https://example.invalid/init");
                this.comic.status = response.body;
            }

            comic = {
                loadInfo(id) {
                    return new ComicDetails({
                        title: this.status + ":" + id,
                        tags: {},
                        description: APP.version,
                    });
                },
                onThumbnailLoad(url) { return { url }; },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()
}
