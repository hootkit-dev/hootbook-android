package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window

internal fun Context.activityWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.takeUnless { it === this }?.activityWindow()
        else -> null
    }

/** Change only our secure bit; retain an existing application lock / screenshot policy. */
internal fun protectInvestmentWindow(window: Window): () -> Unit {
    return io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindows.acquire(window)
}
