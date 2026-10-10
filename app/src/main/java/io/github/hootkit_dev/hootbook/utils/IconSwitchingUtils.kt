package io.github.hootkit_dev.hootbook.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import io.github.hootkit_dev.hootbook.MainActivity

object IconSwitchingUtils {
    fun switchAppIcon(context: Context, targetIcon: AppIcon) {
        val packageManager = context.packageManager
        // Component class names follow the namespace, not the suffixed application ID.
        val activityName = MainActivity::class.java.name

        val iconComponents = mapOf(
            AppIcon.ORIGINAL to "${activityName}Original",
            AppIcon.ANARCHY to "${activityName}Anarchy",
            AppIcon.ZENITH to "${activityName}Zenith",
            AppIcon.MONOCHROME to "${activityName}Monochrome",
            AppIcon.COMIC to "${activityName}Comic"
        )

        iconComponents.forEach { (icon, componentName) ->
            val component = ComponentName(context, componentName)
            val newState = if (icon == targetIcon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            
            // Only update if state is different to avoid system overhead and potential lag
            if (packageManager.getComponentEnabledSetting(component) != newState) {
                packageManager.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
