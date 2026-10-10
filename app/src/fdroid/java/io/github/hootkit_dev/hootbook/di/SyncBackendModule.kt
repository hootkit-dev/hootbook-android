package io.github.hootkit_dev.hootbook.di

import android.content.Context
import io.github.hootkit_dev.hootbook.data.sync.RemoteStore
import io.github.hootkit_dev.hootbook.data.sync.SyncAccount
import io.github.hootkit_dev.hootbook.data.sync.SyncBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor: no Firebase or Play Services, so no sync. The Sync screen says it is
 * unavailable in this build.
 */
@Singleton
class UnavailableSyncBackend @Inject constructor() : SyncBackend {
    override val available = false
    override fun currentAccount(): SyncAccount? = null
    override suspend fun signIn(activityContext: Context): SyncAccount = throw UnsupportedOperationException("No sync in this build")
    override suspend fun signOut() = Unit
    override fun store(account: SyncAccount): RemoteStore = throw UnsupportedOperationException("No sync in this build")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBackendModule {
    @Binds
    abstract fun bindSyncBackend(backend: UnavailableSyncBackend): SyncBackend
}
