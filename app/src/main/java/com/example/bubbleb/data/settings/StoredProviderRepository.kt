package com.example.bubbleb.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bubbleb.data.config.ProviderDefaults
import com.example.bubbleb.data.llm.ProviderUrl
import com.example.bubbleb.data.secrets.EncryptedFileSecretStore
import com.example.bubbleb.domain.llm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Non-secret metadata in DataStore; the secret is authenticated to its endpoint using AES-GCM AAD. */
class StoredProviderRepository(
    private val dataStore: DataStore<Preferences>,
    private val secrets: SecretStore,
    private val defaults: ProviderDefaults,
) : ProviderRepository {
    private val mutable = MutableStateFlow(ProviderStatus())
    override val status: StateFlow<ProviderStatus> = mutable
    private val mutex = Mutex()
    private var revision = 0L

    override suspend fun refresh() { resolve() }

    override suspend fun resolve(): ProviderCredentials? = withContext(Dispatchers.IO) { mutex.withLock {
        try { load() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            publish(ProviderStatus(keyIssue = KeyIssue.UNAVAILABLE))
            null
        }
    } }

    private suspend fun load(): ProviderCredentials? {
        val preferences = dataStore.data.first()
        val stored = preferences[CONFIG]
        if (stored != null) {
            val config = try { Json.decodeFromString<ProviderConfig>(stored) } catch (_: Exception) { null }
            if (config == null || !config.fieldsValid() || ProviderUrl.normalize(config.baseUrl) != config.baseUrl) {
                publish(ProviderStatus(keyIssue = KeyIssue.UNAVAILABLE, source = ProviderSource.SAVED))
                return null
            }
            val key = secrets.read(config.baseUrl)
            val issue = when (key) {
                is SecretRead.Available -> KeyIssue.NONE
                SecretRead.Missing -> KeyIssue.MISSING
                SecretRead.Invalidated -> KeyIssue.INVALIDATED
                SecretRead.Unavailable -> KeyIssue.UNAVAILABLE
            }
            publish(ProviderStatus(config, key is SecretRead.Available, issue, ProviderSource.SAVED))
            return (key as? SecretRead.Available)?.let { ProviderCredentials(config, it.value, mutable.value.revision) }
        }
        if (preferences[DISABLED] == true) { publish(ProviderStatus()); return null }
        val base = ProviderUrl.normalize(defaults.gateway.baseUrl)
        val config = base?.let { ProviderConfig(baseUrl = it, model = defaults.defaultModelId) }
        val ready = config?.fieldsValid() == true && EncryptedFileSecretStore.validKey(defaults.gateway.apiKey)
        publish(ProviderStatus(config, ready, if (ready) KeyIssue.NONE else KeyIssue.MISSING, if (config == null) ProviderSource.NONE else ProviderSource.DEBUG))
        return if (ready) ProviderCredentials(config, defaults.gateway.apiKey, mutable.value.revision) else null
    }

    override suspend fun save(config: ProviderConfig, key: String?) = withContext(Dispatchers.IO) { mutex.withLock {
        val base = ProviderUrl.normalize(config.baseUrl) ?: throw ProviderException(ProviderProblem.INVALID_CONFIG)
        val normalized = config.copy(name = config.name.trim(), baseUrl = base, model = config.model.trim())
        if (!normalized.fieldsValid()) throw ProviderException(ProviderProblem.INVALID_CONFIG)
        val existing = load()
        val value = key?.takeIf { it.isNotBlank() } ?: existing?.takeIf { it.config.baseUrl == base }?.apiKey
            ?: throw ProviderException(ProviderProblem.KEY_REQUIRED)
        if (!EncryptedFileSecretStore.validKey(value)) throw ProviderException(ProviderProblem.KEY_REQUIRED)
        revision++
        try {
            secrets.write(base, value)
            dataStore.edit { it[CONFIG] = Json.encodeToString(normalized); it[DISABLED] = false }
            publish(ProviderStatus(normalized, true, KeyIssue.NONE, ProviderSource.SAVED))
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            publish(ProviderStatus(normalized, false, KeyIssue.UNAVAILABLE, ProviderSource.SAVED))
            throw ProviderException(ProviderProblem.STORAGE_UNAVAILABLE)
        }
    } }

    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        revision++
        // Disable debug fallback first; a key-store deletion failure must never reactivate .env.
        dataStore.edit { it.remove(CONFIG); it[DISABLED] = true }
        publish(ProviderStatus())
        secrets.clear()
    } }

    private fun publish(status: ProviderStatus) {
        if (mutable.value.copy(revision = 0) != status.copy(revision = 0)) revision++
        mutable.value = status.copy(revision = revision)
    }

    private companion object {
        val CONFIG = stringPreferencesKey("provider_config_v1")
        val DISABLED = booleanPreferencesKey("provider_disabled_v1")
    }
}
