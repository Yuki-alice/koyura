package app.koyura.source.runtime

import app.koyura.network.applyReaderDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun imageCallbackArgs(vararg values: String?) = JsonArray(values.map {
    if (it == null) JsonNull else JsonPrimitive(it)
})

private fun imageScript(onImage: String, onThumb: String? = null) = """
    class ImageSource extends ComicSource {
        name = "Image";
        key = "image_source";
        version = "1.0.0";
        minAppVersion = "1.16.0";
        comic = {
            onImageLoad: $onImage,
            ${onThumb?.let { "onThumbnailLoad: $it," } ?: ""}
            onClickTag() { return null; },
        };
    }
""".trimIndent()

class VeneraImageCallbacksTest {
    private suspend fun withImageRuntime(
        script: String,
        block: suspend (VeneraSourceRuntime) -> Unit,
    ) {
        val runtime = VeneraSourceRuntime.create(
            VeneraEnvironment("1.16.0", "zh_CN", "test"),
            KtorVeneraHttpTransport(HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }),
        )
        try {
            runtime.register(script, "image.js", expectedKey = "image_source")
            block(runtime)
            assertEquals(0, runtime.imageCallbackCount(), "all image callbacks must be released")
        } finally {
            runtime.close()
        }
    }

    @Test
    fun parsesPlainConfigWithoutCallbacks() = runTest {
        withImageRuntime(
            imageScript("""(key) => ({ url: key, headers: { "Referer": "https://example.invalid/" } })"""),
        ) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("https://example.invalid/a.jpg", "w"),
            )
            assertEquals("https://example.invalid/a.jpg", config.url)
            assertEquals(mapOf("Referer" to "https://example.invalid/"), config.headers)
            assertEquals(null, config.onResponse)
            assertEquals(null, config.onLoadFailed)
            assertTrue(config.liveHandles.isEmpty())
        }
    }

    @Test
    fun syncAndAsyncOnResponseRoundTripBytes() = runTest {
        for (async in listOf(false, true)) {
            val body = if (async) "async (b) => b" else "(b) => b"
            withImageRuntime(imageScript("""(key) => ({ url: key, onResponse: $body })""")) { runtime ->
                val config = runtime.parseImageConfig(
                    "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
                )
                val handle = config.onResponse ?: error("expected onResponse")
                try {
                    assertEquals(
                        listOf(7.toByte(), 8.toByte()),
                        runtime.invokeImageOnResponse(handle, byteArrayOf(7, 8)).toList(),
                    )
                } finally {
                    runtime.releaseImageCallbacks(config.liveHandles)
                }
            }
        }
    }

    @Test
    fun viewOffsetsAndArrayBuffersNormalize() = runTest {
        withImageRuntime(
            imageScript("""(key) => ({ url: key, onResponse: (b) => b.subarray(1, 4) })"""),
        ) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(
                    listOf(1.toByte(), 2.toByte(), 3.toByte()),
                    runtime.invokeImageOnResponse(config.onResponse!!, byteArrayOf(0, 1, 2, 3, 4)).toList(),
                )
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
        }
        withImageRuntime(
            imageScript("""(key) => ({ url: key, onResponse: (b) => b.buffer })"""),
        ) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(
                    listOf(5.toByte(), 6.toByte()),
                    runtime.invokeImageOnResponse(config.onResponse!!, byteArrayOf(5, 6)).toList(),
                )
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
        }
    }

    @Test
    fun invalidTransformResultsAreRejected() = runTest {
        // Plain integer arrays are valid (upstream accepts List<int>).
        withImageRuntime(imageScript("""(key) => ({ url: key, onResponse: (b) => [9, 8] })""")) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(
                    listOf(9.toByte(), 8.toByte()),
                    runtime.invokeImageOnResponse(config.onResponse!!, byteArrayOf(1)).toList(),
                )
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
        }
        for (body in listOf("42", "null", "\"x\"", "[\"a\"]", "[1.5]", "[256]", "{}")) {
            withImageRuntime(imageScript("""(key) => ({ url: key, onResponse: (b) => ($body) })""")) { runtime ->
                val config = runtime.parseImageConfig(
                    "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
                )
                try {
                    assertFailsWith<VeneraScriptException> {
                        runtime.invokeImageOnResponse(config.onResponse!!, byteArrayOf(1))
                    }
                } finally {
                    runtime.releaseImageCallbacks(config.liveHandles)
                }
            }
        }
    }

    @Test
    fun nonObjectConfigsAndNestedFunctionsAreRejected() = runTest {
        for (body in listOf("[{ onResponse: (b) => b }]", "42", "\"s\"", "null")) {
            withImageRuntime(imageScript("""(key) => ($body)""")) { runtime ->
                assertFailsWith<VeneraScriptException> {
                    runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"))
                }
                assertEquals(0, runtime.imageCallbackCount())
            }
        }
        withImageRuntime(
            imageScript("""(key) => ({ url: key, nested: { onResponse: (b) => b } })"""),
        ) { runtime ->
            assertFailsWith<VeneraScriptException> {
                runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"))
            }
            assertEquals(0, runtime.imageCallbackCount())
        }
    }

    @Test
    fun decodeFailureAfterSerializationReleasesOnlyTheRejectedConfig() = runTest {
        val script = imageScript(
            """
            (key) => {
                const malformed = () => {
                    const headers = { "X-Example": "seven" };
                    Object.defineProperty(headers, "toJSON", { value: () => ({ "X-Example": 7 }) });
                    return { url: key, headers, onResponse: (bytes) => bytes };
                };
                if (key === "retry") return { url: key, onLoadFailed: malformed };
                return malformed();
            }
            """.trimIndent(),
        )
        withImageRuntime(script) { runtime ->
            assertFailsWith<VeneraScriptException> {
                runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("initial"))
            }
            assertEquals(0, runtime.imageCallbackCount())

            val first = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("retry"),
            )
            try {
                assertFailsWith<VeneraScriptException> {
                    runtime.invokeImageOnLoadFailed(checkNotNull(first.onLoadFailed))
                }
                assertEquals(1, runtime.imageCallbackCount(), "only the caller-owned callback remains")
            } finally {
                runtime.releaseImageCallbacks(first.liveHandles)
            }
        }
    }

    @Test
    fun cancellationBeforeDispatcherDeliversConfigReleasesItsHandle() = runTest {
        withImageRuntime(imageScript("(key) => ({ url: key, onResponse: (bytes) => bytes })")) { runtime ->
            val continuations = Channel<Runnable>(Channel.UNLIMITED)
            val dispatcher = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    check(continuations.trySend(block).isSuccess)
                }
            }
            val load = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("u"))
                error("The cancelled caller must not receive the configuration")
            }

            // Let the engine finish registering the handle, then cancel before running the
            // continuation that would deliver ownership to the caller.
            val deliver = continuations.receive()
            val handlesBeforeCancellation = runtime.imageCallbackCount()
            load.cancel()
            deliver.run()
            while (!load.isCompleted) {
                select<Unit> {
                    load.onJoin {}
                    continuations.onReceive { it.run() }
                }
            }
            assertEquals(1, handlesBeforeCancellation)
            assertEquals(0, runtime.imageCallbackCount())
        }
    }

    @Test
    fun thrownCallbackReleasesItsReferences() = runTest {
        val shared = "(b) => b"
        withImageRuntime(
            imageScript("""(key) => { throw { message: "boom", nested: [$shared] }; }"""),
        ) { runtime ->
            val failure = assertFailsWith<VeneraScriptException> {
                runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"))
            }
            assertTrue(failure.message.orEmpty().isNotEmpty())
            assertEquals(0, runtime.imageCallbackCount())
        }
        // The shared function itself was never adopted, so it stays usable.
        withImageRuntime(imageScript("""(key) => ({ url: key, onResponse: $shared })""")) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(listOf(3.toByte()), runtime.invokeImageOnResponse(config.onResponse!!, byteArrayOf(3)).toList())
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
        }
    }

    @Test
    fun modifyImageRejectsFunctionInsteadOfScriptString() = runTest {
        withImageRuntime(
            imageScript("""(key) => ({ url: key, modifyImage: image => image })"""),
        ) { runtime ->
            val failure = assertFailsWith<VeneraScriptException> {
                runtime.parseImageConfig("image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"))
            }
            assertTrue(failure.message.orEmpty().contains("Image config field is not a string: modifyImage"))
            assertEquals(0, runtime.imageCallbackCount())
        }
    }

    @Test
    fun onLoadFailedRoundTripsNewConfig() = runTest {
        withImageRuntime(
            imageScript("""(key) => ({ url: key, onLoadFailed: () => ({ url: "https://example.invalid/retry.jpg" }) })"""),
        ) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            val replacement = try {
                runtime.invokeImageOnLoadFailed(requireNotNull(config.onLoadFailed))
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
            assertEquals("https://example.invalid/retry.jpg", replacement?.url)
            if (replacement != null) {
                runtime.releaseImageCallbacks(replacement.liveHandles)
            }
            assertEquals(0, runtime.imageCallbackCount())
        }
    }

    @Test
    fun declinedOnLoadFailedReturnsNull() = runTest {
        withImageRuntime(
            imageScript("""(key) => ({ url: key, onLoadFailed: () => null })"""),
        ) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(null, runtime.invokeImageOnLoadFailed(requireNotNull(config.onLoadFailed)))
            } finally {
                runtime.releaseImageCallbacks(config.liveHandles)
            }
        }
    }

    @Test
    fun releasedHandlesCannotBeInvoked() = runTest {
        withImageRuntime(imageScript("""(key) => ({ url: key, onResponse: (b) => b })""")) { runtime ->
            val config = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            runtime.releaseImageCallbacks(config.liveHandles)
            assertFailsWith<VeneraScriptException> {
                runtime.invokeImageOnResponse(requireNotNull(config.onResponse), byteArrayOf(1))
            }
            assertEquals(0, runtime.imageCallbackCount())
        }
    }

    @Test
    fun oldHandlesStillInvokeOldCodeAfterReplace() = runTest {
        val v1 = imageScript("""(key) => ({ url: key, onResponse: (b) => [9] })""")
        val v2 = imageScript("""(key) => ({ url: key, onResponse: (b) => [8] })""")
        val runtime = VeneraSourceRuntime.create(
            VeneraEnvironment("1.16.0", "zh_CN", "test"),
            KtorVeneraHttpTransport(HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }),
        )
        try {
            runtime.register(v1, "image.js", expectedKey = "image_source")
            val old = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            runtime.replace("image_source", v2, "image.js", expectedKey = "image_source")
            // The old handle still runs the old function; it never resolves to the new instance.
            assertEquals(listOf(9.toByte()), runtime.invokeImageOnResponse(requireNotNull(old.onResponse), byteArrayOf(1)).toList())
            val current = runtime.parseImageConfig(
                "image_source", VeneraCallback.LoadImage, imageCallbackArgs("u", "w"),
            )
            try {
                assertEquals(listOf(8.toByte()), runtime.invokeImageOnResponse(requireNotNull(current.onResponse), byteArrayOf(1)).toList())
            } finally {
                runtime.releaseImageCallbacks(current.liveHandles)
            }
            runtime.releaseImageCallbacks(old.liveHandles)
            assertEquals(0, runtime.imageCallbackCount())
        } finally {
            runtime.close()
        }
    }
}
