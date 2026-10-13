package io.github.hootkit_dev.hootbook.core.ui

import android.app.Application
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class AppIconTest {
    @Test fun oldLocalPreferencesAndBackupsKeepTheirLauncherComponent() {
        val aliases = mapOf(
            "ORIGINAL" to "Original", "ANARCHY" to "Anarchy", "ZENITH" to "Zenith",
            "COMIC" to "Comic", "MONOCHROME" to "Monochrome",
        )
        for ((old, alias) in aliases) {
            val current = CoreUiPreferences(appLockEnabled = true, userName = "Existing user")
            val local = CoreUiPreferencesCodec.encode(current).apply {
                getJSONObject("theme").put("icon", old)
            }
            val restored = CoreUiPreferencesCodec.decode(local.toString(), current)
            assertEquals(alias, restored.appIcon.activityAliasSuffix)
            assertTrue(restored.appLockEnabled)
            assertEquals(current.userName, restored.userName)
            val backup = JSONObject().put("theme", JSONObject().put("app_icon", old))
            assertEquals(restored.appIcon,
                CoreUiPreferencesCodec.decode(backup.toString(), current, legacy = true).appIcon)
            assertTrue(old in AppIcon.storedNames)
        }
    }

    @Test fun everyBrandChoiceSurvivesSaveAndRestore() {
        assertEquals(AppIcon.HOOTBOOK_WHITE, CoreUiPreferences().appIcon)
        assertEquals(6, AppIcon.entries.size)
        assertEquals(6, AppIcon.entries.map { it.activityAliasSuffix }.distinct().size)
        for (icon in AppIcon.entries) {
            val prefs = CoreUiPreferences(appIcon = icon)
            assertEquals(prefs, CoreUiPreferencesCodec.decode(
                CoreUiPreferencesCodec.encode(prefs).toString(), CoreUiPreferences()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidStoredIconStillFailsValidation() {
        AppIcon.fromStoredName("UNKNOWN_ICON")
    }
}
