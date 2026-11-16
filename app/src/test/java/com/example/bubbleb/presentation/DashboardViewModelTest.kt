package com.example.bubbleb.presentation

import com.example.bubbleb.domain.Clock
import com.example.bubbleb.domain.ConsentPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val status = FakeAccessibilityStatus()
    private val coordinator = BubbleCoordinator(settings, FakeAppProfileRepository(), "own")
    private lateinit var vm: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = DashboardViewModel(settings, status, coordinator, Clock { 42L })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun acceptConsent_storesOnlyVersionAndTimestamp() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.acceptConsent()
        val stored = settings.state.value
        assertEquals(ConsentPolicy.CURRENT_VERSION, stored.consentVersion)
        assertEquals(42L, stored.consentAcceptedAtMs)
        assertTrue(vm.uiState.value.consentAccepted)
    }

    @Test
    fun enablingBubble_ignoredUntilServiceConnected() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        settings.setBubbleEnabled(false)
        vm.acceptConsent()
        vm.setBubbleEnabled(true)
        assertFalse(settings.state.value.bubbleEnabled)

        status.enabled.value = true
        coordinator.onServiceConnected()
        assertEquals(ServiceStatus.BUBBLE_OFF, vm.uiState.first { it.canToggleBubble }.status)
        vm.setBubbleEnabled(true)
        assertTrue(settings.state.value.bubbleEnabled)
        assertEquals(ServiceStatus.CONNECTED, vm.uiState.value.status)
    }

    @Test
    fun disablingBubble_alwaysAllowed() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setBubbleEnabled(false)
        assertFalse(settings.state.value.bubbleEnabled)
    }
}
