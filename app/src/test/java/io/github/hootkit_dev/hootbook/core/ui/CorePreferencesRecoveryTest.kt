package io.github.hootkit_dev.hootbook.core.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CorePreferencesRecoveryTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun reset(){File(context.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(context.filesDir,"datastore").deleteRecursively()}
    @Test fun unreadableModernUiPrefsFailClosedWithoutPublishingDefaultDisabledLock()=runTest {
        reset(); val file=File(context.noBackupFilesDir,"hootkit-core/ui/preferences.json").apply{parentFile.mkdirs();writeText("broken")}
        val prefs=CoreUiPreferencesStore(context)
        try{prefs.ready();fail()}catch(_:Exception){}
        assertFalse(prefs.loaded.value);assertEquals("broken",file.readText())
    }
    @Test fun explicitAuthenticatedUiRecoveryKeepsOriginalAndUnknownLockEnabledWithoutTouchingLedger()=runTest {
        reset();val root=File(context.noBackupFilesDir,"hootkit-core/ui");root.mkdirs();File(root,"preferences.json").writeText("broken")
        val ledger=File(context.cacheDir,"not-a-real-ledger").apply{writeText("ledger unchanged")}
        val prefs=CoreUiPreferencesStore(context); val recovered=prefs.recoverUi(CoreUiRecoveryTicket())
        assertTrue(recovered.appLockEnabled);assertTrue(prefs.loaded.value);assertEquals("ledger unchanged",ledger.readText())
        assertTrue(root.walkTopDown().any{it.isFile&&it.name=="preferences.json"&&it.readText()=="broken"})
    }
    @Test fun knownEnabledLockSurvivesUnknownModernDisplayEnumAndRecovery()=runTest {
        reset();val root=File(context.noBackupFilesDir,"hootkit-core/ui").also{it.mkdirs()};val json=CoreUiPreferencesCodec.encode(CoreUiPreferences(appLockEnabled=true))
        json.getJSONObject("theme").put("font","unsupported-font");File(root,"preferences.json").writeText(json.toString())
        val prefs=CoreUiPreferencesStore(context);try{prefs.ready();fail()}catch(_:Exception){}
        assertTrue(prefs.recoverUi(CoreUiRecoveryTicket()).appLockEnabled)
    }
    @Test fun recoveryTicketIsMemoryOnlyAndSingleUse()=runTest {
        reset();val ticket=CoreUiRecoveryTicket();val prefs=CoreUiPreferencesStore(context);prefs.recoverUi(ticket)
        try{prefs.recoverUi(ticket);fail()}catch(_:IllegalStateException){}
        assertFalse(File(context.noBackupFilesDir,"hootkit-core/ui/preferences.json").readText().contains("authentication"))
    }
    @Test fun missingModernLockPolicyDoesNotSilentlyInterpretAbsentAsDisabled()=runTest {
        reset();val root=File(context.noBackupFilesDir,"hootkit-core/ui").also{it.mkdirs()};val json=CoreUiPreferencesCodec.encode(CoreUiPreferences()).apply{remove("lock")}
        File(root,"preferences.json").writeText(json.toString());val prefs=CoreUiPreferencesStore(context)
        try{prefs.ready();fail()}catch(_:Exception){};assertFalse(prefs.loaded.value)
    }
}
