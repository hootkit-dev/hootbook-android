package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Restored account layout exercises generated records and opaque IDs without native data mutation. */
class AccountsUiTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val id = "opaque/中文?&=account"
    private val amount = BigDecimal("-1234567.89")
    private fun render(fontScale: Float = 1f, onAccount: (String) -> Unit = {}) {
        val account = Account(id, "Core wallet", null, AccountKind.WALLET, "CNY",
            listOf(Pocket("CNY", amount, null, null, null)), Money(amount, "CNY"), false,
            null, null, null, null, null, null, "type_finance_bank", "#001122", false, false, 0,
            LocalDateTime(2026, 10u, 11u, 12u, 0u, 0u, 0u))
        val core = object : HootKitCore(NoPointer) {
            override fun accounts() = object : Accounts(NoPointer) {
                override suspend fun overview() = AccountsOverview(listOf(account), Money(amount, "CNY"), false, null, null)
                override suspend fun netWorthHistory(from: LocalDate, to: LocalDate, step: HistoryStep) = NetWorthHistory("CNY", emptyList(), emptyList(), null, null)
            }
            override fun brokerage() = object : Brokerage(NoPointer) { override suspend fun connections() = emptyList<BrokerageConnection>() }
            override fun settings() = object : Settings(NoPointer) { override suspend fun mainCurrency() = "CNY" }
            override fun catalog() = object : Catalog(NoPointer) { override fun all() = emptyList<Institution>() }
            override fun currency() = object : Currency(NoPointer) { override suspend fun customCurrencies() = emptyList<CustomCurrency>() }
        }
        lateinit var vm: ManageAccountsViewModel
        rule.runOnUiThread {
            vm = ManageAccountsViewModel(CoreSession(scope) { core }, ChangeBus())
            store.put("manage", vm)
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HootbookTheme(dynamicColor = false) {
                    ManageAccountsScreen(onNavigateBack = {}, onNavigateToAccountDetail = onAccount,
                        onNavigateToAddAccount = {}, onBrokerage = { _, _ -> }, category = AccountCategory.WALLETS, manageAccountsViewModel = vm)
                }
            }
        }
        rule.waitUntil(5000) { !vm.uiState.value.isLoading }
    }
    @After fun cleanup() { rule.runOnUiThread { store.clear(); scope.cancel() } }
    @Test fun accountClickUsesCoreStringId() {
        var opened: String? = null
        render(onAccount = { opened = it })
        rule.onNodeWithText("Core wallet").performClick()
        rule.runOnIdle { assertEquals(id, opened) }
    }
    @Test fun negativeBalanceAndIncompleteIndicatorRemainVisibleAtLargeFont() {
        render(fontScale = 2f)
        rule.onNodeWithText(CoreMoneyPresentation.formatCurrency(amount, "CNY"), substring = true).assertIsDisplayed()
        rule.onNodeWithText("Core wallet").assertIsDisplayed()
        rule.onNodeWithText(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(io.github.hootkit_dev.hootbook.R.string.core_complete_incomplete)).assertIsDisplayed()
    }
}
