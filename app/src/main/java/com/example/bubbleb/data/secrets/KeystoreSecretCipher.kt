package com.example.bubbleb.data.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AndroidKeyStore retains the non-exportable AES key. Cipher creates a fresh random IV per write. */
class KeystoreSecretCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {
    private val store: KeyStore get() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun encrypt(plaintext: ByteArray, binding: ByteArray): EncryptedSecret {
        val key = store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance("AES", KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(binding)
        return EncryptedSecret(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(secret: EncryptedSecret, binding: ByteArray): ByteArray {
        val key = store.getKey(alias, null) as? SecretKey ?: throw InvalidSecretKey()
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, secret.iv))
            updateAAD(binding)
            doFinal(secret.ciphertext)
        }
    }

    override fun reset() { store.deleteEntry(alias) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "bubbleb_provider_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
