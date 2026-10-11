package io.github.hootkit_dev.hootbook.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.CoreCapabilities
import io.github.hootkit_dev.hootbook.core.CoreSyncAvailability
import io.github.hootkit_dev.hootbook.core.sync.*
import io.github.hootkit_dev.hootbook.core.sync.firebase.*
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object CoreCapabilitiesModule {
    @Provides @Singleton fun provideCoreRemote(backend: CoreFirebaseV2Backend, gate: CoreSyncGate,
        @ApplicationScope scope: CoroutineScope, session: Provider<CoreSession>): CoreV2RemoteStore =
        CoreV2RemoteStore(backend, gate) {
            scope.launch { try { session.get().awaitCore().sync().use { it.remoteChanged() } } catch (_: Throwable) { } }
        }
    @Provides @Singleton fun provideCoreCapabilities(gate: CoreSyncGate, remote: CoreV2RemoteStore,
        @Suppress("UNUSED_PARAMETER") identity: CoreSyncIdentityStore): CoreCapabilities =
        // Construct identity store before CoreSession.open captures whether this is a fresh Core DB.
        CoreCapabilities(CoreSyncAvailability.AVAILABLE, gate, remote)
    @Provides @Singleton fun provideCoreSyncPlatform(platform: CoreFirebaseSyncPlatform): CoreSyncPlatform = platform
}
