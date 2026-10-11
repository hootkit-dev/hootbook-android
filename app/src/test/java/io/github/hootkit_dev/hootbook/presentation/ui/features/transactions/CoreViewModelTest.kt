package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.ktor.client.engine.mock.respond
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
abstract class CoreViewModelTest {
    protected val dispatcher = StandardTestDispatcher()
    protected val views = ViewModelStore()
    protected val context get() = ApplicationProvider.getApplicationContext<Application>()
    protected val files get() = CoreAttachmentStore(context)
    protected val preferences get() = CoreUiPreferencesStore(context)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { views.clear(); Dispatchers.resetMain() }
    protected fun <T: ViewModel> keep(vm: T): T = vm.also { views.put("${it.javaClass.name}-${System.identityHashCode(it)}", it) }
    protected fun TestScope.session(core: FakeLedgerCore) = CoreSession(backgroundScope) { core }
    protected suspend fun TestScope.awaitState(condition: () -> Boolean) {
        repeat(1000) {
            runCurrent()
            if (condition()) return
            withContext(Dispatchers.IO) { delay(5) }
        }
        fail("State did not settle")
    }
    protected fun ChangeBus.changed(vararg tables: Table) = onChange(ChangeSet(tables.toList(), emptyList(), ChangeOrigin.LOCAL))

}
