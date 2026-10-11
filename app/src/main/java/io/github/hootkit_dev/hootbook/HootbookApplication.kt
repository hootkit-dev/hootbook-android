package io.github.hootkit_dev.hootbook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.hootkit_dev.hootbook.core.CoreAppBootstrap
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.utils.CrashHandler
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class HootbookApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: Provider<HiltWorkerFactory>
    @Inject lateinit var bootstrap: Provider<CoreAppBootstrap>
    @Volatile var isAppInForeground: Boolean = false; private set
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workerFactory.get()).build()
    override fun onCreate() {
        super.onCreate()
        if (CoreProcess.isCrashProcess(this)) return
        CrashHandler.install(this)
        bootstrap.get().start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { isAppInForeground = true; bootstrap.get().foregroundChanged(true) }
                Lifecycle.Event.ON_STOP -> { isAppInForeground = false; bootstrap.get().foregroundChanged(false) }
                else -> Unit
            }
        })
    }
}
