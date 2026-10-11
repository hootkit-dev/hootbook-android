package io.github.hootkit_dev.hootbook.presentation.core.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoreCustomCurrencyDraft(val originalCode: String? = null, val code: String = "", val name: String = "", val symbol: String = "")
data class CoreCustomRateDraft(val original: ExchangeRate? = null, val from: String = "", val to: String = "", val amount: String = "")
data class CoreConversionDraft(val amount: String = "1", val from: String = "", val to: String = "")
/** Only input/display state; the amount and resolution are returned by Core, not calculated in Kotlin. */
data class CoreConversionPreview(val input: CoreConversionDraft, val amount: BigDecimal?, val rate: ResolvedRate?)
data class CoreCurrenciesUiState(
    val loading: Boolean = true, val loaded: Boolean = false, val saving: Boolean = false,
    val navigationBlocked: Boolean = false, val error: String? = null,
    val mainCurrency: String = "", val provider: RateProvider? = null,
    val rates: List<ExchangeRate> = emptyList(), val customCurrencies: List<CustomCurrency> = emptyList(),
    val currencyDraft: CoreCustomCurrencyDraft? = null, val rateDraft: CoreCustomRateDraft? = null,
    val conversionDraft: CoreConversionDraft = CoreConversionDraft(), val preview: CoreConversionPreview? = null,
    val previewLoading: Boolean = false, val previewError: String? = null,
    val downloadFailed:Boolean=false,
    val refreshOutcome: RefreshOutcome? = null, val commandRevision: Long = 0,
)

