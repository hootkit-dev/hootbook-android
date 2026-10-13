package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import android.app.Application
import dev.hootkit.core.*
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MerchantIconViewModelTest : CoreViewModelTest() {
    @Test
    fun merchantIconSearchLatestWinsAndDownloadPreventsDuplicateCommands() = runTest {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var downloads = 0
        val png =
            java.io
                .ByteArrayOutputStream()
                .also {
                    android.graphics.Bitmap.createBitmap(
                            2,
                            2,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                .toByteArray()
        val client =
            io.ktor.client.HttpClient(
                io.ktor.client.engine.mock.MockEngine { request ->
                    val term = request.url.parameters["term"]
                    if (term == "old")
                        withContext(NonCancellable) {
                            started.complete(Unit)
                            gate.await()
                        }
                    if (term == "fail") respond("", io.ktor.http.HttpStatusCode.ServiceUnavailable)
                    else if (term != null)
                        respond(
                            """{"results":[{"trackName":"$term","artworkUrl512":"https://icon.test/icon.png"}]}"""
                        )
                    else {
                        downloads++
                        respond(
                            png,
                            io.ktor.http.HttpStatusCode.OK,
                            io.ktor.http.headersOf("Content-Type", "image/png"),
                        )
                    }
                }
            )
        try {
            val assets = io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets(context)
            val vm =
                keep(
                    MerchantIconViewModel(
                        assets,
                        io.github.hootkit_dev.hootbook.data.icons.AppStoreIconSearch(client),
                    )
                )
            assertFalse(vm.state.value.loading)
            vm.search("old")
            awaitState { started.isCompleted }
            vm.search("new")
            awaitState { vm.state.value.results?.singleOrNull()?.name == "new" }
            gate.complete(Unit)
            vm.search("fail")
            awaitState { vm.state.value.error != null }
            assertEquals(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed, vm.state.value.error)
            val candidate =
                io.github.hootkit_dev.hootbook.data.icons.AppIconCandidate(
                    "Coffee",
                    "",
                    "https://icon.test/icon.png",
                    "https://icon.test/icon.png",
                )
            var completed = 0
            vm.choose("Coffee", candidate) { completed++ }
            vm.choose("Coffee", candidate) { completed++ }
            awaitState { vm.state.value.saving == null }
            assertEquals(1, completed)
            assertEquals(1, downloads)
            assertNotNull(assets.merchantIcon("Coffee"))
            vm.reset("Coffee")
            awaitState { assets.merchantIcon("Coffee") == null }
        } finally {
            client.close()
        }
    }
}
