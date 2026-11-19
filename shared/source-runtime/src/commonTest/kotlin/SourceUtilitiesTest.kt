package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SourceUtilitiesTest {
    @Test
    fun fetchPreservesTypedArraySlicesAndComputeTransfersBytesAcrossEngines() = runTest {
        val client = HttpClient(MockEngine { request ->
            val data = (request.body as OutgoingContent.ByteArrayContent).bytes()
            assertContentEquals(byteArrayOf(1, 255.toByte()), data)
            assertEquals(ContentType.Application.OctetStream, request.body.contentType)
            respond(byteArrayOf(255.toByte(), 0, 1), headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        })
        val transport = KtorVeneraHttpTransport(client, ownsClient = true)
        val runtime = VeneraSourceRuntime.create(environment, transport)
        try {
            runtime.register(script)
            val result = runtime.call("utilities", VeneraCallback.Search).jsonObject
            assertEquals(JsonPrimitive("ff0001"), result["download"])
            assertEquals(JsonPrimitive("0001"), result["compute"])
            assertEquals(JsonPrimitive("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), result["sha256"])
            assertEquals(JsonPrimitive("d6d0"), result["gbk"])
            assertEquals(JsonPrimitive("69c4e0d86a7b0430d8cdb78070b4c55a"), result["aes"])
        } finally {
            runtime.close()
            transport.close()
        }
    }

    @Test
    fun intervalsAdvanceAfterInitializationAndCanBeCancelled() = runTest {
        val runtime = VeneraSourceRuntime.create(environment) { error("Unexpected HTTP") }
        try {
            runtime.register(script.replace("init() {}", """
                init() {
                    this.ticks = 0;
                    this.timer = setInterval(() => this.ticks++, 10);
                }
            """.trimIndent()))
            val ticks = withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    var latest: Int
                    do {
                        delay(10)
                        latest = runtime.call("utilities", VeneraCallback.LoadInfo).toString().toInt()
                    } while (latest < 2)
                    latest
                }
            }
            withContext(Dispatchers.Default) { delay(50) }
            assertEquals(JsonPrimitive(ticks), runtime.call("utilities", VeneraCallback.LoadInfo))
        } finally {
            runtime.close()
        }
    }

    private val environment = VeneraEnvironment("1.16.0", "en", "test")
    private val script = """
        class UtilitySource extends ComicSource {
            name = "Utility"; key = "utilities"; version = "1.0.0"; minAppVersion = "1.0.0";
            init() {}
            search = {
                load: async () => {
                    await new Promise(resolve => setTimeout(resolve, 1));
                    const bytes = new Uint8Array([8, 1, 255, 9]).subarray(1, 3);
                    const response = await fetch("https://example.test/data", { method: "POST", body: bytes });
                    const body = await response.arrayBuffer();
                    const transformed = await compute("(bytes) => ({ data: new Uint8Array(bytes).subarray(1) })", body);
                    const key = Convert.hexDecode("000102030405060708090a0b0c0d0e0f");
                    const block = Convert.hexDecode("00112233445566778899aabbccddeeff");
                    return {
                        download: Convert.hexEncode(body),
                        compute: Convert.hexEncode(transformed.data),
                        sha256: Convert.hexEncode(Convert.sha256(Convert.encodeUtf8("abc"))),
                        gbk: Convert.hexEncode(Convert.encodeGbk("中")),
                        aes: Convert.hexEncode(Convert.encryptAesEcb(block, key))
                    };
                }
            };
            comic = { loadInfo: () => {
                if (this.ticks >= 2) this.timer.cancel();
                return this.ticks;
            }};
        }
    """.trimIndent()
}
