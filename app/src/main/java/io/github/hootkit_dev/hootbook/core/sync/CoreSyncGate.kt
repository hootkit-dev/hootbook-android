package io.github.hootkit_dev.hootbook.core.sync

import dev.hootkit.core.AuthProvider
import dev.hootkit.core.PlatformException
import dev.hootkit.core.SyncAccount
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class CoreRemoteLease(val uid: String, val epoch: Long)
/** Stable platform snapshot. A request captures a UID once; never reads Firebase currentUser. */
@Singleton
class CoreSyncGate @Inject constructor() : AuthProvider {
    private data class Snapshot(val account: SyncAccount?, val open: Boolean, val epoch: Long)
    private val state = AtomicReference(Snapshot(null, false, 0))
    override fun currentAccount(): SyncAccount? = state.get().account
    fun close() { state.updateAndGet { it.copy(open = false, epoch = it.epoch + 1) } }
    fun publish(account: SyncAccount?) {
        account?.let { require(it.uid.isNotBlank() && '/' !in it.uid) { "Invalid account ID" } }
        state.updateAndGet { check(!it.open) { "Close remote gate before publishing account" }; Snapshot(account, false, it.epoch + 1) }
    }
    fun open() { state.updateAndGet { check(it.account != null) { "No bound account" }; it.copy(open = true) } }
    fun lease(): CoreRemoteLease {
        val snapshot = state.get(); val account = snapshot.account
        if (!snapshot.open || account == null) throw PlatformException.Unauthorized()
        return CoreRemoteLease(account.uid, snapshot.epoch)
    }
    fun accepts(lease: CoreRemoteLease): Boolean {
        val snapshot = state.get(); return snapshot.open && snapshot.epoch == lease.epoch && snapshot.account?.uid == lease.uid
    }
}
