package io.github.hootkit_dev.hootbook.core.sync

import android.content.Context
import dev.hootkit.core.SyncAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

data class CoreAuthEvent(val account: SyncAccount?)
interface CoreSyncPlatform {
    val available: Boolean
    val events: Flow<CoreAuthEvent>
    fun start()
    fun stopRemoteListening()
    suspend fun acceptIdentity(account: SyncAccount): CoreIdentityDecision
    suspend fun signIn(activity: Context)
    suspend fun signOut()
}
class CoreSignInCancelled : Exception("Sign-in cancelled")
class CoreOfflineSyncPlatform : CoreSyncPlatform {
    override val available = false
    override val events = MutableSharedFlow<CoreAuthEvent>()
    override fun start() = Unit
    override fun stopRemoteListening() = Unit
    override suspend fun acceptIdentity(account: SyncAccount) = CoreIdentityDecision.MISSING_IDENTITY_BLOCKED
    override suspend fun signIn(activity: Context) = Unit
    override suspend fun signOut() = Unit
}
