package io.github.hootkit_dev.hootbook.presentation.core.currencies

import android.app.Application
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreCurrenciesViewModelTest {
    private val now = LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u)
    private fun row(from: String = "USD", to: String = "GBP", custom: Boolean = true) = ExchangeRate(from, to,
        BigDecimal("0.81111112345"), if(custom) "custom" else "ErApi", custom, now, now, true)
    private fun currency(onDestroy: () -> Unit = {}, body: (String, Array<out Any?>) -> Any?): CurrencyInterface =
        Proxy.newProxyInstance(CurrencyInterface::class.java.classLoader, arrayOf(CurrencyInterface::class.java, Disposable::class.java)) { _, method, args ->
            if (method.name == "destroy") { onDestroy(); Unit } else body(method.name.substringBefore('-'), args ?: emptyArray())
        } as CurrencyInterface
    private fun query(method: String, rates: List<ExchangeRate> = emptyList()): Any? = when(method) {
        "rates" -> rates; "customCurrencies" -> emptyList<CustomCurrency>(); "provider" -> RateProvider.AUTO
        "convert" -> null; "rate" -> null
        else -> error(method)
    }
    private fun failure(args: Array<out Any?>, error: Throwable): Any {
        @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<Any?>
        continuation.resumeWithException(error); return COROUTINE_SUSPENDED
    }
    private fun runModel(test: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try { test(store) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun actualMainCurrencyAndGeneratedRateRecordsArePreservedAndHandlesReleased() = runModel { store ->
        val rate = row(); var opened = 0; var destroyed = 0
        val vm = CoreCurrenciesViewModel(ChangeBus(), mainCurrency = { "GBP" }) {
            opened++; currency({ destroyed++ }) { method, _ -> query(method, listOf(rate)) }
        }
        store.put("vm", vm); vm.initialCurrency("USD"); runCurrent()
        assertEquals("GBP", vm.state.value.mainCurrency); assertSame(rate, vm.state.value.rates.single())
        assertEquals("USD", vm.state.value.conversionDraft.from); assertEquals("GBP", vm.state.value.conversionDraft.to)
        assertEquals(opened, destroyed); vm.editRate(); assertEquals("GBP", vm.state.value.rateDraft!!.from)
    }
    @Test fun missingConversionStaysNullAndCrossExpiredCustomMetadataComesFromCore() = runModel { store ->
        var result: BigDecimal? = null
        var rate: ResolvedRate? = null
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, _ -> when(method) {
            "convert" -> result; "rate" -> rate; else -> query(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.conversionChanged(CoreConversionDraft("100", "USD", "GBP")); vm.convert(); runCurrent()
        assertNull(vm.state.value.preview!!.amount); assertNull(vm.state.value.preview!!.rate)
        result = BigDecimal("81.11"); rate = ResolvedRate("USD", "GBP", BigDecimal("0.81111112345"), RateKind.CROSS, true, true, "EUR")
        vm.convert(); runCurrent(); assertSame(result, vm.state.value.preview!!.amount); assertSame(rate, vm.state.value.preview!!.rate)
    }
    @Test fun stalePreviewAndStaleListCannotOverwriteNewInputsOrLatestRates() = runModel { store ->
        var oldRates: Continuation<List<ExchangeRate>>? = null; var rateReads = 0
        var oldConversion: Continuation<BigDecimal?>? = null
        val newest = row(to = "EUR")
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "rates" -> if (++rateReads == 1) { @Suppress("UNCHECKED_CAST")
                oldRates = args.last() as Continuation<List<ExchangeRate>>; COROUTINE_SUSPENDED } else listOf(newest)
            "convert" -> if (args[1] == "USD") { @Suppress("UNCHECKED_CAST")
                oldConversion = args.last() as Continuation<BigDecimal?>; COROUTINE_SUSPENDED } else BigDecimal("55.50")
            else -> query(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.refresh(); runCurrent(); oldRates!!.resume(listOf(row())); runCurrent()
        assertSame(newest, vm.state.value.rates.single())
        vm.conversionChanged(CoreConversionDraft("100", "USD", "GBP")); vm.convert(); runCurrent()
        vm.conversionChanged(CoreConversionDraft("100", "EUR", "GBP")); vm.convert(); runCurrent(); oldConversion!!.resume(BigDecimal("99")); runCurrent()
        assertEquals("EUR", vm.state.value.preview!!.input.from); assertEquals(BigDecimal("55.50"), vm.state.value.preview!!.amount)
    }
    @Test fun downloadOutcomeFailureDoesNotPretendSuccessAndProviderChangeIsExplicit() = runModel { store ->
        var outcome: RefreshOutcome = RefreshOutcome.Failed(503u); var provider: RateProvider? = null; val force = mutableListOf<Boolean>()
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "refresh" -> { force += args[0] as Boolean; outcome }
            "setProvider" -> { provider = args[0] as RateProvider; Unit }
            else -> query(method, listOf(row()))
        } } }
        store.put("vm", vm); runCurrent(); vm.download(true); runCurrent()
        assertEquals(RefreshOutcome.Failed(503u), vm.state.value.refreshOutcome); assertEquals(1, vm.state.value.rates.size)
        outcome = RefreshOutcome.NotNeeded; vm.download(false); runCurrent(); assertSame(outcome, vm.state.value.refreshOutcome)
        outcome = RefreshOutcome.Updated(RateProvider.FRANKFURTER, 32u); vm.download(true); runCurrent(); assertSame(outcome, vm.state.value.refreshOutcome)
        vm.provider(RateProvider.CURRENCY_API); runCurrent(); assertEquals(RateProvider.CURRENCY_API, provider)
        assertEquals(listOf(true, false, true), force)
    }
    @Test fun invalidCustomRateRetainsExactDraftAndCoreOwnsPositivityPrecisionAndTarget() = runModel { store ->
        var pending: Continuation<Unit>? = null; var calls = 0; val rate = row(); var saved: Triple<String, String, BigDecimal>? = null
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "setCustomRate" -> { calls++; saved = Triple(args[0] as String, args[1] as String, args[2] as BigDecimal)
                @Suppress("UNCHECKED_CAST") pending = args.last() as Continuation<Unit>; COROUTINE_SUSPENDED }
            else -> query(method, listOf(rate))
        } } }
        store.put("vm", vm); runCurrent(); vm.editRate(rate); vm.rateDraftChanged(vm.state.value.rateDraft!!.copy(from = "WRONG", to = "WRONG", amount = "-0.81111112345"))
        vm.saveRate(); vm.saveRate(); runCurrent(); assertEquals(1, calls); assertTrue(vm.back())
        vm.rateDraftChanged(CoreCustomRateDraft(from = "EUR")); vm.editCurrency(); vm.editRate(); vm.provider(RateProvider.ER_API)
        assertEquals("USD", vm.state.value.rateDraft!!.from); assertEquals("GBP", vm.state.value.rateDraft!!.to)
        assertEquals(BigDecimal("-0.81111112345"), saved!!.third)
        pending!!.resumeWithException(CoreException.Invalid("rate", InvalidReason.NOT_POSITIVE)); runCurrent()
        assertNotNull(vm.state.value.error); assertEquals("-0.81111112345", vm.state.value.rateDraft!!.amount)
        vm.rateDraftChanged(vm.state.value.rateDraft!!.copy(amount = "0.81111112345")); vm.saveRate(); runCurrent(); pending!!.resume(Unit); runCurrent()
        assertNull(vm.state.value.rateDraft); assertNull(vm.state.value.error); assertEquals(BigDecimal("0.81111112345"), saved!!.third)
    }
    @Test fun currencyUpdateCannotRenameOriginalCodeAndDeleteOnlyRemovesNamedMetadata() = runModel { store ->
        var saved: CustomCurrency? = null; var removed: String? = null; var rateRemoved: Pair<String,String>? = null
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "setCustomCurrency" -> { saved = args[0] as CustomCurrency; Unit }
            "removeCustomCurrency" -> { removed = args[0] as String; Unit }
            "removeCustomRate" -> { rateRemoved = args[0] as String to args[1] as String; Unit }
            else -> query(method)
        } } }
        store.put("vm", vm); runCurrent(); val existing = CustomCurrency("PTS", "Points", "P")
        vm.editCurrency(existing); vm.currencyDraftChanged(vm.state.value.currencyDraft!!.copy(code = "USD", name = " New points ", symbol = "Q")); vm.saveCurrency(); runCurrent()
        assertEquals(CustomCurrency("PTS", "New points", "Q"), saved)
        vm.deleteCurrency(existing); runCurrent(); assertEquals("PTS", removed); assertNull(rateRemoved)
        vm.deleteRate(row(custom = false)); runCurrent(); assertNull(rateRemoved)
        vm.deleteRate(row()); runCurrent(); assertEquals("USD" to "GBP", rateRemoved)
    }
    @Test fun settingsAndRatesInvalidationRefreshButUnrelatedTablesDoNotAndDraftSurvives() = runModel { store ->
        var reads = 0; var value = "GBP"; val changes = ChangeBus()
        val vm = CoreCurrenciesViewModel(changes, { value }) { currency { method, _ -> if (method == "rates") reads++; query(method) } }
        store.put("vm", vm); runCurrent(); vm.editCurrency(); vm.currencyDraftChanged(CoreCustomCurrencyDraft(code = "PTS", name = "Points"))
        val before = reads; value = "EUR"; changes.onChange(ChangeSet(listOf(Table.SETTINGS), emptyList(), ChangeOrigin.IMPORT)); runCurrent()
        assertEquals(before + 1, reads); assertEquals("EUR", vm.state.value.mainCurrency); assertEquals("PTS", vm.state.value.currencyDraft!!.code)
        changes.onChange(ChangeSet(listOf(Table.EXCHANGE_RATES), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(before + 2, reads)
        changes.onChange(ChangeSet(listOf(Table.DEBTS), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(before + 2, reads)
    }
    @Test fun conversionParseAndCoreCapabilityErrorsAreExplicitAndRetryable() = runModel { store ->
        var apiCalls = 0; var fail = true
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "convert" -> { apiCalls++; if (fail) failure(args, CoreException.Invalid("from", InvalidReason.BAD_FORMAT)) else BigDecimal("42.00") }
            else -> query(method)
        } } }
        store.put("vm", vm); runCurrent(); assertNotNull(vm.state.value.previewError)
        vm.conversionChanged(CoreConversionDraft("not-a-number", "USD", "GBP")); val before = apiCalls; vm.convert(); runCurrent()
        assertEquals(before, apiCalls); assertNotNull(vm.state.value.previewError); assertNull(vm.state.value.preview)
        fail = false; vm.conversionChanged(CoreConversionDraft("100", "USD", "GBP")); vm.convert(); runCurrent()
        assertNull(vm.state.value.previewError); assertEquals(BigDecimal("42.00"), vm.state.value.preview!!.amount)
    }
    @Test fun unavailableDownloadCapabilityIsExplicitAndCanRetryWithoutLosingRates() = runModel { store ->
        var fail = true; var opened = 0; var destroyed = 0; val original = row()
        val vm = CoreCurrenciesViewModel(ChangeBus()) { opened++; currency({ destroyed++ }) { method, args -> when(method) {
            "refresh" -> if (fail) failure(args, CoreException.Platform("HTTP transport unavailable")) else RefreshOutcome.NotNeeded
            else -> query(method, listOf(original))
        } } }
        store.put("vm", vm); runCurrent(); vm.download(true); runCurrent()
        assertTrue(vm.state.value.downloadFailed); assertEquals(io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText(CoreException.Platform("HTTP transport unavailable")),vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("HTTP transport unavailable")); assertSame(original, vm.state.value.rates.single())
        assertNull(vm.state.value.refreshOutcome); assertEquals(opened, destroyed)
        fail = false; vm.download(true); runCurrent(); assertNull(vm.state.value.error);assertFalse(vm.state.value.downloadFailed)
        assertSame(RefreshOutcome.NotNeeded, vm.state.value.refreshOutcome); assertEquals(opened, destroyed)
    }
    @Test fun customCurrencyCoreValidationRetainsDraftAndDoesNotLocallyTruncateSymbols() = runModel { store ->
        var submitted: CustomCurrency? = null; var fail = true
        val vm = CoreCurrenciesViewModel(ChangeBus()) { currency { method, args -> when(method) {
            "setCustomCurrency" -> { submitted = args[0] as CustomCurrency
                if (fail) failure(args, CoreException.Invalid("symbol", InvalidReason.BAD_FORMAT)) else Unit }
            else -> query(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.editCurrency()
        vm.currencyDraftChanged(CoreCustomCurrencyDraft(code = "pts", name = "Points", symbol = "TOOLONG")); vm.saveCurrency(); runCurrent()
        assertEquals("TOOLONG", submitted!!.symbol); assertEquals("pts", submitted!!.code)
        assertEquals("TOOLONG", vm.state.value.currencyDraft!!.symbol); assertNotNull(vm.state.value.error)
        fail = false; vm.currencyDraftChanged(vm.state.value.currencyDraft!!.copy(symbol = "P")); vm.saveCurrency(); runCurrent()
        assertNull(vm.state.value.currencyDraft); assertNull(vm.state.value.error)
    }

}
