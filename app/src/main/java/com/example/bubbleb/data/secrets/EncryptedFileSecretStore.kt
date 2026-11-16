package com.example.bubbleb.data.secrets

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.AtomicFile
import com.example.bubbleb.domain.llm.LlmLimits
import com.example.bubbleb.domain.llm.ProviderException
import com.example.bubbleb.domain.llm.ProviderProblem
import com.example.bubbleb.domain.llm.SecretRead
import com.example.bubbleb.domain.llm.SecretStore
import java.io.File
import java.io.FileNotFoundException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Only a versioned IV + authenticated ciphertext is written, under noBackupFilesDir in production. */
class EncryptedFileSecretStore(file: File, private val cipher: SecretCipher) : SecretStore {
    private val file = AtomicFile(file)
    private val mutex = Mutex()

    override suspend fun read(binding: String): SecretRead = withContext(Dispatchers.IO) { mutex.withLock {
        try {
            val bytes = file.openRead().use { stream ->
                val buffer = ByteArray(SecretEnvelope.MAX_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = stream.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
            val plaintext = cipher.decrypt(SecretEnvelope.decode(bytes), binding.toByteArray(Charsets.UTF_8))
            try {
                val value = plaintext.toString(Charsets.UTF_8)
                if (!validKey(value)) throw IllegalArgumentException()
                SecretRead.Available(value)
            } finally { plaintext.fill(0) }
        } catch (_: FileNotFoundException) {
            SecretRead.Missing
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is InvalidSecretKey || e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException ||
                e is AEADBadTagException || e is IllegalArgumentException) {
                file.delete()
                runCatching { cipher.reset() }
                SecretRead.Invalidated
            } else SecretRead.Unavailable
        }
    } }

    override suspend fun write(binding: String, value: String) = withContext(Dispatchers.IO) { mutex.withLock {
        if (!validKey(value)) throw ProviderException(ProviderProblem.KEY_REQUIRED)
        val plaintext = value.toByteArray(Charsets.UTF_8)
        try {
            val aad = binding.toByteArray(Charsets.UTF_8)
            val encrypted = try { cipher.encrypt(plaintext, aad) } catch (e: Exception) {
                if (e !is KeyPermanentlyInvalidatedException && e !is InvalidSecretKey && e !is UnrecoverableKeyException) throw e
                cipher.reset()
                cipher.encrypt(plaintext, aad)
            }
            val stream = file.startWrite()
            try {
                stream.write(SecretEnvelope.encode(encrypted))
                file.finishWrite(stream)
            } catch (e: Exception) { file.failWrite(stream); throw e }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            throw ProviderException(ProviderProblem.STORAGE_UNAVAILABLE)
        } finally { plaintext.fill(0) }
    } }

    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        file.delete()
        try { cipher.reset() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw ProviderException(ProviderProblem.STORAGE_UNAVAILABLE) }
    } }

    companion object {
        fun validKey(value: String): Boolean = value.isNotBlank() && value.length <= LlmLimits.MAX_KEY_CHARS &&
            value.all { it.code in 33..126 }
    }
}
