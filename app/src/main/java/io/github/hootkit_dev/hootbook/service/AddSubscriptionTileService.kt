package io.github.hootkit_dev.hootbook.service
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.hootkit_dev.hootbook.widget.QuickEntry
class AddSubscriptionTileService:TileService() {
    // Intent overload is required only on API 26–33; API 34+ always takes PendingIntent below.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent=QuickEntry.intent(this,QuickEntry.Entry.NewSubscription)
        if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
    }
}
