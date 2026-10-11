package io.github.hootkit_dev.hootbook

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Pure Compose/fake-API tests deliberately use no production Application.
 * Never launches HootbookApplication, its Hilt graph, Core startup or legacy sync.
 * Tests needing the real application/native engine must use a separate isolated harness.
 */
class IsolatedUiTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
