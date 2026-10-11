package io.github.hootkit_dev.hootbook.presentation.core.settings

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import io.github.hootkit_dev.hootbook.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Actual AppCompat resources and durable UI preferences, not an Android native-Core test. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "en")
class CoreLocaleControllerTest {
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private val activities = mutableListOf<ActivityController<LocaleActivity>>()
    private val preferences get() = context.getSharedPreferences(CoreLocaleController.STORE_NAME, Context.MODE_PRIVATE)
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        preferences.edit().clear().commit()
        context.deleteFile(COMPAT_LOCALE_FILE)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
    @After fun cleanup() {
        activities.forEach { it.pause().stop().destroy() }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        preferences.edit().clear().commit(); context.deleteFile(COMPAT_LOCALE_FILE)
        Dispatchers.resetMain()
    }
    private fun activity(): LocaleActivity {
        val controller = Robolectric.buildActivity(LocaleActivity::class.java).setup()
        activities += controller
        return controller.get()
    }
    @Test fun coldStartupReadsPreviouslySelectedLocaleBeforeCompatRestoresAndMigratesUiOnly() {
        // Fixture for existing official AndroidX autoStoreLocales. Production uses LocaleManagerCompat.
        context.openFileOutput(COMPAT_LOCALE_FILE, Context.MODE_PRIVATE).use {
            it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><locales application_locales=\"zh-CN\"/>".toByteArray())
        }
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        val locale = CoreLocaleController(context)
        assertEquals("zh-CN", locale.resolveStartupTag())
        assertEquals("zh-CN", preferences.getString(CoreLocaleController.SELECTION_KEY, null))
        context.deleteFile(COMPAT_LOCALE_FILE)
        assertEquals("zh-CN", CoreLocaleController(context).resolveStartupTag())
        assertEquals(setOf(CoreLocaleController.SELECTION_KEY), preferences.all.keys)
    }
    @Test fun persistedChoiceSurvivesCompatStaticEmptyAndActivityAttachUsesTranslatedResources() {
        preferences.edit().putString(CoreLocaleController.SELECTION_KEY, "zh-CN").commit()
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        assertEquals("zh-CN", CoreLocaleController(context).resolveStartupTag())
        val app = activity()
        assertEquals("zh", app.resources.configuration.locales[0].language)
        assertEquals("设置", app.getString(R.string.core_settings_title))
        assertEquals("货币与汇率", app.getString(R.string.core_currencies_title))
    }
    @Test fun englishAndSystemSelectionHaveRealResourceAndCoreTagAgreement() {
        preferences.edit().putString(CoreLocaleController.SELECTION_KEY, "en").commit()
        val app = activity()
        assertEquals("en", app.resources.configuration.locales[0].language)
        assertEquals("Settings", app.getString(R.string.core_settings_title))
        preferences.edit().putString(CoreLocaleController.SELECTION_KEY, "").commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        val startup = CoreLocaleController(context).resolveStartupTag()
        assertEquals(LocaleManagerCompat.getSystemLocales(context)[0]!!.toLanguageTag(), startup)
        assertEquals("", CoreLocaleController(context).selection())
        assertEquals("en", Locale.forLanguageTag(startup).language)
        assertEquals("Settings", activity().getString(R.string.core_settings_title))
    }
    @Test fun uiApplyFailureRestoresPersistentSelectionAndCompatLocaleForCoreRollback() = runTest {
        preferences.edit().putString(CoreLocaleController.SELECTION_KEY, "en").commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        val controller = CoreLocaleController(context) { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            if (tag == "zh-CN") error("fixture UI apply failure after mutation")
        }
        val result = runCatching { controller.apply("zh-CN") }
        assertTrue(result.isFailure)
        assertEquals("en", preferences.getString(CoreLocaleController.SELECTION_KEY, null))
        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals("en", CoreLocaleController(context).resolveStartupTag())
    }
    @Test fun successfulUiSelectionIsDurableAndExplicitSystemModeDoesNotReimportOldChoice() = runTest {
        val controller = CoreLocaleController(context)
        controller.apply("zh-CN")
        assertEquals("zh-CN", CoreLocaleController(context).resolveStartupTag())
        controller.apply("")
        assertEquals("", preferences.getString(CoreLocaleController.SELECTION_KEY, null))
        // A delayed old Compat autoStore file cannot override the explicit system-mode preference.
        context.openFileOutput(COMPAT_LOCALE_FILE, Context.MODE_PRIVATE).use {
            it.write("<locales application_locales=\"zh-CN\"/>".toByteArray())
        }
        assertEquals("", CoreLocaleController(context).selection())
        assertEquals("en", Locale.forLanguageTag(CoreLocaleController(context).resolveStartupTag()).language)
    }
    @Test @Config(sdk = [33], qualifiers = "en")
    fun api33ExternalOsLanguageSelectionOverridesStaleUiMirrorAtCoreStartup() {
        val manager = context.getSystemService(LocaleManager::class.java)
        preferences.edit().putString(CoreLocaleController.SELECTION_KEY, "zh-CN").commit()
        manager.applicationLocales = LocaleList.forLanguageTags("en-GB")
        val controller = CoreLocaleController(context)
        assertEquals("en-GB", controller.resolveStartupTag())
        assertEquals("en-GB", preferences.getString(CoreLocaleController.SELECTION_KEY, null))
        controller.applyStartupPreference()
        assertEquals("en-GB", manager.applicationLocales.toLanguageTags())
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertEquals("", controller.selection())
        assertEquals(LocaleManagerCompat.getSystemLocales(context)[0]!!.toLanguageTag(), controller.resolveStartupTag())
    }
    @Test @Config(sdk = [33], qualifiers = "en")
    fun api33ApplyUsesFrameworkEvenWithoutAnAppCompatActivity() = runTest {
        val controller = CoreLocaleController(context)
        controller.apply("zh-CN")
        assertEquals("zh-CN", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        assertEquals("zh-CN", controller.resolveStartupTag())
        controller.apply("")
        assertTrue(context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
        assertEquals("", controller.selection())
    }
    companion object { private const val COMPAT_LOCALE_FILE = "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file" }
}

/** Mirrors the integrator's early MainActivity hook without constructing the legacy Application. */
class LocaleActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        CoreLocaleController(newBase).applyStartupPreference()
        super.attachBaseContext(newBase)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }
}