@HiltViewModel
class CoreCurrenciesViewModel private constructor(
    private val session: CoreSession?, private val changes: ChangeBus,
    private val testCurrency: (suspend () -> CurrencyInterface)?, private val testMainCurrency: (suspend () -> String)?,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus) : this(session, changes, null, null)
    internal constructor(changes: ChangeBus, mainCurrency: suspend () -> String = { "GBP" },
        currency: suspend () -> CurrencyInterface) : this(null, changes, currency, mainCurrency)
    private val mutable = MutableStateFlow(CoreCurrenciesUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var previewQuery: Job? = null
    private var previewRevision = 0L
    private var initializedConversion = false
    private var initialCode: String? = null
    private suspend fun <T> withCurrency(block: suspend (CurrencyInterface) -> T): T {
        testCurrency?.let { provider ->
            val handle = provider()
            return if (handle is Disposable) handle.use { block(handle) } else block(handle)
        }
        return session!!.awaitCore().currency().use { block(it) }
    }
    init {
        viewModelScope.launch { changes.observe(setOf(Table.SETTINGS, Table.EXCHANGE_RATES)).collect { refresh() } }
    }
    fun refresh() {
        val request = ++queryRevision
        query?.cancel(); invalidatePreview()
        mutable.update { it.copy(loading = true) }
        query = viewModelScope.launch {
            try {
                val mainCurrency = testMainCurrency?.invoke() ?: session!!.awaitCore().settings().use { it.mainCurrency() }
                val result = withCurrency { Triple(it.rates(), it.customCurrencies(), it.provider()) }
                if (request == queryRevision) {
                    mutable.update { it.copy(loading = false, loaded = true, mainCurrency = mainCurrency, rates = result.first,
                        customCurrencies = result.second, provider = result.third,
                        conversionDraft = if (!initializedConversion) CoreConversionDraft(from = initialCode ?: mainCurrency, to = mainCurrency) else it.conversionDraft) }
                    initializedConversion = true
                    convert()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (request == queryRevision) mutable.update { it.copy(loading = false, error = coreFailureText(failure)) } }
        }
    }
    fun initialCurrency(code: String?) {
        if (code.isNullOrBlank() || state.value.saving) return
        if (!state.value.loaded) { initialCode = code; return }
        conversionChanged(state.value.conversionDraft.copy(from = code, to = state.value.mainCurrency))
    }
    fun editCurrency(row: CustomCurrency? = null) {
        if (blocked()) return
        mutable.update { it.copy(error = null, currencyDraft = row?.let { CoreCustomCurrencyDraft(it.code, it.code, it.name, it.symbol) }
            ?: CoreCustomCurrencyDraft()) }
    }
    fun currencyDraftChanged(draft: CoreCustomCurrencyDraft) {
        if (!state.value.saving) mutable.update { it.copy(currencyDraft = draft.copy(originalCode = it.currencyDraft?.originalCode, code = it.currencyDraft?.originalCode ?: draft.code)) }
    }
    fun dismissCurrency() { if (!blocked()) mutable.update { it.copy(currencyDraft = null) } }
    fun saveCurrency() {
        val draft = state.value.currencyDraft ?: return
        command { it.setCustomCurrency(CustomCurrency(draft.originalCode ?: draft.code.trim(), draft.name.trim(), draft.symbol))
            mutable.update { state -> state.copy(currencyDraft = null) } }
    }
    fun deleteCurrency(row: CustomCurrency) = command { it.removeCustomCurrency(row.code) }
    fun editRate(row: ExchangeRate? = null) {
        if (blocked()) return
        mutable.update { it.copy(error = null, rateDraft = row?.let { CoreCustomRateDraft(it, it.from, it.to, it.rate.toPlainString()) }
            ?: CoreCustomRateDraft(from = it.mainCurrency)) }
    }
    fun rateDraftChanged(draft: CoreCustomRateDraft) {
        if (!state.value.saving) mutable.update { it.copy(rateDraft = draft.copy(original = it.rateDraft?.original, from = it.rateDraft?.original?.from ?: draft.from,
            to = it.rateDraft?.original?.to ?: draft.to)) }
    }
    fun dismissRate() { if (!blocked()) mutable.update { it.copy(rateDraft = null) } }
    fun saveRate() {
        val draft = state.value.rateDraft ?: return
        command { it.setCustomRate(draft.from.trim(), draft.to.trim(), decimalInput(draft.amount))
            mutable.update { state -> state.copy(rateDraft = null) } }
    }
    fun deleteRate(row: ExchangeRate) { if (row.custom) command { it.removeCustomRate(row.from, row.to) } }
    fun provider(provider: RateProvider) = command { it.setProvider(provider) }
    fun download(force: Boolean) = command { api ->
        mutable.update {it.copy(refreshOutcome=null)}
        try {val outcome=api.refresh(force);mutable.update{it.copy(refreshOutcome=outcome,downloadFailed=outcome is RefreshOutcome.Failed)}}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Throwable){mutable.update{it.copy(downloadFailed=true)};throw failure}
    }
    fun conversionChanged(draft: CoreConversionDraft) {
        if (state.value.saving) return
        initializedConversion = true
        invalidatePreview()
        mutable.update { it.copy(conversionDraft = draft, previewError = null) }
    }
    private fun invalidatePreview() {
        ++previewRevision; previewQuery?.cancel()
        mutable.update { it.copy(preview = null, previewLoading = false) }
    }
    fun convert() {
        if (!state.value.loaded || state.value.saving) return
        val request = ++previewRevision; previewQuery?.cancel()
        val draft = state.value.conversionDraft
        mutable.update { it.copy(previewLoading = true, previewError = null, preview = null) }
        previewQuery = viewModelScope.launch {
            try {
                val result = withCurrency { api ->
                    val amount = api.convert(decimalInput(draft.amount), draft.from.trim(), draft.to.trim())
                    CoreConversionPreview(draft, amount, api.rate(draft.from.trim(), draft.to.trim()))
                }
                if (request == previewRevision && draft == state.value.conversionDraft)
                    mutable.update { it.copy(preview = result, previewLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (request == previewRevision) mutable.update { it.copy(previewError = coreFailureText(failure), previewLoading = false) } }
        }
    }
    fun clearError() { mutable.update { it.copy(error = null,downloadFailed=false) } }
    fun back(): Boolean {
        if (blocked()) return true
        if (state.value.currencyDraft != null) { dismissCurrency(); return true }
        if (state.value.rateDraft != null) { dismissRate(); return true }
        return false
    }
    private fun blocked(): Boolean {
        if (!state.value.saving) return false
        mutable.update { it.copy(navigationBlocked = true) }; return true
    }
    private fun command(block: suspend (CurrencyInterface) -> Unit) {
        if (state.value.saving || !state.value.loaded) return
        ++queryRevision; query?.cancel(); invalidatePreview()
        mutable.update { it.copy(saving = true, loading = false, error = null,downloadFailed=false, navigationBlocked = false) }
        viewModelScope.launch {
            try { withCurrency(block); mutable.update { it.copy(commandRevision = it.commandRevision + 1) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false, navigationBlocked = false) }; refresh() }
        }
    }
}
internal fun decimalInput(value: String): BigDecimal = value.trim().toBigDecimalOrNull()
    ?: throw IllegalArgumentException("请输入有效的十进制数")
