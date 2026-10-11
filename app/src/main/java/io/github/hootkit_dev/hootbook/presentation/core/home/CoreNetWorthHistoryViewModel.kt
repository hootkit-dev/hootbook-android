package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.Table
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal val NET_WORTH_HISTORY_TABLES = setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES,
    Table.BALANCE_ANCHORS, Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE)

data class CoreNetWorthHistoryUiState(
    val range: CoreHistoryRange = CoreHistoryRange.HALF_YEAR,
    val data: CoreNetWorthHistoryResult? = null,
    val refreshing: Boolean = true,
    val failed: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CoreNetWorthHistoryViewModel private constructor(
    private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore?,
    private val displayOverride: StateFlow<String?>?,
    private val query: suspend (LocalDate, CoreHistoryRange, String?) -> CoreNetWorthHistoryResult,
    private val today: () -> LocalDate,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, preferences: CoreUiPreferencesStore) :
        this(changes, preferences, null, { date, range, currency ->
            CoreNetWorthHistoryQuery(session.awaitCore()).load(date, range, currency)
        }, LocalDate::now)

    internal constructor(changes: ChangeBus, display: StateFlow<String?> = MutableStateFlow(null),
        today: () -> LocalDate = LocalDate::now,
        query: suspend (LocalDate, CoreHistoryRange, String?) -> CoreNetWorthHistoryResult) :
        this(changes, null, display, query, today)

    private data class Request(val range: CoreHistoryRange, val date: LocalDate, val retry: Long = 0)
    private data class Batch(val revisions: Map<Table, Long>, val display: String?, val request: Request)
    private val request = MutableStateFlow(Request(CoreHistoryRange.HALF_YEAR, today()))
    private val mutable = MutableStateFlow(CoreNetWorthHistoryUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try { preferences?.ready() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(refreshing = false, failed = true) }; return@launch }
            val display = displayOverride ?: preferences!!.state.map {
                it.unifiedDisplayCurrency?.takeIf { _ -> it.unifiedDisplayEnabled }
            }.distinctUntilChanged()
            // Coalesce transaction/sync bursts. No loop or repeated native fan-out on stable data.
            combine(changes.observe(NET_WORTH_HISTORY_TABLES), display, request, ::Batch)
                .debounce(75).collectLatest { batch ->
                    mutable.update { it.copy(range = batch.request.range, refreshing = true, failed = false) }
                    try {
                        val result = query(batch.request.date, batch.request.range, batch.display)
                        if (isCurrent(batch)) mutable.update { it.copy(data = result, refreshing = false, failed = false) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (isCurrent(batch)) mutable.update { it.copy(refreshing = false, failed = true) }
                    }
                }
        }
    }

    fun onResume() { request.update { it.copy(date = today()) } }
    fun chooseRange(range: CoreHistoryRange) { request.update { it.copy(range = range, date = today()) } }
    fun retry() { request.update { it.copy(date = today(), retry = it.retry + 1) } }

    private fun isCurrent(batch: Batch): Boolean {
        val currentDisplay = displayOverride?.value ?: preferences?.state?.value?.let {
            it.unifiedDisplayCurrency?.takeIf { _ -> it.unifiedDisplayEnabled }
        }
        return request.value == batch.request && currentDisplay == batch.display &&
            NET_WORTH_HISTORY_TABLES.all { changes.revisions.value[it] == batch.revisions[it] }
    }
}
