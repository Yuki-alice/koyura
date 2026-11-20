package app.koyura.source.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class ComputeHost(private val app: JsonObject) {
    private val slots = Semaphore(2)

    suspend fun dispatch(payload: String): String = try {
        val request = Json.decodeFromString<ComputeRequest>(payload)
        withTimeout(15_000) {
            slots.withPermit {
                val engine = QuickJs.create(Dispatchers.Default)
                try {
                    engine.memoryLimit = 16L * 1024 * 1024
                    engine.evaluationTimeoutMillis = 10_000
                    engine.function<String, String>("__readerConvert", ::convertSourceValue)
                    val html = HtmlHost()
                    engine.function<String, String>("__readerHtml", html::dispatch)
                    engine.evaluate<String>(
                        """
                        const APP = Object.freeze($app);
                        $utilityBootstrap
                        $htmlBootstrap
                        ;await (async () => {
                            const source = ${JsonPrimitive(request.code)};
                            let compile;
                            try {
                                compile = Function("return (" + source + "\n);");
                            } catch (error) {
                                if (!(error instanceof SyntaxError)) throw error;
                            }
                            const fn = compile ? compile() : (0, eval)(source);
                            if (typeof fn !== "function") throw new TypeError("compute expects a function");
                            const args = __readerUtilities.unpack(JSON.parse(${JsonPrimitive(request.args.toString())}));
                            return JSON.stringify({ value: __readerUtilities.pack(await Reflect.apply(fn, undefined, args)) });
                        })();
                        """.trimIndent(),
                        filename = "venera:compute",
                    )
                } finally {
                    engine.close()
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        JsonObject(mapOf("error" to JsonPrimitive(error.message ?: "Computation failed"))).toString()
    }

    @Serializable
    private data class ComputeRequest(val code: String, val args: JsonElement)
}
