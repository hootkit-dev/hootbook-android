package io.github.hootkit_dev.hootbook.core.system
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.hootkit_dev.hootbook.core.CoreProcess
import kotlinx.coroutines.CancellationException
@HiltWorker
class CoreReminderWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,
    private val dispatcher:CoreReminderDispatcher):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        if(CoreProcess.isCrashProcess(applicationContext))return Result.success()
        return try{dispatcher.deliver(requireScheduledTime=true);Result.success()}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){if(runAttemptCount<2)Result.retry() else Result.failure()}
    }
}
