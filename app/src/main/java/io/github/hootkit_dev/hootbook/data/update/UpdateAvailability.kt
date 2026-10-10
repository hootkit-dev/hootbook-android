package io.github.hootkit_dev.hootbook.data.update

object UpdateAvailability {
    fun shouldPrompt(
        remoteCommitCount: Int,
        localCommitCount: Int,
        dismissedCommitCount: Int
    ): Boolean {
        if (remoteCommitCount <= 0) return false
        if (remoteCommitCount <= localCommitCount) return false
        if (remoteCommitCount <= dismissedCommitCount) return false
        return true
    }
}
