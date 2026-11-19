package app.koyura.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.koyura.source.runtime.SourceSecretStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidSourceSecretStore(context: Context) : SourceSecretStore {
    private val directory = File(context.noBackupFilesDir, "source-sessions")

    override fun read(sourceKey: String): String? {
        val file = file(sourceKey)
        if (!file.baseFile.exists()) return null
        val encoded = file.readFully()
        require(encoded.size >= 28) { "Invalid encrypted source session" }
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encoded, 0, 12))
            updateAAD(sourceKey.encodeToByteArray())
            doFinal(encoded, 12, encoded.size - 12).decodeToString()
        }
    }

    override fun write(sourceKey: String, value: String?) {
        val file = file(sourceKey)
        if (value == null) {
            file.delete()
            check(!file.baseFile.exists()) { "Could not remove encrypted source session" }
            return
        }
        val encrypted = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(sourceKey.encodeToByteArray())
            iv + doFinal(value.encodeToByteArray())
        }
        val sink = file.startWrite()
        try {
            sink.write(encrypted)
            file.finishWrite(sink)
        } catch (failure: Throwable) {
            file.failWrite(sink)
            throw failure
        }
    }

    private fun file(key: String): AtomicFile = AtomicFile(File(directory, key))

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        store.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "app.koyura.source-sessions"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val KEY_LOCK = Any()
    }
}
