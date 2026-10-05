package dev.teyd.justintv.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dev.teyd.justintv.core.network.SessionCodec
import dev.teyd.justintv.core.network.StoredSession
import dev.teyd.justintv.core.network.TokenVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The sign-in file. Android Keystore holds the key; the ciphertext sits in no-backup storage,
 * not in the preferences DataStore.
 */
class KeystoreTokenVault(
    context: Context,
) : TokenVault {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override suspend fun load(): StoredSession? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val plain = decrypt(file.readBytes()) ?: return@withContext null
            SessionCodec.decode(plain)
        }

    override suspend fun save(session: StoredSession) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeBytes(encrypt(SessionCodec.encode(session)))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + cipherText
    }

    private fun decrypt(payload: ByteArray): String? =
        try {
            if (payload.isEmpty()) return null
            val ivLength = payload[0].toInt() and 0xFF
            if (payload.size <= 1 + ivLength) return null
            val key = existingKey() ?: return null.also { file.delete() }
            val iv = payload.copyOfRange(1, 1 + ivLength)
            val cipherText = payload.copyOfRange(1 + ivLength, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            // A keystore that is briefly unavailable must not destroy the only copy of the
            // tokens. Delete only when the ciphertext cannot match this key.
            if (isPermanentCryptoFailure(e)) file.delete()
            null
        }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun secretKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun isPermanentCryptoFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is UnrecoverableKeyException ||
                current is KeyPermanentlyInvalidatedException ||
                current is BadPaddingException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "justintv-session"
        const val FILE_NAME = "session.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
