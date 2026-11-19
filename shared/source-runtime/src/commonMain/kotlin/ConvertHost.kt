package app.koyura.source.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import kotlin.uuid.Uuid

internal expect fun encodeSourceGbk(text: String): ByteArray
internal expect fun decodeSourceGbk(bytes: ByteArray): String
internal expect fun cryptSourceAes(bytes: ByteArray, key: ByteArray, iv: ByteArray?, encrypt: Boolean): ByteArray
internal expect fun decryptSourceRsa(bytes: ByteArray, key: ByteArray): ByteArray

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
internal fun convertSourceValue(payload: String): String = runCatching {
    val args = Json.parseToJsonElement(payload).jsonObject
    fun text(name: String) = args.getValue(name).jsonPrimitive.content
    fun data(name: String) = checkNotNull(text(name).decodeBase64())
    fun value() = data("value")
    fun key() = data("key").toByteArray()
    fun binary(value: ByteString) = JsonObject(mapOf("bytes" to JsonPrimitive(value.base64())))
    fun binary(value: ByteArray) = binary(value.toByteString())
    fun result(value: String) = JsonObject(mapOf("text" to JsonPrimitive(value)))
    when (val operation = text("operation")) {
        "utf8.encode" -> binary(text("value").encodeToByteArray())
        "utf8.decode" -> result(value().toByteArray().decodeToString(throwOnInvalidSequence = true))
        "gbk.encode" -> binary(encodeSourceGbk(text("value")))
        "gbk.decode" -> result(decodeSourceGbk(value().toByteArray()))
        "base64.encode" -> result(value().base64())
        "base64.decode" -> binary(checkNotNull(text("value").decodeBase64()))
        "hex.encode" -> result(value().hex())
        "hex.decode" -> binary(text("value").decodeHex())
        "md5", "sha1", "sha256", "sha512" -> binary(value().digest(operation))
        "hmac", "hmacString" -> {
            val hash = text("hash")
            val digest = hmac(key(), value().toByteArray(), hash)
            if (operation == "hmacString") result(digest.hex()) else binary(digest)
        }
        "aes-ecb", "aes-cbc" -> binary(
            cryptSourceAes(value().toByteArray(), key(), if (operation == "aes-cbc") data("iv").toByteArray() else null,
                text("encrypt").toBooleanStrict()),
        )
        "aes-cfb", "aes-ofb" -> binary(feedbackAes(
            value().toByteArray(), key(), if (operation == "aes-cfb") data("iv").toByteArray() else ByteArray(16),
            args.getValue("blockSize").jsonPrimitive.int,
            text("encrypt").toBooleanStrict(), operation == "aes-cfb",
        ))
        "rsa" -> binary(decryptSourceRsa(value().toByteArray(), key()))
        "uuid" -> result(Uuid.generateV7().toString())
        else -> JsonNull
    }.toString()
}.getOrDefault("null")

private fun ByteString.digest(hash: String): ByteString = when (hash) {
    "md5" -> md5()
    "sha1" -> sha1()
    "sha256" -> sha256()
    "sha512" -> sha512()
    else -> error("Unknown hash")
}

private fun hmac(key: ByteArray, message: ByteArray, hash: String): ByteString {
    val size = if (hash == "sha512") 128 else 64
    val normalized = if (key.size > size) key.toByteString().digest(hash).toByteArray() else key
    val inner = ByteArray(size) { i -> ((normalized.getOrNull(i)?.toInt() ?: 0) xor 0x36).toByte() }
    val outer = ByteArray(size) { i -> ((normalized.getOrNull(i)?.toInt() ?: 0) xor 0x5c).toByte() }
    return (outer + (inner + message).toByteString().digest(hash).toByteArray()).toByteString().digest(hash)
}

private fun feedbackAes(
    bytes: ByteArray, key: ByteArray, iv: ByteArray, blockSize: Int, encrypt: Boolean, cipherFeedback: Boolean,
): ByteArray {
    require(blockSize in 8..128 && blockSize % 8 == 0 && iv.size == 16)
    val span = blockSize / 8
    require(bytes.size % span == 0)
    val state = iv.copyOf()
    val result = ByteArray(bytes.size)
    for (offset in bytes.indices step span) {
        val output = cryptSourceAes(state, key, null, true)
        for (index in 0 until span) result[offset + index] = (bytes[offset + index].toInt() xor output[index].toInt()).toByte()
        state.copyInto(state, 0, span, state.size)
        when {
            !cipherFeedback -> output.copyInto(state, state.size - span, 0, span)
            encrypt -> result.copyInto(state, state.size - span, offset, offset + span)
            else -> bytes.copyInto(state, state.size - span, offset, offset + span)
        }
    }
    return result
}
