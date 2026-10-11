package io.github.hootkit_dev.hootbook.presentation.ui.features.home

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.data.ConvertedMoney

data class HomeUiState(
    val overview: AccountsOverview? = null,
    val summary: LedgerSummary? = null,
    val lastSummary: LedgerSummary? = null,
    val budgets: List<Budget> = emptyList(),
    val subscriptions: SubscriptionSummary? = null,
    val debts: DebtSummary? = null,
    val recent: List<Transaction> = emptyList(),
    val upcoming: List<Subscription> = emptyList(),
    val heatmap: Map<LocalDate, UInt> = emptyMap(),
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val subcategories: Map<String, Subcategory> = emptyMap(),
    val displayNetWorth: ConvertedMoney? = null,
    val recentConversions: Map<String, ConvertedMoney> = emptyMap(),
    val availableCurrencies: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: Int? = null,
    val displayTotals: Map<String, ConvertedMoney> = emptyMap(),
    val customSymbols: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
) {
    val displayCurrency: String get() = displayNetWorth?.currency ?: overview?.netWorth?.currency.orEmpty()
}
