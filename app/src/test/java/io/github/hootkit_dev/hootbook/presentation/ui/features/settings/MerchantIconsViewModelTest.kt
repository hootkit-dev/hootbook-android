package io.github.hootkit_dev.hootbook.presentation.ui.features.settings

import android.graphics.Bitmap
import android.net.Uri
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.MerchantIconsViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.CoreViewModelTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MerchantIconsViewModelTest : CoreViewModelTest() {
    @Test fun imageImportFailureRetryDuplicateGuardAndRemoveUsePlatformAssets() = runTest {
        val assets = CorePlatformAssets(context); val vm = keep(MerchantIconsViewModel(assets))
        assertFalse(vm.busy.value); assertNull(vm.error.value)
        vm.pick("", Uri.parse("content://test/empty")); assertFalse(vm.busy.value)
        val uri = Uri.parse("content://test-provider/icon")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("bad".toByteArray()))
        vm.pick("Store", uri); vm.pick("Duplicate", uri); awaitState { !vm.busy.value }
        assertNotNull(vm.error.value); assertNull(assets.merchantIcon("Store")); assertNull(assets.merchantIcon("Duplicate"))
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(png))
        vm.pick("Store", uri); vm.pick("Duplicate", uri); awaitState { !vm.busy.value }
        assertNotNull(assets.merchantIcon("store")); assertNull(assets.merchantIcon("Duplicate")); assertNull(vm.error.value)
        vm.remove("Store"); awaitState { !vm.busy.value }; assertNull(assets.merchantIcon("Store"))
    }
}
