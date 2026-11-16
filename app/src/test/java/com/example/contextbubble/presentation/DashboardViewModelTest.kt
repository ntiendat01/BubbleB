package com.example.contextbubble.presentation

import com.example.contextbubble.domain.Clock
import com.example.contextbubble.domain.ConsentPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettingsRepository()
    private lateinit var vm: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = DashboardViewModel(settings, Clock { 42L })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initially_noConsent_settingsNotOffered() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        assertFalse(vm.uiState.value.consentAccepted)
        assertFalse(vm.uiState.value.canOpenAccessibilitySettings)
    }

    @Test
    fun acceptConsent_storesOnlyVersionAndTimestamp() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.acceptConsent()
        val stored = settings.state.value
        assertEquals(ConsentPolicy.CURRENT_VERSION, stored.consentVersion)
        assertEquals(42L, stored.consentAcceptedAtMs)
        assertTrue(vm.uiState.value.canOpenAccessibilitySettings)
    }

    @Test
    fun revokeConsent_clearsState() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.acceptConsent()
        vm.revokeConsent()
        assertFalse(vm.uiState.value.consentAccepted)
        assertNull(vm.uiState.value.consentVersion)
    }
}
