package io.github.hootkit_dev.hootbook.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sync switch and the last error of the Sync page's debug section. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `pausing keeps the account and its sync state`() {
        val settings = SyncSettings(context)
        settings.account = SyncAccount("uid-1", "me@example.com")
        settings.enabled = true
        settings.cursor = RemoteCursor(RemoteTime(10, 5), "transactions_00")
        settings.paused = true

        val reread = SyncSettings(context)
        assertTrue(reread.paused)
        assertEquals("uid-1", reread.account?.uid)
        assertTrue(reread.enabled)
        assertEquals("transactions_00", reread.cursor?.docId)

        reread.paused = false
        assertFalse(SyncSettings(context).paused)
    }

    @Test
    fun `signing out forgets the pause and the last error`() {
        val settings = SyncSettings(context)
        settings.account = SyncAccount("uid-1", null)
        settings.paused = true
        settings.lastError = "IOException: offline"
        settings.clearAccount()
        assertFalse(settings.paused)
        assertNull(settings.lastError)
    }

    @Test
    fun `errors are described by type, message and cause`() {
        val error = SyncRemoteException(SyncProblem.NETWORK, IOException("timeout"))
        assertEquals("SyncRemoteException: NETWORK ← IOException: timeout", SyncManager.describe(error))
        assertEquals("IllegalStateException", SyncManager.describe(IllegalStateException()))
    }
}
