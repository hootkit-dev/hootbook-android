package io.github.hootkit_dev.hootbook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.*
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.presentation.core.navigation.CoreAppRoot
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        const val ACTION_ADD_TRANSACTION = QuickEntry.ACTION_TRANSACTION
        const val ACTION_ADD_SUBSCRIPTION = QuickEntry.ACTION_SUBSCRIPTION
        const val ACTION_ADD_TRANSFER = QuickEntry.ACTION_TRANSFER
        const val EXTRA_TEMPLATE_ID = "io.github.hootkit_dev.hootbook.extra.TEMPLATE_ID"
    }
    @Inject lateinit var bootstrap: CoreAppBootstrap
    @Inject lateinit var coreSession: CoreSession
    @Inject lateinit var capabilities: CoreCapabilities
    @Inject lateinit var preferences: CoreUiPreferencesStore
    @Inject lateinit var lock: CoreAppLockController
    @Inject lateinit var ingress: CoreIntentIngress
    override fun attachBaseContext(newBase: Context) {
        try { CoreLocaleController(newBase).applyStartupPreference() } catch (_: Exception) { /* Ready locale gate owns explicit retry. */ }
        super.attachBaseContext(newBase)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen(); super.onCreate(savedInstanceState)
        enableEdgeToEdge(); requestHighestRefreshRate(); bootstrap.start()
        if (savedInstanceState == null) ingress.accept(this, intent)
        setContent { CoreAppRoot(bootstrap, coreSession, capabilities, preferences, lock, ingress) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); ingress.accept(this, intent) }
    private fun requestHighestRefreshRate() {
        val screen = if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = screen?.mode ?: return
        val best = screen.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != current.modeId) window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

}
