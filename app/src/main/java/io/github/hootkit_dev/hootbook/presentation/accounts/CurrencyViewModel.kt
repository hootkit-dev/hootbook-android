package io.github.hootkit_dev.hootbook.presentation.accounts

import io.github.hootkit_dev.hootbook.R

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import io.github.hootkit_dev.hootbook.data.currency.ExchangeRateProvider
import io.github.hootkit_dev.hootbook.data.currency.RateServerChoice
import io.github.hootkit_dev.hootbook.data.currency.RateSyncState
import io.github.hootkit_dev.hootbook.data.currency.RateSyncStatus
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.currency.model.CurrencyConversion
import io.github.hootkit_dev.hootbook.data.currency.model.CurrencySymbols
import io.github.hootkit_dev.hootbook.data.model.Currency
import io.github.hootkit_dev.hootbook.data.repository.CurrencyRepository
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val exchangeRateProvider: ExchangeRateProvider,
    private val currencyConversionService: CurrencyConversionService,
    private val currencyRepository: CurrencyRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val rateSyncState: RateSyncState,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** The servers syncs use, and how the last sync went. */
    val serverChoice: StateFlow<RateServerChoice> = rateSyncState.choice
    val lastSync: StateFlow<RateSyncStatus?> = rateSyncState.lastSync

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _uiState = MutableStateFlow(CurrencyUiState())
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        checkConnectivity()
        monitorNetworkConnectivity()
        loadCurrencies()
        observeBaseCurrency()
        viewModelScope.launch {
            accountBalanceRepository.getAllLatestBalances().collect { accounts ->
                _uiState.update { it.copy(accountCurrencies = accounts.map { a -> a.currency.uppercase() }.toSet()) }
            }
        }
    }

    private fun observeBaseCurrency() {
        viewModelScope.launch {
            currencyRepository.effectiveBaseCurrencyCode.collectLatest { effectiveCurrency ->
                val currencies = _uiState.value.currencies
                if (currencies.isNotEmpty()) {
                    val selectedCurrency = currencies.find { it.code.equals(effectiveCurrency, ignoreCase = true) }
                    if (selectedCurrency != null && _uiState.value.selectedCurrency?.code != selectedCurrency.code) {
                        _uiState.update { it.copy(selectedCurrency = selectedCurrency) }
                        loadConversions(selectedCurrency.code)
                    }
                }
            }
        }
    }

    private fun checkConnectivity() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isConnected.value = networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun monitorNetworkConnectivity() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
                loadCurrencies()
            }

            override fun onLost(network: Network) {
                _isConnected.value = false
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun loadCurrencies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val currencyMap = exchangeRateProvider.fetchAllCurrencies()
            if (currencyMap != null) {
                val customCurrencies = userPreferencesRepository.customCurrencies.first().map { it.toCurrency() }
                
                val currenciesMap = currencyMap.map { (code, name) ->
                    Currency(
                        code = code.uppercase(),
                        name = name,
                        symbol = CurrencySymbols.getSymbol(code)
                    )
                }.toMutableList()

                customCurrencies.forEach { custom ->
                    currenciesMap.removeAll { it.code == custom.code }
                    currenciesMap.add(custom)
                }

                val currencies = currenciesMap.sortedBy { it.name }

                val effectiveCurrencyCode = currencyRepository.effectiveBaseCurrencyCode.first()
                val selectedCurrency = currencies.find { it.code.equals(effectiveCurrencyCode, ignoreCase = true) } 
                    ?: currencies.find { it.code == "USD" }
                    ?: currencies.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currencies = currencies,
                        selectedCurrency = selectedCurrency,
                        isOfflineMode = !_isConnected.value
                    )
                }

                selectedCurrency?.let { loadConversions(it.code) }
            } else {
                // Fallback to supported currencies if API fails
                // The currency names come from the network; the rates do not need them, so the
                // base currency is still chosen and its stored rates shown.
                val effectiveCurrencyCode = currencyRepository.effectiveBaseCurrencyCode.first()
                val currencies = Currency.SUPPORTED_CURRENCIES
                val selectedCurrency = currencies.find { it.code.equals(effectiveCurrencyCode, ignoreCase = true) }
                    ?: Currency(code = effectiveCurrencyCode, name = effectiveCurrencyCode,
                        symbol = CurrencySymbols.getSymbol(effectiveCurrencyCode))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currencies = currencies,
                        selectedCurrency = it.selectedCurrency ?: selectedCurrency,
                        error = context.getString(R.string.msg_currencies_fallback),
                        isOfflineMode = !_isConnected.value
                    )
                }
                loadConversions((_uiState.value.selectedCurrency ?: selectedCurrency).code)
            }
        }
    }

    /**
     * Shows the stored rates for [currencyCode] at once, then syncs them when online. The list
     * stays visible while syncing; the status card says how it went.
     */
    fun loadConversions(currencyCode: String) {
        viewModelScope.launch {
            showStoredConversions(currencyCode)
            if (_isConnected.value) sync(currencyCode)
        }
    }

    /** The user's "Sync now". */
    fun syncNow() {
        val base = _uiState.value.selectedCurrency?.code ?: return
        viewModelScope.launch { sync(base) }
    }

    /** Pins the servers a sync uses and syncs with them. */
    fun selectServer(choice: RateServerChoice) {
        rateSyncState.setChoice(choice)
        syncNow()
    }

    private suspend fun sync(base: String) {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true) }
        try {
            currencyConversionService.syncNow(base)
        } finally {
            _uiState.update { it.copy(isSyncing = false) }
        }
        showStoredConversions(base)
    }

    private suspend fun showStoredConversions(base: String) {
        val customSymbols = userPreferencesRepository.customCurrencies.first().associate { it.code to it.symbol }
        val (rates, lastUpdated) = currencyConversionService.getStoredConversions(base)
        val conversions = rates.map { entity ->
            CurrencyConversion(
                currencyCode = entity.toCurrency,
                rate = entity.rate.toDouble(),
                lastUpdated = entity.updatedAtUnix * 1000,
                isCustom = entity.isCustom,
                customSymbol = customSymbols[entity.toCurrency]
            )
        }.sortedBy { it.currencyCode }
        _uiState.update {
            it.copy(
                conversions = conversions,
                lastUpdated = lastUpdated,
                isLoadingConversions = false,
                isOfflineMode = !_isConnected.value,
                conversionError = null
            )
        }
    }

    fun addCustomCurrency(name: String, symbol: String, code: String, rate: Double) {
        viewModelScope.launch {
            val customCurrency = io.github.hootkit_dev.hootbook.data.model.CustomCurrency(code.uppercase(), name, symbol)
            userPreferencesRepository.addCustomCurrency(customCurrency)
            
            val baseCurrency = _uiState.value.selectedCurrency?.code ?: "USD"
            currencyConversionService.saveCustomRate(baseCurrency, code.uppercase(), BigDecimal.valueOf(rate))
            
            loadCurrencies()
            loadConversions(baseCurrency)
        }
    }

    fun saveCustomRate(fromCurrency: String, toCurrency: String, rate: Double) {
        viewModelScope.launch {
            currencyConversionService.saveCustomRate(fromCurrency, toCurrency, BigDecimal.valueOf(rate))
            loadConversions(fromCurrency)
        }
    }

    fun resetCustomRate(fromCurrency: String, toCurrency: String) {
        viewModelScope.launch {
            currencyConversionService.resetCustomRate(fromCurrency, toCurrency)
            loadConversions(fromCurrency)
        }
    }

    fun selectCurrency(currencyCode: String) {
        val currency = _uiState.value.currencies.find { it.code.equals(currencyCode, ignoreCase = true) }
        if (currency != null) {
            _uiState.update { it.copy(selectedCurrency = currency) }
            loadConversions(currency.code)
        }
    }

    data class CurrencyUiState(
        val isLoading: Boolean = false,
        // A rate sync is running
        val isSyncing: Boolean = false,
        // Currencies the accounts are kept in, listed first
        val accountCurrencies: Set<String> = emptySet(),
        val currencies: List<Currency> = emptyList(),
        val selectedCurrency: Currency? = null,
        val error: String? = null,
        val isLoadingConversions: Boolean = false,
        val conversions: List<CurrencyConversion> = emptyList(),
        val conversionError: String? = null,
        val isOfflineMode: Boolean = false,
        val lastUpdated: Long = 0L
    )
}
