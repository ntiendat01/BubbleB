package com.example.bubbleb.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.data.secrets.*
import com.example.bubbleb.domain.llm.SecretRead
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class JvmAesCipher : SecretCipher {
    private var key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    var unavailable = false
    var invalidated = false
    override fun encrypt(plaintext: ByteArray, binding: ByteArray): EncryptedSecret {
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key); updateAAD(binding); EncryptedSecret(iv, doFinal(plaintext))
        }
    }
    override fun decrypt(secret: EncryptedSecret, binding: ByteArray): ByteArray {
        if (unavailable) throw IOException("synthetic unavailable")
        if (invalidated) throw InvalidSecretKey()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, secret.iv)); updateAAD(binding); doFinal(secret.ciphertext)
        }
    }
    override fun reset() { invalidated = false; key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 35])
class EncryptedSecretStoreTest {
    private val directory = Files.createTempDirectory("bubbleb-secret-test").toFile()
    private val file = File(directory, "secret.enc")
    private val engine = JvmAesCipher()
    private val store = EncryptedFileSecretStore(file, engine)
    private val sample = "synthetic" + "-secret-value"
    private val binding = "https://fixture.example/v1"
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun encryptedRoundTripNeverWritesPlaintextAndUsesFreshIv() = runBlocking {
        store.write(binding, sample)
        val first = file.readBytes()
        assertFalse(first.toString(Charsets.UTF_8).contains(sample))
        assertEquals(sample, (store.read(binding) as SecretRead.Available).value)
        store.write(binding, sample)
        assertFalse(first.contentEquals(file.readBytes()))
        assertFalse(SecretEnvelope.decode(first).iv.contentEquals(SecretEnvelope.decode(file.readBytes()).iv))
    }

    @Test fun changedEndpointCannotReadOrReuseKey() = runBlocking {
        store.write(binding, sample)
        assertEquals(SecretRead.Invalidated, store.read("https://different.example/v1"))
        assertFalse(file.exists())
    }

    @Test fun invalidationClearsCiphertextAndRequiresReentry() = runBlocking {
        store.write(binding, sample)
        engine.invalidated = true
        assertEquals(SecretRead.Invalidated, store.read(binding))
        assertFalse(file.exists())
        assertEquals(SecretRead.Missing, store.read(binding))
        store.write(binding, sample)
        assertEquals(sample, (store.read(binding) as SecretRead.Available).value)
    }

    @Test fun transientFailurePreservesCiphertext_corruptionDoesNotFallBackToPlaintext() = runBlocking {
        store.write(binding, sample)
        engine.unavailable = true
        assertEquals(SecretRead.Unavailable, store.read(binding))
        assertTrue(file.exists())
        engine.unavailable = false
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(SecretRead.Invalidated, store.read(binding))
        assertFalse(file.exists())
    }

    @Test fun clearDeletesKeyMaterialAndEnvelope() = runBlocking {
        store.write(binding, sample)
        store.clear()
        assertEquals(SecretRead.Missing, store.read(binding))
        assertFalse(file.exists())
    }
}
