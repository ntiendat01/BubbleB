package com.example.bubbleb.llm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.bubbleb.data.config.LlmEndpoint
import com.example.bubbleb.data.config.ProviderDefaults
import com.example.bubbleb.data.settings.StoredProviderRepository
import com.example.bubbleb.domain.llm.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

private class MemorySecrets : SecretStore {
    var value: String? = null
    var binding: String? = null
    var invalid = false
    override suspend fun read(binding: String): SecretRead = when {
        invalid || (value != null && this.binding != binding) -> SecretRead.Invalidated
        value == null -> SecretRead.Missing
        else -> SecretRead.Available(value!!)
    }
    override suspend fun write(binding: String, value: String) { this.binding = binding; this.value = value; invalid = false }
    override suspend fun clear() { value = null; binding = null }
}

class StoredProviderRepositoryTest {
    private val dir = Files.createTempDirectory("bubbleb-provider-test").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferencesFile = File(dir, "settings.preferences_pb")
    private val preferences = PreferenceDataStoreFactory.create(scope = scope) { preferencesFile }
    private val secrets = MemorySecrets()
    private val empty = LlmEndpoint("", "")
    private val defaults = ProviderDefaults(LlmEndpoint(ProviderPreset.BASE_URL, "synthetic" + "-debug-value"), ProviderPreset.MODEL, "", "deepseek", empty, empty)
    private val repo = StoredProviderRepository(preferences, secrets, defaults)
    @After fun cleanup() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively(); Unit }

    @Test fun debugFallbackIsReadOnlyAndSavedConfigTakesPriorityWithoutPlaintextPreferenceKey() = runBlocking {
        repo.refresh()
        assertTrue(repo.status.value.ready)
        assertEquals(ProviderSource.DEBUG, repo.status.value.source)
        val sample = "synthetic" + "-saved-value"
        repo.save(ProviderConfig(model = "custom/model"), sample)
        assertEquals(ProviderSource.SAVED, repo.status.value.source)
        assertEquals("custom/model", repo.resolve()!!.config.model)
        assertEquals(sample, repo.resolve()!!.apiKey)
        assertFalse(preferencesFile.readBytes().toString(Charsets.UTF_8).contains(sample))
    }

    @Test fun changedEndpointCannotSilentlyReuseExistingKey() = runBlocking {
        repo.save(ProviderConfig(), "synthetic" + "-saved-value")
        try {
            repo.save(ProviderConfig(baseUrl = "https://different.example/v1"), null)
            fail("Expected explicit new credential")
        } catch (e: ProviderException) { assertEquals(ProviderProblem.KEY_REQUIRED, e.problem) }
        assertEquals(ProviderPreset.BASE_URL, repo.resolve()!!.config.baseUrl)
    }

    @Test fun invalidatedSavedKeyNeverFallsBackToDebugKey() = runBlocking {
        repo.save(ProviderConfig(), "synthetic" + "-saved-value")
        secrets.invalid = true
        assertNull(repo.resolve())
        assertFalse(repo.status.value.ready)
        assertEquals(KeyIssue.INVALIDATED, repo.status.value.keyIssue)
        assertEquals(ProviderSource.SAVED, repo.status.value.source)
    }

    @Test fun removalDisablesDebugFallbackAndKeyRotationChangesRevision() = runBlocking {
        repo.refresh()
        val old = repo.status.value.revision
        repo.save(ProviderConfig(), "synthetic" + "-new-value")
        assertTrue(repo.status.value.revision > old)
        val saved = repo.status.value.revision
        repo.save(ProviderConfig(), "synthetic" + "-rotation")
        assertTrue(repo.status.value.revision > saved)
        repo.clear()
        assertNull(repo.resolve())
        assertEquals(ProviderSource.NONE, repo.status.value.source)
        assertNull(secrets.value)
    }

    @Test fun invalidUrlOrLimitsAreRejectedBeforeSecretWrite() = runBlocking {
        listOf(ProviderConfig(baseUrl = "http" + "://fixture.example/v1"), ProviderConfig(inputLimit = 7_000), ProviderConfig(maxOutputTokens = 99_999)).forEach {
            try { repo.save(it, "synthetic" + "-saved-value"); fail("Expected config rejection") }
            catch (e: ProviderException) { assertEquals(ProviderProblem.INVALID_CONFIG, e.problem) }
        }
        assertNull(secrets.value)
    }
}
