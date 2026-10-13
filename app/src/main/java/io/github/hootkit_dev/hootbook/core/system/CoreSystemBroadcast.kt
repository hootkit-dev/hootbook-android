package io.github.hootkit_dev.hootbook.core.system
import android.content.BroadcastReceiver
import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import kotlinx.coroutines.*

@EntryPoint @InstallIn(SingletonComponent::class)
interface CoreSystemEntryPoint {
    fun scheduler():CoreReminderScheduler
    fun dispatcher():CoreReminderDispatcher
    @ApplicationScope fun applicationScope():CoroutineScope
}
/** Uses the existing app-owned Core handle, bounds broadcast lifetime, always finishes. */
fun BroadcastReceiver.launchBounded(context:Context,block:suspend (CoreSystemEntryPoint)->Unit){
    if(CoreProcess.isCrashProcess(context))return
    val pending=goAsync()
    val finished=java.util.concurrent.atomic.AtomicBoolean(false)
    fun finish(){if(finished.compareAndSet(false,true))pending.finish()}
    try{
        val entry=EntryPointAccessors.fromApplication(context.applicationContext,CoreSystemEntryPoint::class.java)
        entry.applicationScope().launch(Dispatchers.IO){
            try{withTimeout(8_000){block(entry)}}catch(cancelled:CancellationException){throw cancelled}
            catch(_:Throwable){android.util.Log.w("CoreSystemBroadcast","Platform reminder deferred")}
            finally{finish()}
        }.invokeOnCompletion{finish()}
    }catch(_:Throwable){finish()}
}
