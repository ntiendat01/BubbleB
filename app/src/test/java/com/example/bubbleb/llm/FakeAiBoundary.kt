package com.example.bubbleb.llm

import com.example.bubbleb.domain.llm.*
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAiProviders : ProviderRepository {
    override val status = MutableStateFlow(ProviderStatus(ProviderConfig(), true, KeyIssue.NONE, ProviderSource.SAVED, 1))
    var resolves = 0
    override suspend fun refresh() = Unit
    override suspend fun resolve(): ProviderCredentials? {
        resolves++
        return status.value.takeIf { it.ready }?.let { ProviderCredentials(it.config!!, "synthetic" + "-value", it.revision) }
    }
    override suspend fun save(config: ProviderConfig, key: String?) {
        status.value = status.value.copy(config = config, ready = true, revision = status.value.revision + 1)
    }
    override suspend fun clear() { status.value = ProviderStatus() }
}
