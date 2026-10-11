package io.github.hootkit_dev.hootbook.core.ui

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.data.preferences.AppFont
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreUiPreferencesMigrationTest {
    @Test fun unknownLegacyDisplayEnumUsesDefaultWarningWithoutLosingEnabledLockOrOriginalFile()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>();File(context.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(context.filesDir,"datastore").deleteRecursively()
        val legacy=File(context.filesDir,"datastore/user_preferences.preferences_pb").also{it.parentFile.mkdirs()}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val store=PreferenceDataStoreFactory.create(scope=scope,produceFile={legacy})
        store.edit{it[stringPreferencesKey("app_font")]="UNKNOWN_OLD_FONT";it[booleanPreferencesKey("app_lock_enabled")]=true;it[booleanPreferencesKey("has_shown_scan_tutorial")]=true}
        scope.coroutineContext[Job]!!.cancelAndJoin();val original=legacy.readBytes()
        val prefs=CoreUiPreferencesStore(context);val result=prefs.ready()
        assertEquals(AppFont.SYSTEM,result.font);assertTrue(result.appLockEnabled);assertTrue(result.onboardingFinished)
        assertTrue(prefs.migrationWarnings.value.isNotEmpty());assertArrayEquals(original,legacy.readBytes())
    }
    @Test fun corruptLegacyProtobufDoesNotGuessSecurityStateOrOverwriteSource()=runTest {
        val context=ApplicationProvider.getApplicationContext<Application>();File(context.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();val legacy=File(context.filesDir,"datastore/user_preferences.preferences_pb").also{it.parentFile.mkdirs();it.writeBytes(byteArrayOf(0xff.toByte(),0xff.toByte()))};val original=legacy.readBytes()
        val prefs=CoreUiPreferencesStore(context);try{prefs.ready();fail()}catch(_:Exception){}
        assertFalse(prefs.loaded.value);assertArrayEquals(original,legacy.readBytes())
    }
}
