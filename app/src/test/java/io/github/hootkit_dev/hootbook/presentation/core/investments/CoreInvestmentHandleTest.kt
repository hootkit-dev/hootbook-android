package io.github.hootkit_dev.hootbook.presentation.core.investments

import android.app.Activity
import android.view.WindowManager
import androidx.lifecycle.ViewModelStore
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class CoreInvestmentHandleTest {
    @Test fun temporaryServicesDestroyOnQueryAndWriteButApplicationCoreStaysOpen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val core = TrackedInvestmentCore()
        val session = CoreSession(backgroundScope) { core }
        val store = ViewModelStore()
        try {
            val model = CoreInvestmentsViewModel(session, ChangeBus()); store.put("model", model)
            runCurrent(); model.refresh(1); runCurrent()
            assertTrue(core.opened > 0)
            assertEquals(core.opened, core.destroyed)
            assertFalse(core.rootDestroyed)
        } finally { store.clear(); session.shutdown(); Dispatchers.resetMain() }
    }
    @Test fun temporaryServiceDestroysOnTypedErrorAndCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val core = TrackedInvestmentCore()
        val session = CoreSession(backgroundScope) { core }
        val store = ViewModelStore()
        try {
            val model = CoreInvestmentsViewModel(session, ChangeBus()); store.put("model", model)
            runCurrent()
            core.fetch = { throw CoreException.Brokerage(BrokerageErrorKind.NETWORK) }
            model.refresh(1); runCurrent()
            assertEquals(core.opened, core.destroyed)
            val pending = CompletableDeferred<Unit>()
            core.fetch = { pending.await(); investmentConnection() }
            model.refresh(1); runCurrent(); model.cancelOperation(); runCurrent()
            assertEquals(core.opened, core.destroyed)
            assertFalse(core.rootDestroyed)
        } finally { store.clear(); session.shutdown(); Dispatchers.resetMain() }
    }
    @Test fun credentialWindowAddsSecureAndRestoresOnlyItsOwnBit() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val window = controller.get().window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val release = protectInvestmentWindow(window)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            release(); release()
            assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun credentialWindowDoesNotClearExistingAppLockSecureProtection() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val window = controller.get().window
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val release = protectInvestmentWindow(window)
            release()
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        } finally { controller.pause().stop().destroy() }
    }
}

private class TrackedInvestmentCore : HootKitCore(NoPointer) {
    var opened = 0
    var destroyed = 0
    var rootDestroyed = false
    var fetch: suspend () -> BrokerageConnection = { investmentConnection() }
    override suspend fun shutdown() = Unit
    override fun destroy() { rootDestroyed = true; super.destroy() }
    override fun brokerage(): Brokerage {
        opened++
        return object : Brokerage(NoPointer) {
            override fun providers() = emptyList<BrokerageProviderInfo>()
            override suspend fun connections() = listOf(investmentConnection())
            override suspend fun refresh(connectionId: Long) = fetch()
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun accounts(): Accounts {
        opened++
        return object : Accounts(NoPointer) {
            override suspend fun overview() = AccountsOverview(emptyList(), Money(BigDecimal.ZERO, "CNY"), true, null, null)
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
    override fun catalog(): Catalog {
        opened++
        return object : Catalog(NoPointer) {
            override fun all() = emptyList<Institution>()
            private var released = false
            override fun destroy() { if (!released) { released = true; destroyed++ }; super.destroy() }
        }
    }
}
