package io.github.hootkit_dev.hootbook.presentation.accounts

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.FakeAccountsCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyViewModelTest : CoreViewModelTest() {
    private class Fake : FakeAccountsCore() {
        var custom = emptyList<CustomCurrency>()
        var selectedProvider = RateProvider.AUTO
        var onRate: suspend (String, String) -> ResolvedRate? = { from, to -> ResolvedRate(from, to, BigDecimal("5"), RateKind.DIRECT, false, false, null) }
        var onConvert: suspend (BigDecimal) -> BigDecimal? = { BigDecimal("543.21") }
        var onCurrencyCommand: suspend () -> Unit = {}
        var customRateCalls = 0
        var refreshCalls = 0
        override fun currency(): Currency {
            opened.incrementAndGet()
            return object : Currency(NoPointer) {
                override suspend fun customCurrencies() = custom
                override suspend fun rates() = emptyList<ExchangeRate>()
                override suspend fun rate(from: String, to: String) = onRate(from, to)
                override suspend fun provider() = selectedProvider
                override suspend fun convert(amount: BigDecimal, from: String, to: String) = onConvert(amount)
                override suspend fun setCustomCurrency(currency: CustomCurrency) { onCurrencyCommand(); custom = listOf(currency) }
                override suspend fun setCustomRate(base: String, currency: String, rate: BigDecimal) { customRateCalls++; onCurrencyCommand() }
                override suspend fun setProvider(provider: RateProvider) { onCurrencyCommand(); selectedProvider = provider }
                override suspend fun refresh(force: Boolean): RefreshOutcome { refreshCalls++; onCurrencyCommand(); return RefreshOutcome.NotNeeded }
                override fun destroy() { closed.incrementAndGet(); super.destroy() }
            }
        }
    }
    @Test fun pickerLoadsNativeRatesAndRefreshesWithLatestBase() = runTest {
        val core = Fake(); val changes = ChangeBus()
        val vm = keep(CurrencyViewModel(CoreSession(backgroundScope) { core }, changes))
        vm.selectCurrency("GBP"); awaitState { !vm.uiState.value.loading }
        assertEquals(BigDecimal("5"), vm.uiState.value.rates["USD"]?.rate)
        vm.selectCurrency("USD"); awaitState { !vm.uiState.value.loading && vm.uiState.value.rates["GBP"]?.from == "USD" }
        core.custom = listOf(CustomCurrency("TEST", "Test", "T")); changes.changed(Table.SETTINGS)
        awaitState { vm.uiState.value.customCurrencies == core.custom }
        assertEquals("USD", vm.uiState.value.base)
        assertEquals(core.opened.get(), core.closed.get())
    }
    @Test fun conversionComesFromCoreAndLatestInputsWin() = runTest {
        val core = Fake(); val gate = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        core.onConvert = { amount -> if (amount == BigDecimal.ONE) withContext(NonCancellable) { started.complete(Unit); gate.await() }; if (amount == BigDecimal.ONE) BigDecimal("111") else BigDecimal("222") }
        val vm = keep(CurrencyViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.convert("1", "GBP", "USD"); awaitState { started.isCompleted }
        vm.convert("2", "GBP", "USD"); awaitState { vm.uiState.value.converted == BigDecimal("222") }
        gate.complete(Unit); awaitState { core.opened.get() == core.closed.get() }
        assertEquals(BigDecimal("222"), vm.uiState.value.converted)
    }
    @Test fun rateCommandsGuardDuplicateSubmissionAndKeepFailures() = runTest {
        val core = Fake(); val gate = CompletableDeferred<Unit>()
        core.onCurrencyCommand = { gate.await(); error("rate error") }
        val vm = keep(CurrencyViewModel(CoreSession(backgroundScope) { core }, ChangeBus()))
        vm.saveCustomRate("USD", BigDecimal("3")); vm.saveCustomRate("USD", BigDecimal("3"))
        awaitState { core.customRateCalls == 1 }; assertTrue(vm.uiState.value.saving)
        gate.complete(Unit); awaitState { !vm.uiState.value.saving }; assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.uiState.value.error)
        core.onCurrencyCommand = {}; vm.selectProvider(RateProvider.FRANKFURTER)
        awaitState { !vm.uiState.value.saving }; assertEquals(RateProvider.FRANKFURTER, core.selectedProvider)
        vm.syncNow(); vm.syncNow(); awaitState { !vm.uiState.value.saving }; assertEquals(1, core.refreshCalls)
        assertEquals(RefreshOutcome.NotNeeded, vm.uiState.value.outcome)
        assertEquals(core.opened.get(), core.closed.get())
    }
}
