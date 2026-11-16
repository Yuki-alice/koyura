package dev.reader.source.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A registry of Venera sources in one QuickJS runtime. Callback parameters and results use the
 * Venera JSON shapes. The transport owns network policy and sessions for its source key.
 */
class VeneraSourceRuntime private constructor(
    private val engine: QuickJs,
    private val supportedVersion: VeneraVersion,
) {
    private val mutex = Mutex()
    private val sources = linkedMapOf<String, VeneraSource>()

    suspend fun register(
        script: String,
        fileName: String = "source.js",
        expectedKey: String? = null,
    ): VeneraSource = withContext(Dispatchers.Default) {
        val code = script.removePrefix("\uFEFF")
        val className = entryClass.find(code)?.groupValues?.get(1)
            ?: throw VeneraScriptException("Expected a class extending ComicSource in $fileName")
        val callbackPaths = Json.encodeToString(VeneraCallback.entries.map { it.path })

        mutex.withLock {
            checkOpen()
            try {
                val source = withTimeout(INITIALIZE_TIMEOUT_MILLIS) {
                    val result = evaluate<String>(
                        """
                        __readerVenera.stage((Network, sendMessage) => {
                        $code
                        ;
                        return new $className();
                        }, $callbackPaths)
                        """.trimIndent(),
                        fileName,
                    )
                    val descriptor = decodeRegistration(result, fileName)
                    validate(descriptor, expectedKey)
                    val initialized = evaluate<Boolean>(
                        "await __readerVenera.initialize(${JsonPrimitive(descriptor.key)})",
                        fileName,
                    )
                    check(initialized) { "Source '${descriptor.key}' did not finish initialization" }
                    descriptor
                }
                withContext(NonCancellable) {
                    evaluate<Any?>("__readerVenera.commit()", fileName)
                    sources[source.key] = source
                    source
                }
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    try {
                        evaluate<Any?>("__readerVenera.rollback()", fileName)
                    } catch (rollback: Throwable) {
                        failure.addSuppressed(rollback)
                        try {
                            engine.close()
                        } catch (close: Throwable) {
                            failure.addSuppressed(close)
                        } finally {
                            sources.clear()
                        }
                    }
                }
                throw failure
            }
        }
    }

    suspend fun registeredSources(): List<VeneraSource> = mutex.withLock {
        checkOpen()
        sources.values.toList()
    }

    /**
     * Opaque identity of the JS instance currently registered for [key], or
     * null when absent. Used to tell a bootstrap-restored original apart from
     * a stranded candidate after a cancelled replacement.
     */
    suspend fun liveInstanceSeq(key: String): Long? = withContext(Dispatchers.Default) {
        mutex.withLock {
            checkOpen()
            evaluate("(__readerVenera.instanceSeq(${JsonPrimitive(key)}))", "venera-seq:$key")
        }
    }

    /**
     * Puts back the instance displaced by the latest replace attempt for [key],
     * if any. Returns false when there is nothing to repair (the registry
     * already holds the pre-replace instance or nothing at all).
     */
    suspend fun repairDisplaced(key: String): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            checkOpen()
            evaluate("__readerVenera.repairDisplaced(${JsonPrimitive(key)})", "venera-repair:$key")
        }
    }

    suspend fun unregister(key: String): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            checkOpen()
            if (key !in sources) return@withLock false
            withContext(NonCancellable) {
                evaluate<Any?>("__readerVenera.unregister(${JsonPrimitive(key)})", "venera-registry.js")
                sources.remove(key)
            }
            true
        }
    }

    suspend fun call(
        key: String,
        callback: VeneraCallback,
        arguments: JsonArray = JsonArray(emptyList()),
    ): JsonElement = withContext(Dispatchers.Default) {
        mutex.withLock {
            checkOpen()
            val source = requireNotNull(sources[key]) { "Source '$key' is not registered" }
            require(callback in source.callbacks) { "Source '$key' does not provide ${callback.path}" }
            val encodedArguments = JsonPrimitive(arguments.toString())
            val script = "await __readerVenera.invoke(${JsonPrimitive(key)}, ${JsonPrimitive(callback.path)}, " +
                "JSON.parse($encodedArguments), ${callback.synchronous})"
            withTimeout(CALLBACK_TIMEOUT_MILLIS) {
                val value = evaluate<String>(script, "$key:${callback.path}")
                Json.parseToJsonElement(value)
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.Default + NonCancellable) {
        mutex.withLock {
            if (!engine.isClosed) engine.close()
            sources.clear()
        }
    }

    private fun checkOpen() {
        check(!engine.isClosed) { "Venera source runtime is closed" }
    }

    private fun validate(source: VeneraSource, expectedKey: String?) {
        validateFormat(source, expectedKey)
        require(source.key !in sources) { "Source '${source.key}' is already registered" }
    }

    private fun validateFormat(source: VeneraSource, expectedKey: String?) {
        require(source.name.isNotBlank()) { "A Venera source name is required" }
        require(sourceKeyPattern.matches(source.key)) { "Invalid Venera source key: '${source.key}'" }
        require(expectedKey == null || source.key == expectedKey) {
            "Expected source '$expectedKey', got '${source.key}'"
        }
        VeneraVersion.parse(source.version)
        source.minAppVersion?.let { minimum ->
            require(VeneraVersion.parse(minimum) <= supportedVersion) {
                "Source '${source.key}' requires Venera host version $minimum"
            }
        }
    }

    /**
     * Reads script metadata without running `init` and without touching the
     * registry: stages the constructor, decodes the descriptor, then discards
     * the pending instance. Used to name an immutable revision file before the
     * single initializing registration/replacement.
     */
    suspend fun peekMetadata(
        script: String,
        fileName: String = "source.js",
        expectedKey: String? = null,
    ): VeneraSource = withContext(Dispatchers.Default) {
        val code = script.removePrefix("\uFEFF")
        val className = entryClass.find(code)?.groupValues?.get(1)
            ?: throw VeneraScriptException("Expected a class extending ComicSource in $fileName")
        val callbackPaths = Json.encodeToString(VeneraCallback.entries.map { it.path })

        mutex.withLock {
            checkOpen()
            try {
                val result = evaluate<String>(
                    """
                    __readerVenera.stage((Network, sendMessage) => {
                    $code
                    ;
                    return new $className();
                    }, $callbackPaths)
                    """.trimIndent(),
                    fileName,
                )
                val descriptor = decodeRegistration(result, fileName)
                validateFormat(descriptor, expectedKey)
                descriptor
            } finally {
                withContext(NonCancellable) {
                    evaluate<Any?>("__readerVenera.discard()", fileName)
                }
            }
        }
    }

    /**
     * Atomically replaces the registered source for [key]. The candidate's
     * `init` runs exactly once; when it fails the previous instance (with its
     * in-memory state) stays registered and callable. When [key] is not
     * registered this behaves like [register].
     */
    suspend fun replace(
        key: String,
        script: String,
        fileName: String = "source.js",
        expectedKey: String? = key,
    ): VeneraSource = withContext(Dispatchers.Default) {
        val code = script.removePrefix("\uFEFF")
        val className = entryClass.find(code)?.groupValues?.get(1)
            ?: throw VeneraScriptException("Expected a class extending ComicSource in $fileName")
        val callbackPaths = Json.encodeToString(VeneraCallback.entries.map { it.path })

        mutex.withLock {
            checkOpen()
            val previous = sources[key]
            try {
                val source = withTimeout(INITIALIZE_TIMEOUT_MILLIS) {
                    val result = evaluate<String>(
                        """
                        __readerVenera.stage((Network, sendMessage) => {
                        $code
                        ;
                        return new $className();
                        }, $callbackPaths)
                        """.trimIndent(),
                        fileName,
                    )
                    val descriptor = decodeRegistration(result, fileName)
                    validateFormat(descriptor, expectedKey)
                    require(descriptor.key == key) {
                        "Expected source '$key', got '${descriptor.key}'"
                    }
                    val initialized = evaluate<Boolean>(
                        "await __readerVenera.initializeReplace(${JsonPrimitive(descriptor.key)})",
                        fileName,
                    )
                    check(initialized) { "Source '${descriptor.key}' did not finish initialization" }
                    descriptor
                }
                withContext(NonCancellable) {
                    evaluate<Any?>("__readerVenera.commit()", fileName)
                    sources[source.key] = source
                    source
                }
            } catch (failure: Throwable) {
                // The engine already restored the previous registry entry;
                // only the pending slot needs clearing. The engine stays open
                // so the surviving instance remains callable. A failure that
                // arrives while the caller is cancelled is rethrown as
                // cancellation even if the JS bridge wrapped it.
                withContext(NonCancellable) {
                    try {
                        evaluate<Any?>("__readerVenera.discard()", fileName)
                    } catch (cleanup: Throwable) {
                        failure.addSuppressed(cleanup)
                    }
                }
                if (failure !is CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
                if (previous != null) check(sources[key] === previous || key !in sources) {
                    "Source '$key' registry was left inconsistent after failed replacement"
                }
                throw failure
            }
        }
    }

    private suspend inline fun <reified T> evaluate(script: String, fileName: String): T {
        try {
            return engine.evaluate<T>(script, filename = fileName)
        } catch (error: QuickJsException) {
            throw VeneraScriptException("$fileName: ${error.message}", error)
        }
    }

    private fun decodeRegistration(value: String, fileName: String): VeneraSource {
        val source = try {
            Json.decodeFromString<Registration>(value)
        } catch (error: SerializationException) {
            throw VeneraScriptException("Invalid source metadata in $fileName", error)
        }
        return VeneraSource(
            name = source.name,
            key = source.key,
            version = source.version,
            minAppVersion = source.minAppVersion,
            url = source.url,
            callbacks = VeneraCallback.entries.filterTo(linkedSetOf()) { it.path in source.callbacks },
        )
    }

    @Serializable
    private data class Registration(
        val name: String,
        val key: String,
        val version: String,
        val minAppVersion: String? = null,
        val url: String? = null,
        val callbacks: List<String>,
    )

    companion object {
        private const val INITIALIZE_TIMEOUT_MILLIS = 15_000L
        private const val CALLBACK_TIMEOUT_MILLIS = 45_000L
        private const val ENGINE_MEMORY_BYTES = 32L * 1024 * 1024
        private val entryClass = Regex(
            """^\s*class\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s+extends\s+ComicSource\b""",
            RegexOption.MULTILINE,
        )
        private val sourceKeyPattern = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
        private val hostJson = Json { encodeDefaults = true }

        suspend fun create(
            environment: VeneraEnvironment,
            transport: VeneraHttpTransport,
        ): VeneraSourceRuntime {
            val supportedVersion = VeneraVersion.parse(environment.compatibleAppVersion.substringBefore('-'))
            val engine = QuickJs.create(Dispatchers.Default)
            val runtime = VeneraSourceRuntime(engine, supportedVersion)
            try {
                withContext(Dispatchers.Default) {
                    engine.memoryLimit = ENGINE_MEMORY_BYTES
                    engine.evaluationTimeoutMillis = INITIALIZE_TIMEOUT_MILLIS
                    engine.asyncFunction<String, String>("__readerRequestText") { payload ->
                        try {
                            val request = Json.decodeFromString<VeneraHttpRequest>(payload)
                            hostJson.encodeToString(transport.execute(request))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            val message = JsonPrimitive(error.message ?: "HTTP request failed")
                            JsonObject(mapOf("error" to message)).toString()
                        }
                    }
                    val app = JsonObject(
                        mapOf(
                            "version" to JsonPrimitive(environment.compatibleAppVersion),
                            "locale" to JsonPrimitive(environment.locale),
                            "platform" to JsonPrimitive(environment.platform),
                        ),
                    )
                    runtime.evaluate<Any?>("const APP = Object.freeze($app);\n$veneraBootstrap", "venera-host.js")
                }
                return runtime
            } catch (failure: Throwable) {
                try {
                    runtime.close()
                } catch (close: Throwable) {
                    failure.addSuppressed(close)
                }
                throw failure
            }
        }
    }
}
