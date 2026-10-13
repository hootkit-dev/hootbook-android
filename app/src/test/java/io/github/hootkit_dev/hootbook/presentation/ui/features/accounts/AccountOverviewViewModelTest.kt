package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountOverviewViewModelTest : CoreViewModelTest() {
    @Test fun overviewIsCoreResultAndChangeRefreshRecoversAfterFailure() = runTest {
        val core = FakeAccountsCore(); val changes = ChangeBus()
        val vm = keep(AccountOverviewViewModel(CoreSession(backgroundScope) { core }, changes))
        awaitState { !vm.uiState.value.loading }
        assertSame(core.overview, vm.uiState.value.overview)
        assertFalse(vm.uiState.value.overview!!.netWorthComplete)
        core.onOverview = { error("offline") }; changes.changed(Table.EXCHANGE_RATES)
        awaitState { vm.uiState.value.error == io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed }
        core.overview = core.overview.copy(netWorth = Money(BigDecimal("999"), "GBP"))
        core.onOverview = { core.overview }; changes.changed(Table.BROKERAGE)
        awaitState { vm.uiState.value.overview?.netWorth?.amount == BigDecimal("999") }
        assertNull(vm.uiState.value.error)
        assertEquals(core.opened.get(), core.closed.get())
    }
}
