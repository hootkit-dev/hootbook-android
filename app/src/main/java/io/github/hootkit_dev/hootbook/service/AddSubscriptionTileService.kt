package io.github.hootkit_dev.hootbook.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.hootkit_dev.hootbook.MainActivity

class AddSubscriptionTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_ADD_SUBSCRIPTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
