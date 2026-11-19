@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.koyura.source.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyGetBlockSize
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1
import platform.iconv.iconv
import platform.iconv.iconv_close
import platform.iconv.iconv_open

internal actual fun encodeSourceGbk(text: String): ByteArray =
    transcode(text.encodeToByteArray(), "UTF-8", "GBK")

internal actual fun decodeSourceGbk(bytes: ByteArray): String =
    transcode(bytes, "GBK", "UTF-8").decodeToString()

private fun transcode(bytes: ByteArray, from: String, to: String): ByteArray = memScoped {
    if (bytes.isEmpty()) return@memScoped bytes
    val descriptor = checkNotNull(iconv_open(to, from))
    check(descriptor.rawValue.toLong() != -1L) { "Character encoding is unavailable" }
    try {
        val output = ByteArray(bytes.size * 4)
        val source = alloc<CPointerVar<ByteVar>>()
        val target = alloc<CPointerVar<ByteVar>>()
        val remaining = alloc<ULongVar>().apply { value = bytes.size.toULong() }
        val capacity = alloc<ULongVar>().apply { value = output.size.toULong() }
        val converted = bytes.usePinned { input ->
            output.usePinned { result ->
                source.value = input.addressOf(0)
                target.value = result.addressOf(0)
                iconv(descriptor, source.ptr, remaining.ptr, target.ptr, capacity.ptr)
            }
        }
        check(converted != ULong.MAX_VALUE && remaining.value == 0uL) { "Character conversion failed" }
        output.copyOf(output.size - capacity.value.toInt())
    } finally {
        iconv_close(descriptor)
    }
}

internal actual fun cryptSourceAes(bytes: ByteArray, key: ByteArray, iv: ByteArray?, encrypt: Boolean): ByteArray {
    require(key.size in setOf(16, 24, 32) && bytes.size % 16 == 0 && (iv == null || iv.size == 16))
    if (bytes.isEmpty()) return bytes
    val output = ByteArray(bytes.size)
    val vector = iv ?: ByteArray(16)
    memScoped {
        val written = alloc<ULongVar>()
        val status = bytes.usePinned { input ->
            key.usePinned { secret ->
                vector.usePinned { ivPointer ->
                    output.usePinned { result ->
                        CCCrypt(
                            if (encrypt) kCCEncrypt else kCCDecrypt, kCCAlgorithmAES,
                            if (iv == null) kCCOptionECBMode else 0u,
                            secret.addressOf(0), key.size.toULong(), ivPointer.addressOf(0),
                            input.addressOf(0), bytes.size.toULong(), result.addressOf(0), output.size.toULong(), written.ptr,
                        )
                    }
                }
            }
        }
        check(status == kCCSuccess && written.value == output.size.toULong()) { "AES conversion failed" }
    }
    return output
}

internal actual fun decryptSourceRsa(bytes: ByteArray, key: ByteArray): ByteArray {
    // SecKey imports PKCS#1. Source scripts supply its PKCS#8 wrapper.
    val pkcs8 = DerReader(DerReader(key).read(0x30))
    pkcs8.read(0x02)
    pkcs8.read(0x30)
    val raw = pkcs8.read(0x04)
    val rawKey = data(raw)
    val attributes = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
    try {
        CFDictionarySetValue(attributes, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        val privateKey = checkNotNull(SecKeyCreateWithData(rawKey, attributes, null))
        try {
            val block = SecKeyGetBlockSize(privateKey).toInt()
            require(block > 0 && bytes.size % block == 0)
            val output = Buffer()
            for (offset in bytes.indices step block) {
                val encrypted = data(bytes.copyOfRange(offset, offset + block))
                try {
                    val plaintext = checkNotNull(SecKeyCreateDecryptedData(privateKey, kSecKeyAlgorithmRSAEncryptionPKCS1, encrypted, null))
                    try {
                        output.write(dataBytes(plaintext))
                    } finally {
                        CFRelease(plaintext)
                    }
                } finally {
                    CFRelease(encrypted)
                }
            }
            return output.readByteArray()
        } finally {
            CFRelease(privateKey)
        }
    } finally {
        CFRelease(attributes)
        CFRelease(rawKey)
    }
}

private fun data(bytes: ByteArray): CFDataRef = bytes.usePinned {
    checkNotNull(CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()))
}

private fun dataBytes(data: CFDataRef): ByteArray =
    CFDataGetBytePtr(data)?.readBytes(CFDataGetLength(data).toInt()) ?: ByteArray(0)

private class DerReader(private val bytes: ByteArray) {
    private var offset = 0
    private fun byte() = bytes[offset++].toInt() and 255

    fun read(tag: Int): ByteArray {
        require(byte() == tag)
        val lengthMarker = byte()
        val length = if (lengthMarker < 128) lengthMarker else {
            val count = lengthMarker and 127
            require(count in 1..3)
            var size = 0
            repeat(count) { size = size * 256 + byte() }
            size
        }
        require(length <= bytes.size - offset)
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }
}
