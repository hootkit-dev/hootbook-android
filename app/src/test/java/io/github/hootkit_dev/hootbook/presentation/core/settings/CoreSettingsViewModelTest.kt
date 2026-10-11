package io.github.hootkit_dev.hootbook.presentation.core.settings

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
class CoreSettingsViewModelTest {
    private class LocalePreference(var selected: String = "en") : CoreUiLocalePreference {
        var applyFailure = false
        override fun selection() = selected
        override fun effectiveTag(selection: String) = selection.ifBlank { "en-GB" }
        override suspend fun apply(selection: String) { if (applyFailure) error("UI persistence failed"); selected = selection }
    }
    private fun settings(onDestroy: () -> Unit = {}, body: (String, Array<out Any?>) -> Any?): SettingsInterface =
        Proxy.newProxyInstance(SettingsInterface::class.java.classLoader, arrayOf(SettingsInterface::class.java, Disposable::class.java)) { _, method, args ->
            if (method.name == "destroy") { onDestroy(); Unit } else body(method.name.substringBefore('-'), args ?: emptyArray())
        } as SettingsInterface
    private fun failure(args: Array<out Any?>, throwable: Throwable): Any {
        @Suppress("UNCHECKED_CAST") val continuation = args.last() as Continuation<Any?>
        continuation.resumeWithException(throwable); return COROUTINE_SUSPENDED
    }
    private fun runModel(test: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try { test(store) } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun firstQueryUsesActualCoreMainCurrencyNotOldChineseDefaultsAndStringAccountId() = runModel { store ->
        var main = "GBP"; var default: String? = "account-uuid"; var opened = 0; var closed = 0
        val vm = CoreSettingsViewModel(ChangeBus(), LocalePreference()) { opened++; settings({ closed++ }) { method, args -> when(method) {
            "mainCurrency" -> main; "defaultAccount" -> default
            "setMainCurrency" -> { main = args[0] as String; Unit }
            "setDefaultAccount" -> { default = args[0] as String?; Unit }
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent()
        assertEquals("GBP", vm.state.value.mainCurrency); assertEquals("GBP", vm.state.value.currencyDraft)
        assertEquals("account-uuid", vm.state.value.defaultAccountId)
        vm.currencyChanged(" EUR "); vm.saveCurrency(); runCurrent(); assertEquals("EUR", vm.state.value.mainCurrency)
        vm.defaultAccount("another-uuid"); runCurrent(); assertEquals("another-uuid", vm.state.value.defaultAccountId)
        vm.defaultAccount(null); runCurrent(); assertNull(vm.state.value.defaultAccountId)
        assertEquals(opened, closed)
    }
    @Test fun queryRevisionRejectsCancelledOldSettingsAndInvalidationsPreserveDirtyDraft() = runModel { store ->
        var old: Continuation<String>? = null; var queries = 0; var main = "GBP"; val changes = ChangeBus()
        val vm = CoreSettingsViewModel(changes, LocalePreference()) { settings { method, args -> when(method) {
            "mainCurrency" -> if (++queries == 1) { @Suppress("UNCHECKED_CAST")
                old = args.last() as Continuation<String>; COROUTINE_SUSPENDED } else main
            "defaultAccount" -> null
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.refresh(); runCurrent(); assertEquals("GBP", vm.state.value.mainCurrency)
        old!!.resume("CNY"); runCurrent(); assertEquals("GBP", vm.state.value.mainCurrency)
        vm.currencyChanged("USD"); main = "EUR"
        changes.onChange(ChangeSet(listOf(Table.SETTINGS), emptyList(), ChangeOrigin.IMPORT)); runCurrent()
        assertEquals("EUR", vm.state.value.mainCurrency); assertEquals("USD", vm.state.value.currencyDraft)
        val count = queries; changes.onChange(ChangeSet(listOf(Table.SUBSCRIPTIONS), emptyList(), ChangeOrigin.LOCAL)); runCurrent(); assertEquals(count, queries)
        vm.discardCurrencyDraft(); assertEquals("EUR", vm.state.value.currencyDraft)
    }
    @Test fun duplicateWritesBackAndTargetEditsAreBlockedUntilCoreCompletes() = runModel { store ->
        var pending: Continuation<Unit>? = null; var writes = 0; var main = "GBP"
        val vm = CoreSettingsViewModel(ChangeBus(), LocalePreference()) { settings { method, args -> when(method) {
            "mainCurrency" -> main; "defaultAccount" -> null
            "setMainCurrency" -> { writes++; main = args[0] as String
                @Suppress("UNCHECKED_CAST") pending = args.last() as Continuation<Unit>; COROUTINE_SUSPENDED }
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.currencyChanged("EUR"); vm.saveCurrency(); vm.saveCurrency(); runCurrent()
        vm.currencyChanged("USD"); vm.defaultAccount("wrong-target"); vm.language("zh-CN")
        assertEquals(1, writes); assertTrue(vm.back()); assertEquals("EUR", vm.state.value.currencyDraft)
        assertTrue(vm.state.value.navigationBlocked); pending!!.resume(Unit); runCurrent()
        assertFalse(vm.state.value.saving); assertFalse(vm.back()); assertEquals("EUR", vm.state.value.mainCurrency)
    }
    @Test fun coreInvalidAndNotFoundFailuresRetainDraftAndRetryThroughGeneratedInterfaces() = runModel { store ->
        var fail = true
        val vm = CoreSettingsViewModel(ChangeBus(), LocalePreference()) { settings { method, args -> when(method) {
            "mainCurrency" -> "GBP"; "defaultAccount" -> null
            "setMainCurrency" -> if (fail) failure(args, CoreException.Invalid("currency", InvalidReason.BAD_FORMAT)) else Unit
            "setDefaultAccount" -> failure(args, CoreException.NotFound(Entity.ACCOUNT, args[0] as String))
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.currencyChanged("bad!"); vm.saveCurrency(); runCurrent()
        assertNotNull(vm.state.value.error); assertEquals("bad!", vm.state.value.currencyDraft); assertTrue(vm.state.value.currencyDirty)
        fail = false; vm.currencyChanged("USD"); vm.saveCurrency(); runCurrent(); assertNull(vm.state.value.error)
        vm.defaultAccount("deleted-string-uuid"); runCurrent(); assertNotNull(vm.state.value.error); assertNull(vm.state.value.defaultAccountId)
    }
    @Test fun localeIsAcceptedByCoreBeforeUiPersistenceAndSystemModeHasNonEmptyCoreTag() = runModel { store ->
        val preference = LocalePreference(); val calls = mutableListOf<String>(); var closed = 0
        val vm = CoreSettingsViewModel(ChangeBus(), preference) { settings({ closed++ }) { method, args -> when(method) {
            "mainCurrency" -> "GBP"; "defaultAccount" -> null
            "setLocale" -> { calls += "${args[0]}:${preference.selected}"; Unit }
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.language("zh-CN"); runCurrent()
        assertEquals(listOf("zh-CN:en"), calls); assertEquals("zh-CN", preference.selected); assertEquals("zh-CN", vm.state.value.localeSelection)
        vm.language(""); runCurrent(); assertEquals("en-GB:zh-CN", calls.last()); assertEquals("", preference.selected); assertTrue(closed > 0)
    }
    @Test fun rejectedCoreLocaleDoesNotWriteUiAndUiFailureRestoresPreviousCoreLocale() = runModel { store ->
        val preference = LocalePreference(); var coreFail = true; val tags = mutableListOf<String>()
        val vm = CoreSettingsViewModel(ChangeBus(), preference) { settings { method, args -> when(method) {
            "mainCurrency" -> "GBP"; "defaultAccount" -> null
            "setLocale" -> { tags += args[0] as String
                if (coreFail) failure(args, CoreException.Invalid("locale", InvalidReason.EMPTY)) else Unit }
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.language("zh-CN"); runCurrent(); assertEquals("en", preference.selected)
        assertNotNull(vm.state.value.error); assertEquals(CoreLocaleFailure.CORE_COMMAND_FAILED,vm.state.value.localeFailure); coreFail = false; preference.applyFailure = true
        vm.language("zh-CN"); runCurrent(); assertEquals(listOf("zh-CN", "zh-CN", "en"), tags)
        assertEquals("en", preference.selected); assertEquals(CoreLocaleFailure.UI_ROLLED_BACK,vm.state.value.localeFailure)
        assertFalse(vm.state.value.error!!.contains("UI persistence failed"))
        preference.applyFailure = false; vm.language("zh-CN"); runCurrent(); assertNull(vm.state.value.error); assertNull(vm.state.value.localeFailure); assertEquals("zh-CN", preference.selected)
    }
    @Test fun failedFirstQueryHasRetryAndCannotWriteUnloadedState() = runModel { store ->
        var fail = true; var writes = 0
        val vm = CoreSettingsViewModel(ChangeBus(), LocalePreference()) { settings { method, _ -> when(method) {
            "mainCurrency" -> if (fail) error("read failed") else "GBP"; "defaultAccount" -> null
            "setMainCurrency" -> { writes++; Unit }; else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); assertFalse(vm.state.value.loaded); vm.saveCurrency(); runCurrent(); assertEquals(0, writes)
        fail = false; vm.clearError(); vm.refresh(); runCurrent(); assertTrue(vm.state.value.loaded); assertEquals("GBP", vm.state.value.currencyDraft)
    }
    @Test fun failedCoreLocaleRollbackReportsDivergenceInsteadOfSuccess() = runModel { store ->
        val preference = LocalePreference(); preference.applyFailure = true; var localeWrites = 0
        val vm = CoreSettingsViewModel(ChangeBus(), preference) { settings { method, args -> when(method) {
            "mainCurrency" -> "GBP"; "defaultAccount" -> null
            "setLocale" -> if (++localeWrites == 2) failure(args, CoreException.Platform("rollback failed")) else Unit
            else -> error(method)
        } } }
        store.put("vm", vm); runCurrent(); vm.language("zh-CN"); runCurrent()
        assertEquals("en", preference.selected); assertEquals(0, vm.state.value.commandRevision)
        assertEquals(CoreLocaleFailure.DIVERGED,vm.state.value.localeFailure); assertFalse(vm.state.value.error!!.contains("rollback failed")); assertFalse(vm.state.value.saving)
    }

    @Test fun divergenceWarningSurvivesDismissAndUnrelatedWritesUntilLocaleSynchronizationSucceeds() = runModel { store ->
        val preference=LocalePreference().apply{applyFailure=true};var calls=0;var failRollback=true
        val vm=CoreSettingsViewModel(ChangeBus(),preference){settings{method,args->when(method){
            "mainCurrency"->"GBP";"defaultAccount"->null;"setMainCurrency"->Unit
            "setLocale"->{calls++;if(failRollback&&calls==2)failure(args,CoreException.Platform("PRIVATE_ROLLBACK_BODY"))else Unit}
            else->error(method)
        }}}
        store.put("vm",vm);runCurrent();vm.language("zh-CN");runCurrent();assertEquals(CoreLocaleFailure.DIVERGED,vm.state.value.localeFailure)
        assertFalse(vm.state.value.error!!.contains("PRIVATE_ROLLBACK_BODY"));vm.clearError();assertEquals(CoreLocaleFailure.DIVERGED,vm.state.value.localeFailure)
        vm.currencyChanged("GBP");vm.saveCurrency();runCurrent();assertEquals(CoreLocaleFailure.DIVERGED,vm.state.value.localeFailure)
        preference.applyFailure=false;failRollback=false;vm.language("en");runCurrent();assertNull(vm.state.value.localeFailure);assertEquals("en",preference.selected)
    }

}
