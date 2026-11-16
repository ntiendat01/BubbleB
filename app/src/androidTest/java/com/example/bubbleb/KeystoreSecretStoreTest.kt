package com.example.bubbleb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.data.secrets.EncryptedFileSecretStore
import com.example.bubbleb.data.secrets.KeystoreSecretCipher
import com.example.bubbleb.domain.llm.SecretRead
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real AndroidKeyStore, isolated alias/file; never touches the user's configured provider key. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val id = UUID.randomUUID().toString()
    private val cipher = KeystoreSecretCipher("bubbleb_test_$id")
    private val file = File(context.cacheDir, "bubbleb-test-$id.enc")
    private val store = EncryptedFileSecretStore(file, cipher)
    private val binding = "https://fixture.example/v1"
    private val sample = "synthetic" + "-device-value"

    @After fun cleanup() = runBlocking { store.clear() }

    @Test fun androidKeystoreRoundTripAndCiphertextOnly() = runBlocking {
        store.write(binding, sample)
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(sample))
        assertEquals(sample, (store.read(binding) as SecretRead.Available).value)
    }

    @Test fun lostKeystoreKeyRequiresReentry() = runBlocking {
        store.write(binding, sample)
        cipher.reset() // Simulates a missing key for this isolated test alias only.
        assertEquals(SecretRead.Invalidated, store.read(binding))
        assertFalse(file.exists())
        store.write(binding, sample)
        assertEquals(sample, (store.read(binding) as SecretRead.Available).value)
    }
}
