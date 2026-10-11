package io.github.hootkit_dev.hootbook.presentation.ui.features.settings

import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreUiLocalePreference
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.FakeAccountsCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.plans.FakePlansCore
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance.ThemeViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.currency.CurrencySettingsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.notifications.NotificationViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock.AppLockViewModel
import io.github.hootkit_dev.hootbook.data.preferences.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native NoPointer services use deliberately inconsistent totals to catch presentation recomputation. */
internal class SettingsFamilyCore : FakeAccountsCore() {
    val plans = FakePlansCore()
    var currency = "GBP"
    var writes = 0
    var mainWrites = 0
    var onSettings: suspend () -> Unit = {}
    var coreLocale = "en-GB"
    override fun settings(): Settings = object : Settings(NoPointer) {
        override suspend fun mainCurrency() = currency
        override suspend fun setMainCurrency(currency: String) { mainWrites++; onSettings(); this@SettingsFamilyCore.currency = currency }
        override suspend fun setDefaultAccount(accountId: String?) { defaultId = accountId; onSettings() }
        override suspend fun setLocale(locale: String) { writes++; onSettings(); coreLocale = locale }
    }
    override fun plans() = plans.plans()
}
internal class TestLocale : CoreUiLocalePreference {
    var selected = "en"
    var fail = false
    override fun selection() = selected
    override fun effectiveTag(selection: String) = selection.ifBlank { "en-GB" }
    override suspend fun apply(selection: String) { if (fail) error("persistence"); selected = selection }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : CoreViewModelTest() {
    @Test fun initialCountAndChangesComeFromCoreAndNewestSnapshotWins() = runTest {
        val core = SettingsFamilyCore(); val bus = ChangeBus(); core.onList = { _, _ -> TransactionPage(emptyList(), 17u) }
        val vm = keep(SettingsViewModel(CoreSession(backgroundScope) { core }, bus, preferences, CorePlatformAssets(context), TestLocale()))
        awaitState { vm.totalTransactions.value == 17u && !vm.busy.value }
        val gate = CompletableDeferred<Unit>(); var entered = false
        core.onList = { _, _ -> entered = true; withContext(NonCancellable) { gate.await() }; TransactionPage(emptyList(), 99u) }
        bus.changed(Table.TRANSACTIONS); awaitState { entered }
        core.onList = { _, _ -> TransactionPage(emptyList(), 24u) }; bus.changed(Table.TRANSACTIONS); gate.complete(Unit)
        awaitState { vm.totalTransactions.value == 24u }
    }
    @Test fun localeCommandsGuardDuplicatesCompensateUiFailureAndRetry() = runTest {
        val core = SettingsFamilyCore(); val locale = TestLocale(); val gate = CompletableDeferred<Unit>()
        val vm = keep(SettingsViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), preferences, CorePlatformAssets(context), locale))
        awaitState { !vm.busy.value }
        core.onSettings = { gate.await() }; vm.language("fr"); vm.language("de")
        awaitState { core.writes == 1 }; gate.complete(Unit); awaitState { !vm.busy.value }
        assertEquals("fr", locale.selected); assertEquals("fr", core.coreLocale)
        core.onSettings = {}; locale.fail = true; vm.language("de"); awaitState { !vm.busy.value }
        assertNotNull(vm.error.value); assertEquals("fr", core.coreLocale)
        locale.fail = false; vm.language("de"); awaitState { !vm.busy.value }; assertEquals("de", locale.selected)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencySettingsViewModelTest : CoreViewModelTest() {
    @Test fun mainCurrencyLoadsRefreshesAndCommandsGuardDuplicatesAndRetainPrefs() = runTest {
        val core = SettingsFamilyCore(); val bus = ChangeBus(); val prefs = preferences
        val vm = keep(CurrencySettingsViewModel(CoreSession(backgroundScope) { core }, bus, prefs))
        awaitState { !vm.uiState.value.loading && !vm.uiState.value.saving }; assertEquals("GBP", vm.uiState.value.mainCurrency)
        core.currency = "EUR"; bus.changed(Table.SETTINGS); awaitState { vm.uiState.value.mainCurrency == "EUR" }
        val gate = CompletableDeferred<Unit>(); core.onSettings = { gate.await(); error("rejected") }
        vm.setMainCurrency("USD"); vm.setMainCurrency("JPY"); awaitState { core.mainWrites == 1 }
        gate.complete(Unit); awaitState { !vm.uiState.value.saving }; assertNotNull(vm.uiState.value.error)
        core.onSettings = {}; vm.setMainCurrency("USD"); awaitState { !vm.uiState.value.saving }
        assertEquals("USD", core.currency)
        vm.setUnifiedCurrency("EUR"); awaitState { !vm.uiState.value.saving }; vm.toggleUnifiedCurrency(); awaitState { !vm.uiState.value.saving }
        assertEquals("EUR", prefs.state.value.unifiedDisplayCurrency); assertTrue(prefs.state.value.unifiedDisplayEnabled)
        assertEquals("USD", core.currency)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest : CoreViewModelTest() {
    @Test fun profileUsesLiteralCoreRecordsAndRefreshesContactsAndTotals() = runTest {
        val core = SettingsFamilyCore(); val bus = ChangeBus()
        val vm = keep(ProfileViewModel(CoreSession(backgroundScope) { core }, bus, preferences, CorePlatformAssets(context), files, context))
        awaitState { !vm.state.value.isLoading && !vm.state.value.saving }
        assertSame(core.overview, vm.state.value.overview); assertSame(core.summary, vm.state.value.monthly)
        assertSame(core.plans.subscriptionSummary, vm.state.value.subscriptions)
        core.overview = core.overview.copy(netWorthComplete = false); core.plans.people = listOf(core.plans.person.copy(name = "Updated"))
        bus.changed(Table.ACCOUNTS, Table.PEOPLE); awaitState { vm.state.value.contacts.singleOrNull()?.name == "Updated" }
        assertFalse(vm.state.value.overview!!.netWorthComplete)
    }
    @Test fun profileDraftSurvivesCommandErrorGuardsDuplicateAndRetries() = runTest {
        val core = SettingsFamilyCore(); val prefs = preferences; val gate = CompletableDeferred<Unit>()
        val vm = keep(ProfileViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs, CorePlatformAssets(context), files, context))
        awaitState { !vm.state.value.isLoading && !vm.state.value.saving }
        vm.toggleEditSheet(); vm.updateEditUserName("Ada"); vm.saveProfileChanges(); vm.saveProfileChanges()
        awaitState { !vm.state.value.saving }; assertEquals("Ada", prefs.state.value.userName); assertFalse(vm.state.value.isEditSheetOpen)
        core.plans.onCommand = { gate.await(); error("rejected") }
        vm.showAddPersonSheet(true); vm.addPerson("Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND)
        vm.addPerson("Duplicate", null, null, "#4CAF50", null, null)
        awaitState { core.plans.commandCalls == 1 }; gate.complete(Unit); awaitState { !vm.state.value.saving }
        assertTrue(vm.state.value.isAddPersonSheetOpen); assertNotNull(vm.state.value.error)
        core.plans.onCommand = {}; vm.addPerson("Alice", null, null, "#4CAF50", null, PersonRelation.FRIEND)
        awaitState { !vm.state.value.saving }; assertFalse(vm.state.value.isAddPersonSheetOpen)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest : CoreViewModelTest() {
    @Test fun originalRowsUseCoreSubscriptionsRefreshAndStringMuteIdsReachScheduler() = runTest {
        val core = SettingsFamilyCore(); val bus = ChangeBus(); val prefs = preferences; var scheduled = 0
        val vm = keep(NotificationViewModel(CoreSession(backgroundScope) { core }, bus, prefs) { scheduled++ })
        awaitState { !vm.uiState.value.loading && !vm.uiState.value.saving }
        assertSame(core.plans.subscription, vm.uiState.value.subscriptions.single())
        core.plans.subscription = core.plans.subscription.copy(name = "Updated")
        bus.changed(Table.SUBSCRIPTIONS); awaitState { vm.uiState.value.subscriptions.single().name == "Updated" }
        val id = core.plans.subscription.id
        vm.toggleSubscriptionNotification(id, false); vm.toggleSubscriptionNotification("duplicate", false)
        awaitState { !vm.uiState.value.saving }
        assertEquals(setOf(id), prefs.state.value.mutedSubscriptionIds); assertEquals(1, scheduled)
        vm.setUpcomingNotificationsEnabled(false); awaitState { !vm.uiState.value.saving }
        assertFalse(prefs.state.value.notificationsEnabled); assertEquals(2, scheduled)
    }
    @Test fun failedLoadRecoversOnChange() = runTest {
        val core = SettingsFamilyCore(); val bus = ChangeBus(); core.plans.onSubscriptions = { error("load") }
        val vm = keep(NotificationViewModel(CoreSession(backgroundScope) { core }, bus, preferences) {})
        awaitState { !vm.uiState.value.loading }; assertNotNull(vm.uiState.value.error)
        core.plans.onSubscriptions = { listOf(core.plans.subscription) }; bus.changed(Table.SUBSCRIPTIONS)
        awaitState { vm.uiState.value.subscriptions.isNotEmpty() }; assertNull(vm.uiState.value.error)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest : CoreViewModelTest() {
    @Test fun originalAppearanceControlsPersistObserveAndGuardRepeatedWrites() = runTest {
        val prefs = preferences; val vm = keep(ThemeViewModel(prefs, context)); awaitState { !vm.busy.value }
        vm.updateDarkTheme(true); vm.updateDarkTheme(false); awaitState { !vm.busy.value }; assertEquals(true, vm.themeUiState.value.darkTheme)
        vm.updateThemeStyle(ThemeStyle.DEFAULT); awaitState { !vm.busy.value }; assertFalse(prefs.state.value.dynamicColor)
        vm.updateAppFont(AppFont.SN_PRO); awaitState { !vm.busy.value }; assertEquals(AppFont.SN_PRO, prefs.state.value.font)
        prefs.update { it.copy(accent = AccentColor.GREEN) }; assertEquals(AccentColor.GREEN, vm.themeUiState.value.accent)
    }
    @Test fun persistenceErrorIsVisibleAndDoesNotPublishDraft() = runTest {
        val prefs = preferences; val vm = keep(ThemeViewModel(prefs, context)); awaitState { !vm.busy.value }
        val file = File(context.noBackupFilesDir, "hootkit-core/ui/preferences.json")
        file.parentFile!!.deleteRecursively(); file.parentFile!!.writeText("block atomic write")
        vm.updateAppFont(AppFont.SN_PRO); awaitState { !vm.busy.value }; assertNotNull(vm.error.value); assertEquals(AppFont.SYSTEM, prefs.state.value.font)
        file.parentFile!!.delete(); vm.updateAppFont(AppFont.SN_PRO); awaitState { !vm.busy.value }; assertEquals(AppFont.SN_PRO, prefs.state.value.font)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest : CoreViewModelTest() {
    @Test fun lockPolicyIsObservedNoUnauthenticatedEnableAndTimeoutChangesUseController() = runTest {
        val prefs = preferences; val controller = CoreAppLockController(context, prefs)
        val vm = keep(AppLockViewModel(controller, prefs, context)); awaitState { vm.uiState.value.isLoaded }
        vm.setAppLockEnabled(true); vm.setAppLockEnabled(true); awaitState { !vm.uiState.value.saving }
        assertNotNull(vm.uiState.value.authenticationError); assertFalse(prefs.state.value.appLockEnabled)
        vm.setTimeoutMinutes(15); vm.setTimeoutMinutes(30); awaitState { !vm.uiState.value.saving }
        awaitState { vm.uiState.value.timeoutMinutes == 15 }; assertEquals(15, prefs.state.value.appLockTimeoutMinutes)
        vm.setAppLockEnabled(false); awaitState { !vm.uiState.value.saving }; assertFalse(controller.state.value.locked)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnBoardingViewModelTest : CoreViewModelTest() {
    private fun platform(prefs: CoreUiPreferencesStore, assets: CorePlatformAssets) = BackupPlatformApplier(prefs, assets, TestLocale(), {}, {})
    @Test fun loadsCoreCurrencyCreatesExactlyOneStringIdAccountAndFinishesOnlyAfterPrefsSave() = runTest {
        val core = SettingsFamilyCore(); val prefs = preferences; val assets = CorePlatformAssets(context)
        val vm = keep(OnBoardingViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs, assets, platform(prefs, assets), context))
        awaitState { !vm.uiState.value.isLoading }; assertEquals("GBP", vm.uiState.value.selectedCurrency)
        vm.nextStep(); vm.updateManualAccountName("Bank"); vm.updateManualAccountBalance("123.45"); vm.updateManualAccountLast4("4321")
        val gate = CompletableDeferred<Unit>(); core.onCommand = { gate.await() }
        vm.saveManualAccount(); vm.saveManualAccount(); awaitState { core.creates == 1 }; gate.complete(Unit)
        awaitState { !vm.uiState.value.isLoading }; assertEquals("created/中文", core.defaultId); assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
        assertEquals("GBP", core.newAccount!!.mainCurrency); assertEquals("4321", core.newAccount!!.last4)
        vm.onNameChange("Ada"); vm.saveProfile(); awaitState { !vm.uiState.value.isLoading }
        assertEquals("Ada", prefs.state.value.userName); assertFalse(prefs.state.value.onboardingFinished)
        vm.finishOnboarding(); vm.finishOnboarding(); awaitState { !vm.uiState.value.isLoading }; assertTrue(prefs.state.value.onboardingFinished)
    }
    @Test fun createErrorRetainsDraftAndCanRetryWithoutAdvancing() = runTest {
        val core = SettingsFamilyCore(); val prefs = preferences; val assets = CorePlatformAssets(context)
        val vm = keep(OnBoardingViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs, assets, platform(prefs, assets), context))
        awaitState { !vm.uiState.value.isLoading }; vm.nextStep(); vm.updateManualAccountName("Bank"); vm.updateManualAccountBalance("10"); vm.updateManualAccountLast4("1234")
        core.onCommand = { error("reject") }; vm.saveManualAccount(); awaitState { !vm.uiState.value.isLoading }
        assertEquals(OnboardingStep.ACCOUNT, vm.uiState.value.step); assertNotNull(vm.uiState.value.errorMessage); assertEquals("Bank", vm.uiState.value.manualAccountName)
        core.onCommand = {}; vm.saveManualAccount(); awaitState { !vm.uiState.value.isLoading }; assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
    }
    @Test fun changeBusRefreshesCurrencyAndSyncSummaryAndDraftCurrencyStaysSelected() = runTest {
        val core = SettingsFamilyCore(); val prefs = preferences; val assets = CorePlatformAssets(context); val bus = ChangeBus()
        val vm = keep(OnBoardingViewModel(CoreSession(backgroundScope) { core }, bus, prefs, assets, platform(prefs, assets), context))
        awaitState { !vm.uiState.value.isLoading }
        core.currency = "EUR"; bus.changed(Table.SETTINGS); awaitState { vm.uiState.value.selectedCurrency == "EUR" }
        vm.updateSelectedCurrency("USD"); core.currency = "JPY"; bus.changed(Table.SETTINGS)
        vm.refreshSyncSummary(); awaitState { vm.uiState.value.syncedAccounts == core.overview.accounts.size }
        core.onList = { _, _ -> TransactionPage(emptyList(), 42u) }; bus.changed(Table.TRANSACTIONS)
        awaitState { vm.uiState.value.syncedTransactions == 42 }; assertEquals("USD", vm.uiState.value.selectedCurrency)
    }
    @Test fun failedSettingsAfterCreateRetriesReceiptWithoutCreatingDuplicateAccount() = runTest {
        val core = SettingsFamilyCore(); val prefs = preferences; val assets = CorePlatformAssets(context)
        val vm = keep(OnBoardingViewModel(CoreSession(backgroundScope) { core }, ChangeBus(), prefs, assets, platform(prefs, assets), context))
        awaitState { !vm.uiState.value.isLoading }; vm.nextStep(); vm.updateManualAccountName("Bank"); vm.updateManualAccountBalance("10"); vm.updateManualAccountLast4("1234")
        core.onSettings = { error("rejected") }; vm.saveManualAccount(); awaitState { !vm.uiState.value.isLoading }
        assertEquals(1, core.creates); assertEquals(OnboardingStep.ACCOUNT, vm.uiState.value.step)
        core.onSettings = {}; vm.saveManualAccount(); awaitState { !vm.uiState.value.isLoading }
        assertEquals(1, core.creates); assertTrue(vm.uiState.value.hasSavedAccount); assertEquals("created/中文", core.defaultId)
    }

}
