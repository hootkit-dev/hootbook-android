package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.BrokerageConnection
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages daily automatic brokerage report refresh (Spec 10 §4.3: platform-driven refresh once per day
 * or when a synced connection with credentials has not yet fetched its first snapshot).
 */
@Singleton
class BrokerDailySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(prefs.getBoolean(KEY_DAILY_AUTO_SYNC, true))
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    // Tracks (connectionId -> LocalDate) already auto-attempted in this process to avoid retry loops on failure
    private val attemptedToday = ConcurrentHashMap<String, LocalDate>()

    init {
        syncSchedule(mutableEnabled.value)
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_AUTO_SYNC, value).apply()
        mutableEnabled.value = value
        syncSchedule(value)
    }

    fun shouldAutoRefreshNow(connection: BrokerageConnection, today: LocalDate = LocalDate.now()): Boolean {
        if (!connection.hasCredentials) return false
        if (attemptedToday[connection.id] == today) return false
        // Always auto-fetch on first open when a connection has credentials but no snapshot yet
        if (connection.syncedAt == null) return true
        if (!mutableEnabled.value) return false
        return isConnectionDueToday(connection, today)
    }

    fun markAttempted(connectionId: String, today: LocalDate = LocalDate.now()) {
        attemptedToday[connectionId] = today
    }

    fun isConnectionDueToday(connection: BrokerageConnection, today: LocalDate = LocalDate.now()): Boolean {
        if (!connection.hasCredentials) return false
        val synced = connection.syncedAt ?: return true
        return try {
            val instant = LocalDateTime.of(
                synced.year,
                synced.month.toInt(),
                synced.day.toInt(),
                synced.hour.toInt(),
                synced.minute.toInt(),
                synced.second.toInt(),
            ).toInstant(ZoneOffset.UTC)
            val syncedDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            syncedDate.isBefore(today)
        } catch (_: Exception) {
            false
        }
    }

    private fun syncSchedule(enable: Boolean) {
        try {
            val workManager = WorkManager.getInstance(context)
            if (enable) {
                val request = PeriodicWorkRequestBuilder<BrokerDailySyncWorker>(24, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .addTag(WORK_NAME)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
            }
        } catch (_: Throwable) {
            // WorkManager may not be initialized in unit test environments
        }
    }

    companion object {
        private const val PREFS_NAME = "broker_auto_sync"
        private const val KEY_DAILY_AUTO_SYNC = "daily_auto_sync_enabled"
        const val WORK_NAME = "hootbook-broker-daily-sync"
    }
}

@HiltWorker
class BrokerDailySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: CoreSession,
    private val manager: BrokerDailySyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!manager.enabled.value) return Result.success()
        return try {
            session.withCore { core ->
                core.brokerage().use { api ->
                    val due = api.connections().filter { manager.isConnectionDueToday(it) }
                    for (conn in due) {
                        manager.markAttempted(conn.id)
                        try {
                            api.refresh(conn.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Continue refreshing remaining connections; errors are surfaced on manual refresh
                        }
                    }
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
