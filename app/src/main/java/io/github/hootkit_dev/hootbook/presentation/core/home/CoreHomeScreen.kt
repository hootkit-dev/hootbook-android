package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.presentation.ui.features.home.HomeScreen

@Composable
fun CoreHomeScreen(
    onAccounts: () -> Unit, onCategories: () -> Unit, onTransactions: () -> Unit, onBudgets: () -> Unit,
    onSubscriptions: () -> Unit, onTemplates: () -> Unit, onDebts: () -> Unit, onSettings: () -> Unit,
    onCurrencies: () -> Unit, onBackup: () -> Unit,
    onProfile: () -> Unit = {}, onAppearance: () -> Unit = {}, onAnalytics: () -> Unit = {},
    onAccount: (String) -> Unit = { onAccounts() }, onBudget: (String) -> Unit = { onBudgets() },
    onTransaction: (String) -> Unit = { onTransactions() }, onSubscription: (String) -> Unit = { onSubscriptions() },
    onAi: () -> Unit = {}, onAccountCategory: (AccountKind) -> Unit = { onAccounts() },
    viewModel: CoreHomeViewModel = hiltViewModel(), platform: CoreHomePlatformViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by platform.preferences.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    HomeScreen(state, prefs, platform.assets, viewModel::refresh, onSettings, onAi, onAccounts,
        onAccountCategory, onCurrencies, onAnalytics, onTransactions, onTransaction, onBudgets,
        onBudget, onSubscriptions, onSubscription, onDebts, platform::setWidgetVisible,
        platform::reorderWidgets, platform::resetLayout, platform::toggleBanner)
}
