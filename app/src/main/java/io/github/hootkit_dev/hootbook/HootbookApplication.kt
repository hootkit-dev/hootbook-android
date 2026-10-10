package io.github.hootkit_dev.hootbook

import android.app.Activity
import android.app.Application
import java.io.File
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import io.github.hootkit_dev.hootbook.data.repository.AppLockRepository
import io.github.hootkit_dev.hootbook.widget.QuickEntryPublisher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HootbookApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLockRepository: AppLockRepository

    @Inject
    lateinit var quickEntryPublisher: QuickEntryPublisher

    @Inject
    lateinit var syncManager: io.github.hootkit_dev.hootbook.data.sync.SyncManager

    @Inject
    lateinit var cloudCredentialStore: io.github.hootkit_dev.hootbook.data.cloud.security.CloudCredentialStore

    companion object {
        /** The unique name the removed CloudBackupWorker was scheduled under */
        internal const val REMOVED_CLOUD_BACKUP_WORK = "CashiroCloudBackupWorker"
    }

    // Route any unhandled coroutine exception to the CrashHandler so the crash screen
    // appears even when the crash originates inside a coroutine (which normally bypasses
    // Thread.UncaughtExceptionHandler).
    private val applicationExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("HootbookApplication", "Unhandled error in application scope", throwable)
        io.github.hootkit_dev.hootbook.utils.CrashHandler.triggerCrash(this, throwable)
    }
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + applicationExceptionHandler
    )
    private var activityReferences = 0
    private var isInForeground = false

    /**
     * Publicly accessible flag to check if the app is in the foreground.
     */
    @Volatile
    var isAppInForeground: Boolean = false
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Check if we are running in the isolated :crash process.
        // If so, we skip installing the crash handler (to avoid infinite loops)
        // and skip all background/database initialization which could fail due to multi-process locks.
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            null
        }
        if (processName != null && processName.endsWith(":crash")) {
            Log.d("HootbookApplication", "Started in :crash process. Skipping main initialization.")
            return
        }

        // Install crash handler first — must be before any other initialization
        io.github.hootkit_dev.hootbook.utils.CrashHandler.install(this)
        registerActivityLifecycleCallbacks(AppLockLifecycleObserver())
        // Exported or downloaded backups are plain copies of everything; none outlives a launch
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            listOf("backups", "cloud_restores", "peer_syncs").forEach { File(cacheDir, it).deleteRecursively() }
        }
        // Hiding accounts was removed: bring back any hidden before
        getSharedPreferences("account_prefs", MODE_PRIVATE).edit().remove("hidden_accounts").apply()
        // Webhooks were removed: drop the sync work an older version may have queued
        applicationScope.launch {
            runCatching {
                val workManager = androidx.work.WorkManager.getInstance(this@HootbookApplication)
                workManager.cancelUniqueWork("webhook_periodic_sync")
                workManager.cancelUniqueWork("webhook_one_time_sync")
                // Scheduled cloud backups and Drive/WebDAV device sync were removed (manual
                // backups and Firebase sync remain): stop a periodic job an older version queued
                workManager.cancelUniqueWork(REMOVED_CLOUD_BACKUP_WORK)
            }
        }
        applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { cloudCredentialStore.forgetRemovedSyncSettings() }
        }
        // Template launcher shortcuts and the home-screen widget follow the quick templates
        quickEntryPublisher.start()
        // Multi-device sync (docs/sync.md): pushes local changes, pulls in the foreground
        syncManager.start()
    }

    /**
     * Lifecycle observer to track app foreground/background state
     * This is used to trigger app lock when app returns from background
     */
    private inner class AppLockLifecycleObserver : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            activityReferences++
            if (!isInForeground) {
                // App came to foreground
                isInForeground = true
                isAppInForeground = true
                // Check if app should be locked when returning from background
                checkAndLockApp()
                syncManager.onForeground()
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            activityReferences--
            if (activityReferences == 0) {
                // App went to background
                isInForeground = false
                isAppInForeground = false
                syncManager.onBackground()
                // Note: We don't need to do anything here
                // The lock state will be checked when app returns to foreground
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}

        private fun checkAndLockApp() {
            applicationScope.launch {
                // The AppLockRepository will determine if app should be locked
                // based on timeout settings
                // The lock state will be observed by the AppLockViewModel
            }
        }
    }
}
