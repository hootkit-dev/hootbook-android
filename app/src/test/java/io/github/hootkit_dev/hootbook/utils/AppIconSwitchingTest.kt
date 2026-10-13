package io.github.hootkit_dev.hootbook.utils

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.MainActivity
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppIconSwitchingTest {
    @Test
    @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun themedLayersKeepTransparentDetailsAndAcceptSystemTint() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val colors = listOf(0xff31594b.toInt(), 0xff52477a.toInt(), 0xff754b1c.toInt())
        val preview = Bitmap.createBitmap(6 * 144, 3 * 144, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        colors.forEachIndexed { row, tint ->
            AppIcon.entries.forEachIndexed { column, icon ->
                val component = ComponentName(context,
                    MainActivity::class.java.name + icon.activityAliasSuffix)
                val info = context.packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                val adaptive = context.getDrawable(info.icon) as AdaptiveIconDrawable
                val mono = requireNotNull(adaptive.monochrome) as BitmapDrawable
                val pixels = mono.bitmap
                assertEquals(0, Color.alpha(pixels.getPixel(0, 0)))
                val alpha = IntArray(pixels.width * pixels.height)
                pixels.getPixels(alpha, 0, pixels.width, 0, 0, pixels.width, pixels.height)
                val opaque = alpha.count { Color.alpha(it) > 240 }
                assertTrue("Monochrome must contain the owl, not an opaque tile",
                    opaque in (alpha.size / 20)..(alpha.size / 2))
                canvas.save()
                canvas.translate(column * 144f, row * 144f)
                canvas.clipRect(0, 0, 144, 144)
                canvas.drawColor(0xffe9e5ed.toInt())
                mono.mutate().setTint(tint)
                // The system crops the outer 18dp of the 108dp adaptive layer.
                mono.setBounds(-36, -36, 180, 180)
                mono.draw(canvas)
                canvas.restore()
            }
        }
        File(System.getProperty("java.io.tmpdir"), "hootbook-themed-icons.png")
            .outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun everyChoiceResolvesAndLeavesExactlyOneLauncherEnabled() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.packageManager
        for (selected in AppIcon.entries) {
            IconSwitchingUtils.switchAppIcon(context, selected)
            for (icon in AppIcon.entries) {
                val component = ComponentName(context,
                    MainActivity::class.java.name + icon.activityAliasSuffix)
                assertEquals(
                    if (icon == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    manager.getComponentEnabledSetting(component),
                )
                val info = manager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
                assertEquals(MainActivity::class.java.name, info.targetActivity)
                assertTrue(context.getDrawable(info.icon) is AdaptiveIconDrawable)
                assertNotNull(context.getDrawable(icon.previewRes))
            }
        }
    }
}
