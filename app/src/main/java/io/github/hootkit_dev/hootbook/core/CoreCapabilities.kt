package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.AuthProvider
import dev.hootkit.core.RemoteStore

enum class CoreSyncAvailability {
    STANDARD_ADAPTER_PENDING,
    AVAILABLE,
}

/** Flavor-owned capabilities; the old SyncBackend is never a Core RemoteStore. */
data class CoreCapabilities(
    val syncAvailability: CoreSyncAvailability,
    val auth: AuthProvider? = null,
    val remote: RemoteStore? = null,
) {
    init {
        require((auth == null) == (remote == null)) { "Auth and remote must be supplied together" }
        require((syncAvailability == CoreSyncAvailability.AVAILABLE) == (auth != null)) {
            "Sync availability must match real adapters"
        }
    }
}
