package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.brokerage.BrokerageRepository
import io.github.hootkit_dev.hootbook.domain.brokerage.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.category
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.AccountCategory
import kotlinx.coroutines.launch

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    private val repository: BrokerageRepository,
    accountRepository: io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
) : ViewModel() {
    val manualAccounts = accountRepository.getAllLatestBalances()
        .map { accounts -> accounts.filter { it.category() == AccountCategory.INVESTMENTS } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private var operation: Job? = null
    val connections = repository.connections
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<BrokerageError?>(null)
    val error = _error.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    init { reload() }
    fun reload() = run { repository.load(); _loaded.value = true }
    /** The registered providers, listed under "Broker auto-sync". */
    val providers: List<BrokerageProvider> = repository.availableProviders

    fun connect(providerId: String, label: String, fields: Map<String, String>, onSuccess: () -> Unit) = run {
        repository.connect(providerId, label, BrokerCredentials(fields))
        onSuccess()
    }
    fun refresh(id: String) = run { repository.refresh(id) }
    fun refreshAll() = run {
        val ids = repository.connections.value.map { it.id }
        if (ids.isEmpty()) repository.load() else ids.forEach { repository.refresh(it) }
    }
    fun disconnect(id: String) = run { repository.disconnect(id) }
    fun cancelConnection() { operation?.cancel() }
    fun clearError() { _error.value = null }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: BrokerageException) { _error.value = e.error }
            catch (_: Exception) { _error.value = BrokerageError.STORAGE }
            finally { _busy.value = false }
        }
    }
}
