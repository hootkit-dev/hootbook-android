package io.github.hootkit_dev.hootbook.presentation.accounts

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CurrencyUiState(
    val base: String = "CNY", val loading: Boolean = true, val saving: Boolean = false,
    val customCurrencies: List<CustomCurrency> = emptyList(),
    val rates: Map<String, ResolvedRate?> = emptyMap(),
    val storedRates: List<ExchangeRate> = emptyList(),
    val accountCurrencies: Set<String> = emptySet(),
    val provider: RateProvider? = null, val outcome: RefreshOutcome? = null,
    val error: Int? = null, val commandRevision: Long = 0,
    val converted: BigDecimal? = null, val conversionRate: ResolvedRate? = null,
    val converting: Boolean = false, val refreshing: Boolean = false,
)

/** Original currency-picker boundary, with Core records for rate resolution and conversion. */
@HiltViewModel
class CurrencyViewModel @Inject constructor(private val session: CoreSession, private val changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(CurrencyUiState())
    val uiState = mutable.asStateFlow()
    private var query: Job? = null
    private var generation = 0L
    private var conversion: Job? = null
    private var conversionGeneration = 0L
    fun selectCurrency(base: String) {
        if (query != null && mutable.value.base == base) return
        val mine = ++generation; query?.cancel()
        mutable.update { it.copy(base = base, loading = true) }
        query = viewModelScope.launch {
            session.observe(changes, setOf(Table.EXCHANGE_RATES, Table.SETTINGS, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES)) { core ->
                coreQuery {
                    val accounts = core.accounts().use { it.overview().accounts.flatMap { it.pockets }.map { it.currency }.toSet() }
                    core.currency().use { api ->
                        val custom = api.customCurrencies()
                        val stored = api.rates()
                        val codes = (java.util.Currency.getAvailableCurrencies().map { it.currencyCode } + custom.map { it.code } + stored.flatMap { listOf(it.from, it.to) } + accounts + base).distinct().sorted()
                        CurrencyUiState(base = base, loading = false, customCurrencies = custom,
                            storedRates = stored, accountCurrencies = accounts, provider = api.provider(),
                            rates = codes.associateWith { api.rate(base, it) })
                    }
                }
            }.collect { result ->
                if (mine != generation) return@collect
                result.fold(onSuccess = { snapshot -> mutable.update { it.copy(loading = false,
                    customCurrencies = snapshot.customCurrencies, storedRates = snapshot.storedRates,
                    accountCurrencies = snapshot.accountCurrencies, provider = snapshot.provider, rates = snapshot.rates) } },
                    onFailure = { failure -> mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } })
            }
        }
    }
    fun addCustomCurrency(value: CustomCurrency, rate: BigDecimal, onSaved: () -> Unit) {
        val base = uiState.value.base
        command(onSaved) { it.setCustomCurrency(value); it.setCustomRate(base, value.code, rate) }
    }
    fun saveCustomRate(to: String, rate: BigDecimal) { val base = uiState.value.base; command { it.setCustomRate(base, to, rate) } }
    fun resetCustomRate(to: String) { val base = uiState.value.base; command { it.removeCustomRate(base, to) } }
    fun selectProvider(provider: RateProvider) = command { it.setProvider(provider) }
    fun syncNow() = command { api ->
        mutable.update { it.copy(refreshing = true) }
        try { val outcome = api.refresh(true); mutable.update { it.copy(outcome = outcome) } }
        finally { mutable.update { it.copy(refreshing = false) } }
    }
    fun convert(amount: String, from: String, to: String) {
        val mine = ++conversionGeneration; conversion?.cancel()
        mutable.update { it.copy(converted = null, conversionRate = null, converting = true) }
        conversion = viewModelScope.launch {
            try {
                val parsed = amount.toBigDecimalOrNull()
                val result = if (parsed == null) null else session.withCore { core -> core.currency().use { api -> api.convert(parsed, from, to) to api.rate(from, to) } }
                if (mine == conversionGeneration) mutable.update { it.copy(converted = result?.first, conversionRate = result?.second, converting = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (mine == conversionGeneration) mutable.update { it.copy(converting = false, error = coreFailureText(failure)) } }
        }
    }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (CurrencyInterface) -> Unit) {
        if (uiState.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                session.withCore { core -> core.currency().use { block(it) } }
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
