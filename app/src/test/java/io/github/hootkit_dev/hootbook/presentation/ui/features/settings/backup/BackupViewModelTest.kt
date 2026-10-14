package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.backup

import io.github.hootkit_dev.hootbook.presentation.data.backup.*

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import dev.hootkit.core.ImportMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class BackupViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val stores = mutableListOf<ViewModelStore>()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }
    private fun model(access: BackupTestAccess) = BackupViewModel(context, access, temp.newFolder()).also { vm ->
        stores += ViewModelStore().also { it.put("vm", vm) }
    }
    private suspend fun select(vm: BackupViewModel) {
        vm.state.first { !it.busy }
        val uri = Uri.parse("content://test-provider/source")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("backup".toByteArray()))
        vm.select(uri, false, null); vm.state.first { !it.busy }
        assertNotNull(vm.state.value.info)
    }
    @Test fun pickerCancellationKeepsExistingInspectionAndDoesNotImport() = runTest {
        val access = BackupTestAccess(); val vm = model(access); select(vm)
        val original = vm.state.value.source
        vm.select(null, true, "different")
        assertSame(original, vm.state.value.source); assertNotNull(vm.state.value.info); assertEquals(0, access.imports)
    }
    @Test fun repeatedImportAndBackBlockedWhileCommandIsPending() = runTest {
        val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        val access = BackupTestAccess().apply { beforeReturn = { entered.complete(Unit); release.await() } }
        val vm = model(access); select(vm)
        vm.restore(ImportMode.MERGE, null, false, false); vm.restore(ImportMode.MERGE, null, false, false)
        entered.await(); assertFalse(vm.canBack()); assertEquals(1, access.imports)
        vm.invalidateInspection(); assertNotNull(vm.state.value.info)
        release.complete(Unit); vm.state.first { !it.busy }
        assertTrue(vm.canBack()); assertNotNull(vm.state.value.result)
    }
    @Test fun replaceAllRequiresExplicitConfirmationBeforeCoreCall() = runTest {
        val access = BackupTestAccess(); val vm = model(access); select(vm)
        vm.restore(ImportMode.REPLACE_ALL, null, false, false); vm.state.first { !it.busy }
        assertEquals(0, access.imports); assertNotNull(vm.state.value.error); assertNotNull(vm.state.value.info)
    }
    @Test fun changedPasswordInvalidatesInspectionAndNeverWrites() = runTest {
        val access = BackupTestAccess(); val vm = model(access); select(vm)
        vm.restore(ImportMode.MERGE, "changed-password", false, false); vm.state.first { !it.busy }
        assertEquals(0, access.imports); assertEquals(context.getString(io.github.hootkit_dev.hootbook.R.string.core_ui_backup_inspect_first), vm.state.value.error)
        vm.invalidateInspection(); assertNull(vm.state.value.info)
    }
    @Test fun plaintextSecretExportRequiresConfirmationAndDoesNotPersistPassword() = runTest {
        val access = BackupTestAccess(); val vm = model(access); vm.state.first { !it.busy }
        vm.export(true, true, true, null, false); vm.state.first { !it.busy }
        assertNull(access.exportOptions); assertNotNull(vm.state.value.error)
        vm.export(true, true, true, "secret-password", false); vm.state.first { !it.busy }
        assertEquals("secret-password", access.exportOptions!!.password)
        assertNotNull(vm.state.value.exportFile)
        assertFalse(temp.root.walkTopDown().filter { it.isFile }.any { it.readText().contains("secret-password") })
    }
    @Test fun failedOutputSaveAndSecondExportDoNotImport() = runTest {
        val access = BackupTestAccess(); val vm = model(access); vm.state.first { !it.busy }
        vm.export(true, false, false, null, false); vm.state.first { !it.busy }
        val original = vm.state.value.exportFile!!
        vm.save(Uri.parse("file:///invalid")); vm.state.first { !it.busy }
        assertSame(original, vm.state.value.exportFile); assertTrue(original.file.exists())
        vm.save(null); assertEquals(0, access.imports)
    }
}
