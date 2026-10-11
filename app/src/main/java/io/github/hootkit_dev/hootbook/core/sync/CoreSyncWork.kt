package io.github.hootkit_dev.hootbook.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface CoreSyncScheduler {
    fun schedule(afterMs: Long)
    fun cancel()
    fun started()
}
/** Network-constrained unique chain; Core controls delay. No immediate/busy SDK retry loop. */
@Singleton
class CoreSyncWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) : CoreSyncScheduler {
    private var scheduledDelay: Long? = null
    @Synchronized override fun schedule(afterMs: Long) {
        val delay = afterMs.coerceAtLeast(1_000)
        if (scheduledDelay == delay) return
        val request = coreSyncWorkRequest(delay)
        // A failure may schedule while the current worker is completing. APPEND_OR_REPLACE
        // queues the next round without cancelling the in-flight Core call or losing KEEP enqueue.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        scheduledDelay = delay
    }
    @Synchronized override fun cancel() { scheduledDelay = null; WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    @Synchronized override fun started() { scheduledDelay = null }
    companion object { const val WORK_NAME = "hootkit-core-v2-sync" }
}
@HiltWorker
class CoreSyncWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val coordinator: CoreSyncCoordinator, private val scheduler: CoreSyncWorkScheduler) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        scheduler.started()
        return try { coordinator.backgroundRound(); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { Result.failure() } // Never invent a second retry schedule outside Core status.
    }
}

/** Request factory permits verifying constraints/delay without starting a device or Worker. */
internal fun coreSyncWorkRequest(afterMs: Long): OneTimeWorkRequest = OneTimeWorkRequestBuilder<CoreSyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setInitialDelay(afterMs.coerceAtLeast(1_000), TimeUnit.MILLISECONDS)
    .addTag(CoreSyncWorkScheduler.WORK_NAME).build()
