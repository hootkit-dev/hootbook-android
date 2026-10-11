package io.github.hootkit_dev.hootbook.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/** Shared owner count: overlapping destinations/lock/dialogs cannot clear each other's protection. */
object CoreSecureWindows {
    private data class Entry(val original: Boolean, var leases: Int)
    private val entries = WeakHashMap<Window, Entry>()
    @Synchronized fun acquire(window: Window): () -> Unit {
        val entry = entries.getOrPut(window) { Entry(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0, 0) }
        entry.leases++; window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var released = false
        return release@{
            synchronized(this) {
                if (released) return@release
                released = true
                val current = entries[window] ?: return@release
                current.leases--
                if (current.leases == 0) { entries.remove(window); if (!current.original) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
@Composable fun CoreSecureWindow(enabled: Boolean = true) {
    val window = coreActivity(LocalContext.current)?.window
    DisposableEffect(window, enabled) {
        val release = if (enabled && window != null) CoreSecureWindows.acquire(window) else null
        onDispose { release?.invoke() }
    }
}
internal tailrec fun coreActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> if (context.baseContext !== context) coreActivity(context.baseContext) else null
    else -> null
}
