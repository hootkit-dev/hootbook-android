package io.github.hootkit_dev.hootbook.di

import android.content.Context
import android.util.Log
import io.github.hootkit_dev.hootbook.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.hootkit.platform.AndroidCoreFactory
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import dev.hootkit.platform.KeystoreSecureStore
import dev.hootkit.platform.LogcatLogger
import dev.hootkit.platform.OkHttpTransport
import io.github.hootkit_dev.hootbook.core.CoreCapabilities
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.core.CoreStartupCoordinator
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object CoreHostModule {
    @Provides @Singleton
    fun provideChangeBus(): ChangeBus = ChangeBus { Log.e("HootKitCore", "Change notification failed", it) }

    @Provides @Singleton
    fun provideSecureStore(@ApplicationContext context: Context): KeystoreSecureStore = KeystoreSecureStore(context)

    @Provides @Singleton
    fun provideHttpTransport(): OkHttpTransport = OkHttpTransport()

    @Provides @Singleton
    fun provideLogger(): LogcatLogger = LogcatLogger()

    @Provides @Singleton
    fun provideCoreFactory(
        @ApplicationContext context: Context,
        changes: ChangeBus,
        secureStore: KeystoreSecureStore,
        http: OkHttpTransport,
        logger: LogcatLogger,
    ): AndroidCoreFactory = AndroidCoreFactory(context, changes, secureStore, http, logger)

    @Provides @Singleton
    fun provideCoreSession(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        factory: AndroidCoreFactory,
        capabilities: CoreCapabilities,
        locale: CoreLocaleController,
    ): CoreSession {
        check(!CoreProcess.isCrashProcess(context)) { "Core is disabled in the crash process" }
        // Constructing the binding is cheap. Only awaitCore opens native code, on the factory's IO dispatcher.
        return CoreSession(scope) {
            // Resolves the durable UI-only selection before Activity/AppCompat async locale restoration.
            factory.open(locale.resolveStartupTag(), capabilities.auth, capabilities.remote)
        }
    }

    @Provides @Singleton
    fun provideStartupCoordinator(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        session: Provider<CoreSession>,
    ): CoreStartupCoordinator = CoreStartupCoordinator(
        scope = scope,
        session = session::get,
        isCrashProcess = { CoreProcess.isCrashProcess(context) },
        reportFailure = { Log.e("HootKitCore", "Core startup failed", it) },
    )
}
