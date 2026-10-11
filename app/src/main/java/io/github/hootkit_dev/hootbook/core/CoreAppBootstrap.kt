package io.github.hootkit_dev.hootbook.core

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.platform.CoreSession
import dev.hootkit.platform.CoreSessionState
import io.github.hootkit_dev.hootbook.core.sync.CoreSyncCoordinator
import io.github.hootkit_dev.hootbook.core.system.CoreSystemCoordinator
import io.github.hootkit_dev.hootbook.core.ui.CoreAppLockController
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CoreBootstrapState { PREFERENCES, PREFERENCES_FAILED, OPENING, CORE_FAILED, LEGACY_UPGRADE, READY }
@Singleton
class CoreAppBootstrap @Inject constructor(@ApplicationContext private val context: Context,
    private val prefs: CoreUiPreferencesStore, private val session: Provider<CoreSession>,
    val legacyUpgrade: io.github.hootkit_dev.hootbook.core.migration.LegacyUpgradeGuard,
    private val startup: CoreStartupCoordinator, private val lock: Provider<CoreAppLockController>,
    private val sync: Provider<CoreSyncCoordinator>, private val system: Provider<CoreSystemCoordinator>,
    private val currencies: Provider<CoreCurrencyRefreshCoordinator>,
    @ApplicationScope private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(CoreBootstrapState.PREFERENCES); val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var foreground = false
    @Synchronized fun start() {
        if (CoreProcess.isCrashProcess(context) || job?.isActive == true) return
        job = scope.launch {
            mutable.value = CoreBootstrapState.PREFERENCES
            try { prefs.ready() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { mutable.value = CoreBootstrapState.PREFERENCES_FAILED; return@launch }
            if (legacyUpgrade.required()) {
                lock.get().retryLoading()
                mutable.value = CoreBootstrapState.LEGACY_UPGRADE
                return@launch
            }
            mutable.value = CoreBootstrapState.OPENING
            try {
                val native = session.get()
                if (native.state.value is CoreSessionState.Failed) native.retryOpening() else { startup.start(); native.awaitCore() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { mutable.value = CoreBootstrapState.CORE_FAILED; return@launch }
            // Only removed jobs are cancelled; no ledger/original backup/cache-source erasure.
            try { val work = WorkManager.getInstance(context)
                listOf("cashiro_sync", "CashiroCloudBackupWorker", "webhook_periodic_sync", "webhook_one_time_sync").forEach { work.cancelUniqueWork(it) }
            } catch (_: Throwable) { }
            lock.get().retryLoading(); sync.get().start(); system.get().start()
            mutable.value = CoreBootstrapState.READY
            currencies.get().coreReady()
            if (foreground) { lock.get().onForeground(); sync.get().foregrounded(); system.get().refresh() }
        }
    }
    fun foregroundChanged(value: Boolean) {
        foreground = value; startup.onForegroundChanged(value)
        if (state.value == CoreBootstrapState.LEGACY_UPGRADE) {
            if (value) lock.get().onForeground() else lock.get().onBackground()
            return
        }
        if (state.value != CoreBootstrapState.READY || CoreProcess.isCrashProcess(context)) return
        if (value) { lock.get().onForeground(); sync.get().foregrounded(); system.get().refresh(); currencies.get().foregrounded() }
        else { lock.get().onBackground(); sync.get().backgrounded() }
    }
}
