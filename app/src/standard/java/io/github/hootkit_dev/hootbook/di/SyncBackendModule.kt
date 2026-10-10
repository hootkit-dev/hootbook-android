package io.github.hootkit_dev.hootbook.di

import io.github.hootkit_dev.hootbook.data.sync.SyncBackend
import io.github.hootkit_dev.hootbook.data.sync.firebase.FirebaseSyncBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Standard flavor: sync through Firebase (docs/sync.md). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBackendModule {
    @Binds
    abstract fun bindSyncBackend(backend: FirebaseSyncBackend): SyncBackend
}
