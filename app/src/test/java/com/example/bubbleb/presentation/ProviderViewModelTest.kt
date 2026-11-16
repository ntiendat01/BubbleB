package com.example.bubbleb.presentation

import com.example.bubbleb.domain.llm.*
import com.example.bubbleb.llm.FakeAiProviders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val providers = FakeAiProviders()
    private val counter = UsageCounter()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun openingSettingsAndSavingNeverTestConnectionAutomatically() = runTest(dispatcher) {
        var calls = 0
        val vm = ProviderViewModel(providers, LlmGateway { _, _ -> calls++; flowOf(LlmEvent.Complete(null)) }, counter)
        assertEquals(0, calls)
        vm.edit { it.copy(model = "custom/model", key = "synthetic" + "-value") }
        vm.save()
        assertEquals(0, calls)
        assertEquals("", vm.form.value.key)
        assertEquals("custom/model", providers.status.value.config!!.model)
    }

    @Test fun explicitConnectionTestUsesOnlySyntheticTextAndConfiguredModel() = runTest(dispatcher) {
        var sent: LlmRequest? = null
        var model: String? = null
        var maxTokens: Int? = null
        val vm = ProviderViewModel(providers, LlmGateway { credentials, request ->
            model = credentials.config.model; maxTokens = credentials.config.maxOutputTokens; sent = request
            flowOf(LlmEvent.Text("OK"), LlmEvent.Complete(TokenUsage(3, 1)))
        }, counter)
        vm.testConnection()
        assertEquals(ProviderPreset.MODEL, model)
        assertEquals(LlmLimits.DEFAULT_OUTPUT_TOKENS, maxTokens)
        assertEquals("Synthetic connection test. No screen data.", sent!!.untrustedText)
        assertFalse(sent!!.stream)
        assertEquals(ProviderNotice.TEST_OK, vm.form.value.notice)
        assertEquals(1, counter.totals.value.requests)
    }

    @Test fun unsavedEditsBlockTest_andLeavingScreenCancelsAndClearsKeyDraft() = runTest(dispatcher) {
        var calls = 0
        var cancelled = false
        val vm = ProviderViewModel(providers, LlmGateway { _, _ -> calls++; flow {
            try { awaitCancellation() } finally { cancelled = true }
        } }, counter)
        vm.edit { it.copy(model = "unsaved/model") }
        vm.testConnection()
        assertEquals(0, calls)
        vm.edit { it.copy(model = ProviderPreset.MODEL) }
        vm.testConnection()
        assertEquals(1, calls)
        vm.clearDraft()
        assertTrue(cancelled)
        assertFalse(vm.form.value.testing)
        vm.edit { it.copy(key = "synthetic" + "-draft") }
        assertFalse(vm.form.value.toString().contains("synthetic"))
        vm.clearDraft()
        assertEquals("", vm.form.value.key)
    }

    @Test fun connectionErrorsNeverExposeProviderText() = runTest(dispatcher) {
        val vm = ProviderViewModel(providers, LlmGateway { _, _ -> flow { error("PRIVATE_PROVIDER_ERROR") } }, counter)
        vm.testConnection()
        assertEquals(ProviderNotice.TEST_FAILED, vm.form.value.notice)
        assertFalse(vm.form.value.toString().contains("PRIVATE_PROVIDER"))
    }
}
