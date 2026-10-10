package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AccountOverviewViewModel @Inject constructor(
    private val brokerage: BrokerageRepository,
    private val conversion: CurrencyConversionService
) : ViewModel() {
    private data class Input(val accounts: List<AccountBalanceEntity>, val currency: String)
    private val input = MutableStateFlow(Input(emptyList(), "CNY"))
    private val loadState = MutableStateFlow(0)
    internal val items = combine(input, brokerage.connections, loadState, conversion.rateChangeTrigger) { value, connections, loaded, _ ->
        buildOverview(value.accounts, connections, value.currency, loaded == 1, loaded == -1) { from, to ->
            conversion.getExchangeRate(from, to)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountCategory.entries.map { AccountOverviewItem(it) })

    init { viewModelScope.launch {
        try { brokerage.load(); loadState.value = 1 }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loadState.value = -1 }
    } }
    fun update(accounts: List<AccountBalanceEntity>, currency: String) { input.value = Input(accounts, currency) }
}
