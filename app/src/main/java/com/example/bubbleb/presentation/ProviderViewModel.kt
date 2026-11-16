package com.example.bubbleb.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bubbleb.domain.llm.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Key draft is memory-only and is never put in SavedStateHandle or rememberSaveable. */
@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val gateway: LlmGateway,
    private val counter: UsageCounter,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProviderForm())
    val form = mutable.asStateFlow()
    val status = providers.status
    val usage = counter.totals
    private var testJob: Job? = null
    private var testId = 0L

    init {
        viewModelScope.launch {
            try { providers.refresh(); mutable.value = ProviderForm.from(status.value.config) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = ProviderForm(loading = false, notice = ProviderNotice.STORAGE) }
        }
    }

    fun edit(transform: (ProviderForm) -> ProviderForm) {
        if (mutable.value.loading || mutable.value.saving) return
        cancelTest()
        mutable.value = transform(mutable.value).copy(notice = null)
    }

    fun save() {
        val current = mutable.value
        if (current.loading || current.saving) return
        val config = current.config()
        if (config == null) { mutable.value = current.copy(notice = ProviderNotice.INVALID_CONFIG); return }
        cancelTest()
        mutable.value = current.copy(saving = true, testing = false, notice = null)
        viewModelScope.launch {
            try {
                providers.save(config, current.key.takeIf { it.isNotBlank() })
                mutable.value = ProviderForm.from(status.value.config).copy(notice = ProviderNotice.SAVED)
            } catch (e: CancellationException) { throw e }
            catch (e: ProviderException) {
                mutable.value = mutable.value.copy(key = "", saving = false, notice = when (e.problem) {
                    ProviderProblem.INVALID_CONFIG -> ProviderNotice.INVALID_CONFIG
                    ProviderProblem.KEY_REQUIRED -> ProviderNotice.KEY_REQUIRED
                    ProviderProblem.STORAGE_UNAVAILABLE -> ProviderNotice.STORAGE
                })
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(key = "", saving = false, notice = ProviderNotice.STORAGE)
            }
        }
    }

    fun remove() {
        if (mutable.value.loading || mutable.value.saving) return
        cancelTest()
        mutable.value = mutable.value.copy(saving = true, key = "")
        viewModelScope.launch {
            try { providers.clear(); mutable.value = ProviderForm(loading = false, notice = ProviderNotice.REMOVED) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(saving = false, notice = ProviderNotice.STORAGE) }
        }
    }

    fun testConnection() {
        val current = mutable.value
        if (current.testing || current.saving || !status.value.ready || current.key.isNotEmpty() || current.config() != status.value.config) return
        val id = ++testId
        mutable.value = current.copy(testing = true, notice = null)
        testJob = viewModelScope.launch {
            var completed = false
            var failed = false
            try {
                val credentials = providers.resolve() ?: throw ProviderException(ProviderProblem.KEY_REQUIRED)
                if (credentials.config != current.config() || id != testId) return@launch
                counter.started()
                gateway.generate(credentials, LlmRequest("Reply exactly OK.", "Synthetic connection test. No screen data.", false))
                    .collect { event ->
                        if (event is LlmEvent.Complete) { completed = true; counter.completed(event.usage) }
                        if (event is LlmEvent.Failed) failed = true
                    }
                if (id == testId) mutable.value = mutable.value.copy(notice = if (completed && !failed) ProviderNotice.TEST_OK else ProviderNotice.TEST_FAILED)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (id == testId) mutable.value = mutable.value.copy(notice = ProviderNotice.TEST_FAILED) }
            finally { if (id == testId) mutable.value = mutable.value.copy(testing = false) }
        }
    }

    fun cancelTest() {
        testId++
        testJob?.cancel()
        testJob = null
        mutable.value = mutable.value.copy(testing = false)
    }

    fun clearDraft() { cancelTest(); mutable.value = mutable.value.copy(key = "") }
}
