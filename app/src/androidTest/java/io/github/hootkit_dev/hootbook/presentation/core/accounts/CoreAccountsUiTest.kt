package io.github.hootkit_dev.hootbook.presentation.core.accounts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import dev.hootkit.core.AccountsInterface
import dev.hootkit.core.AccountsOverview
import dev.hootkit.core.LocalDateTime
import dev.hootkit.core.Money
import dev.hootkit.core.Pocket
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Tests actual Core UI with generated records; native integration is covered by CoreSmokeTest. */
class CoreAccountsUiTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var model: CoreAccountsViewModel
    private val accountId = "1fe41c82-29de-4fac-9f59-c343a68e31a2"

    private fun render(fontScale: Float = 1f, onTransactions: (String) -> Unit = {}) {
        val amount = BigDecimal("-1234567.89")
        val account = Account(
            id = accountId, name = "Core wallet", last4 = null, kind = AccountKind.WALLET,
            mainCurrency = "CNY", pockets = listOf(Pocket("CNY", amount, null, null)),
            total = Money(amount, "CNY"), totalComplete = true, securities = null,
            broker = null, creditLimit = null, statementDay = null, dueDay = null,
            institutionKey = null, icon = "wallet", color = "#001122", archived = false,
            isDefault = false, sortOrder = 0,
            createdAt = LocalDateTime(2026, 10u, 10u, 0u, 0u, 0u, 0u),
        )
        val overview = AccountsOverview(listOf(account), Money(amount, "CNY"), true, null, null)
        val api = Proxy.newProxyInstance(AccountsInterface::class.java.classLoader,
            arrayOf(AccountsInterface::class.java)) { _, method, _ ->
            when (method.name.substringBefore('-')) {
                "overview" -> overview
                "anchors" -> emptyList<dev.hootkit.core.BalanceAnchor>()
                "transactionCount" -> 0u
                else -> error("Unexpected Core API: ${method.name}")
            }
        } as AccountsInterface
        rule.runOnUiThread {
            model = CoreAccountsViewModel(ChangeBus(), { api })
            store.put("core-accounts-ui", model)
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HootbookTheme(dynamicColor = false) {
                    CoreAccountsScreen(onBack = {}, onTransactions = onTransactions, viewModel = model)
                }
            }
        }
        rule.waitUntil(5_000) { !model.state.value.loading && model.state.value.overview != null }
        rule.waitForIdle()
        rule.runOnIdle { assertNull(model.state.value.error) }
    }

    @After fun clearModels() { rule.runOnUiThread { store.clear() } }

    @Test fun selectingAccountAndOpeningTransactionsKeepsCoreStringId() {
        var target: String? = null
        render(onTransactions = { target = it })
        rule.onNodeWithText("Core wallet").performClick()
        rule.waitUntil(5_000) { !model.state.value.loading && model.state.value.account != null }
        rule.runOnIdle { assertEquals(accountId, model.state.value.selectedId) }
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.core_accounts_view_transactions)
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(accountId, target) }
    }

    @Test fun largeFontRetainsNegativePocketBalanceWithoutOldBalanceEntity() {
        render(fontScale = 2f)
        rule.onNodeWithText("Core wallet").performClick()
        rule.waitUntil(5_000) { !model.state.value.loading && model.state.value.account != null }
        rule.onNodeWithText("-1234567.89").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(BigDecimal("-1234567.89"), model.state.value.account!!.pockets.single().balance)
            assertFalse(model.state.value.saving)
        }
    }
}
