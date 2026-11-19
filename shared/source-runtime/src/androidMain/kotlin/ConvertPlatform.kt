package app.koyura.source.runtime

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val gbk = Charset.forName("GBK")

internal actual fun encodeSourceGbk(text: String) = text.toByteArray(gbk)
internal actual fun decodeSourceGbk(bytes: ByteArray) = bytes.toString(gbk)

internal actual fun cryptSourceAes(bytes: ByteArray, key: ByteArray, iv: ByteArray?, encrypt: Boolean): ByteArray {
    val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
    val secret = SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance(if (iv == null) "AES/ECB/NoPadding" else "AES/CBC/NoPadding")
    if (iv == null) cipher.init(mode, secret) else cipher.init(mode, secret, IvParameterSpec(iv))
    return cipher.doFinal(bytes)
}

internal actual fun decryptSourceRsa(bytes: ByteArray, key: ByteArray): ByteArray {
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key))
    val blockSize = ((privateKey as RSAKey).modulus.bitLength() + 7) / 8
    require(bytes.size % blockSize == 0)
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, privateKey) }
    return ByteArrayOutputStream().use { output ->
        for (offset in bytes.indices step blockSize) output.write(cipher.doFinal(bytes, offset, blockSize))
        output.toByteArray()
    }
}
