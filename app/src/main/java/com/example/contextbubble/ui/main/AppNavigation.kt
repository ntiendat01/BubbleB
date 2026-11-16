package com.example.contextbubble.ui.main

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.contextbubble.presentation.DashboardViewModel
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute : NavKey

@Serializable
data object ProviderSettingsRoute : NavKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(DashboardRoute)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<DashboardRoute> {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = viewModel,
                    onOpenProviderSettings = { backStack.add(ProviderSettingsRoute) },
                )
            }
            entry<ProviderSettingsRoute> {
                ProviderSettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
