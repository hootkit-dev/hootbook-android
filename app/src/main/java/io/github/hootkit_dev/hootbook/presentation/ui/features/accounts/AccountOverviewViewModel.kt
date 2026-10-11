package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountOverviewUiState(val overview: AccountsOverview? = null, val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class AccountOverviewViewModel @Inject constructor(session: CoreSession, changes: ChangeBus) : ViewModel() {
    private val mutable = MutableStateFlow(AccountOverviewUiState())
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            session.observe(changes, accountTables) { core -> coreQuery { core.accounts().use { it.overview() } } }
                .collect { result -> result.fold(
                    onSuccess = { overview -> mutable.value = AccountOverviewUiState(overview, false) },
                    onFailure = { failure -> mutable.update { it.copy(loading = false, error = failure.message) } },
                ) }
        }
    }
}
