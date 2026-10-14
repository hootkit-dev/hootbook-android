package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import io.github.hootkit_dev.hootbook.presentation.common.TimePeriod

internal val accountTables = setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
    Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS, Table.BROKERAGE,
    Table.BROKERAGE_CONNECTIONS, Table.BROKERAGE_LINKS, Table.TAGS, Table.TRANSACTION_TAGS)

private data class NetWorthQueryConfig(
    val period: TimePeriod = TimePeriod.THIS_MONTH,
    val customRange: Pair<java.time.LocalDate, java.time.LocalDate>? = null,
    val step: HistoryStep = HistoryStep.DAILY,
)

data class ManageAccountsUiState(
    val overview: AccountsOverview? = null,
    val netWorthHistory: NetWorthHistory? = null,
    val netWorthPeriod: TimePeriod = TimePeriod.THIS_MONTH,
    val netWorthCustomRange: Pair<java.time.LocalDate, java.time.LocalDate>? = null,
    val netWorthStep: HistoryStep = HistoryStep.DAILY,
    val connections: List<BrokerageConnection> = emptyList(),
    val institutions: List<Institution> = emptyList(),
    val customCurrencies: List<CustomCurrency> = emptyList(),
    val defaultCurrency: String = "CNY",
    val isLoading: Boolean = true,
    val isSavingAccount: Boolean = false,
    val accountSaveError: Int? = null,
    val errorMessage: Int? = null,
    val commandRevision: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManageAccountsUiState())
    val uiState = mutable.asStateFlow()
    private val queryConfig = MutableStateFlow(NetWorthQueryConfig())
    init {
        viewModelScope.launch {
            queryConfig.flatMapLatest { cfg ->
                session.observe(changes, accountTables) { core ->
                    coreQuery {
                        val today = java.time.LocalDate.now()
                        val (rawFrom, rawTo) = when (cfg.period) {
                            TimePeriod.THIS_MONTH -> java.time.YearMonth.from(today).atDay(1) to today
                            TimePeriod.LAST_MONTH -> {
                                val lastMonth = java.time.YearMonth.from(today).minusMonths(1)
                                lastMonth.atDay(1) to lastMonth.atEndOfMonth()
                            }
                            TimePeriod.CURRENT_FY -> today.withDayOfYear(1) to today
                            TimePeriod.ALL -> today.minusDays(3650) to today
                            TimePeriod.CUSTOM -> cfg.customRange ?: (today.minusDays(30) to today)
                        }
                        val clampedTo = if (rawTo < rawFrom) rawFrom else rawTo
                        val clampedFrom = if (java.time.temporal.ChronoUnit.DAYS.between(rawFrom, clampedTo) > 3650) {
                            clampedTo.minusDays(3650)
                        } else {
                            rawFrom
                        }
                        val (overview, rawHistory) = core.accounts().use { api ->
                            api.overview() to api.netWorthHistory(
                                LocalDate(clampedFrom.year, clampedFrom.monthValue.toUByte(), clampedFrom.dayOfMonth.toUByte()),
                                LocalDate(clampedTo.year, clampedTo.monthValue.toUByte(), clampedTo.dayOfMonth.toUByte()),
                                cfg.step,
                            )
                        }
                        val history = if (cfg.period == TimePeriod.ALL && rawHistory.points.size > 2) {
                            val earliest = listOfNotNull(rawHistory.dataSince, rawHistory.securitiesSince)
                                .minByOrNull { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()) }
                                ?.let { java.time.LocalDate.of(it.year, it.month.toInt(), 1) }
                            if (earliest != null) {
                                val filtered = rawHistory.points.filter { pt ->
                                    val d = java.time.LocalDate.of(pt.date.year, pt.date.month.toInt(), pt.date.day.toInt())
                                    !d.isBefore(earliest)
                                }
                                rawHistory.copy(points = if (filtered.size >= 2) filtered else rawHistory.points.takeLast(2))
                            } else {
                                rawHistory
                            }
                        } else {
                            rawHistory
                        }
                        val institutions = core.catalog().use { it.all() }
                        val currency = core.settings().use { it.mainCurrency() }
                        val custom = core.currency().use { it.customCurrencies() }
                        val connections = core.brokerage().use { it.connections() }
                        ManageAccountsUiState(
                            overview = overview,
                            netWorthHistory = history,
                            netWorthPeriod = cfg.period,
                            netWorthCustomRange = cfg.customRange,
                            netWorthStep = cfg.step,
                            connections = connections,
                            institutions = institutions,
                            customCurrencies = custom,
                            defaultCurrency = currency,
                            isLoading = false,
                        )
                    }
                }
            }.collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        mutable.update {
                            it.copy(
                                overview = snapshot.overview,
                                netWorthHistory = snapshot.netWorthHistory,
                                netWorthPeriod = snapshot.netWorthPeriod,
                                netWorthCustomRange = snapshot.netWorthCustomRange,
                                netWorthStep = snapshot.netWorthStep,
                                connections = snapshot.connections,
                                institutions = snapshot.institutions,
                                customCurrencies = snapshot.customCurrencies,
                                defaultCurrency = snapshot.defaultCurrency,
                                isLoading = false,
                            )
                        }
                    },
                    onFailure = { failure -> mutable.update { it.copy(isLoading = false, errorMessage = coreFailureText(failure)) } },
                )
            }
        }
    }
    fun selectNetWorthPeriod(period: TimePeriod) {
        if (period == TimePeriod.CUSTOM && queryConfig.value.customRange == null) return
        val defaultStep = when (period) {
            TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> HistoryStep.DAILY
            TimePeriod.CURRENT_FY, TimePeriod.ALL -> HistoryStep.MONTHLY
            TimePeriod.CUSTOM -> {
                val range = queryConfig.value.customRange
                if (range != null && java.time.temporal.ChronoUnit.DAYS.between(range.first, range.second) > 62) {
                    HistoryStep.MONTHLY
                } else {
                    HistoryStep.DAILY
                }
            }
        }
        queryConfig.update { it.copy(period = period, step = defaultStep) }
    }
    fun setNetWorthCustomRange(start: java.time.LocalDate, end: java.time.LocalDate) {
        val (from, to) = if (start <= end) start to end else end to start
        val defaultStep = if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 62) {
            HistoryStep.MONTHLY
        } else {
            HistoryStep.DAILY
        }
        queryConfig.update { it.copy(period = TimePeriod.CUSTOM, customRange = from to to, step = defaultStep) }
    }
    fun selectNetWorthStep(step: HistoryStep) {
        queryConfig.update { it.copy(step = step) }
    }
    fun addAccount(input: NewAccount, onSaved: () -> Unit = {}) = command(onSaved) { core ->
        core.accounts().use { it.create(input) }
    }
    fun editAccount(
        account: Account,
        input: NewAccount,
        isDefault: Boolean = account.isDefault,
        archived: Boolean = account.archived,
        calibratedBalances: Map<String, java.math.BigDecimal> = emptyMap(),
        onSaved: () -> Unit = {}
    ) = command(onSaved) { core ->
        core.accounts().use { api ->
            input.extraCurrencies.forEach { api.addCurrency(account.id, it.currency, it.openingBalance, null) }
            api.update(account.id, AccountPatch(name = input.name, last4 = input.last4,
                clearLast4 = input.last4 == null, kind = input.kind, mainCurrency = input.mainCurrency,
                institutionKey = input.institutionKey, clearInstitution = input.institutionKey == null,
                icon = input.icon, color = input.color, creditLimit = input.creditLimit,
                clearCreditLimit = input.creditLimit == null, statementDay = input.statementDay,
                dueDay = input.dueDay, clearBillingDays = true, archived = archived))
            calibratedBalances.forEach { (currency, newBalance) ->
                val oldBalance = account.pockets.firstOrNull { it.currency == currency }?.balance
                if (oldBalance == null || newBalance.compareTo(oldBalance) != 0) {
                    api.calibrate(account.id, currency, newBalance, null)
                }
            }
        }
        if (isDefault != account.isDefault) {
            core.settings().use { it.setDefaultAccount(if (isDefault) account.id else null) }
        }
    }
    fun deleteAccount(accountId: String, deleteTransactions: Boolean = false, onDeleted: () -> Unit = {}) = command(onDeleted) { core ->
        core.accounts().use { it.delete(accountId, deleteTransactions) }
    }
    fun archive(account: Account) = command { core -> core.accounts().use { it.update(account.id, AccountPatch(archived = !account.archived)) } }
    fun setDefault(id: String?) = command { core -> core.settings().use { it.setDefaultAccount(id) } }
    fun reorder(ids: List<String>) = command { core -> core.accounts().use { api ->
        ids.forEachIndexed { order, id -> api.update(id, AccountPatch(sortOrder = order)) }
    } }
    fun addPocket(id: String, currency: String, openingBalance: java.math.BigDecimal?) = command { core ->
        core.accounts().use { it.addCurrency(id, currency, openingBalance, null) }
    }
    fun removePocket(id: String, currency: String) = command { core -> core.accounts().use { it.removeCurrency(id, currency) } }
    fun setPocketLimit(id: String, currency: String, amount: java.math.BigDecimal?) = command { core ->
        core.accounts().use { it.setCurrencyCreditLimit(id, currency, amount) }
    }
    fun clearAccountSaveError() { mutable.update { it.copy(accountSaveError = null) } }
    fun clearError() { mutable.update { it.copy(errorMessage = null) } }
    private fun command(onSuccess: () -> Unit = {}, block: suspend (HootKitCoreInterface) -> Unit) {
        if (uiState.value.isSavingAccount) return
        mutable.update { it.copy(isSavingAccount = true, accountSaveError = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                session.withCore(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(accountSaveError = coreFailureText(failure), errorMessage = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(isSavingAccount = false) } }
        }
    }
}
