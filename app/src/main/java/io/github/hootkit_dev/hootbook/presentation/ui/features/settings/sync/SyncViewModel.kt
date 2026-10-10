package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.sync.SignInCancelledException
import io.github.hootkit_dev.hootbook.data.sync.SyncManager
import io.github.hootkit_dev.hootbook.data.sync.WrongPassphraseException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Sync screen reports once, as a snackbar. */
sealed class SyncMessage {
    data object Synced : SyncMessage()
    data object Failed : SyncMessage()
    /** Sign-in did not finish; [detail] is the error's type and message */
    data class SignInFailed(val detail: String) : SyncMessage()
    /** The account sheet was closed without choosing an account */
    data object SignInCancelled : SyncMessage()
    data object Merged : SyncMessage()
    data class Replaced(val backupName: String) : SyncMessage()
}

@HiltViewModel
class SyncViewModel @Inject constructor(private val manager: SyncManager) : ViewModel() {
    val state: StateFlow<SyncManager.State> = manager.state

    /** False in a build without sync (F-Droid). */
    val available: Boolean = manager.available

    private val _messages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<SyncMessage> = _messages.asSharedFlow()

    private val _wrongPassphrase = MutableStateFlow(false)
    val wrongPassphrase: StateFlow<Boolean> = _wrongPassphrase.asStateFlow()

    init {
        refresh()
    }

    /** Looks up again whether the account has a passphrase (after a failure, e.g. offline). */
    fun refresh() = viewModelScope.launch { manager.refreshPassphraseState() }

    fun signIn(activityContext: Context) = viewModelScope.launch {
        try {
            manager.signIn(activityContext)
        } catch (e: SignInCancelledException) {
            // Said too: a misconfigured client can also end as "cancelled", so it is never silent
            Log.i(TAG, "Sign-in cancelled", e)
            _messages.tryEmit(SyncMessage.SignInCancelled)
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed", e)
            _messages.tryEmit(SyncMessage.SignInFailed(SyncManager.describe(e)))
        }
    }

    fun submitPassphrase(passphrase: String) = viewModelScope.launch {
        _wrongPassphrase.value = false
        try {
            manager.submitPassphrase(passphrase)
        } catch (e: WrongPassphraseException) {
            _wrongPassphrase.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Setting up sync failed", e)
            _messages.tryEmit(SyncMessage.Failed)
        }
    }

    fun clearWrongPassphrase() { _wrongPassphrase.value = false }

    fun merge() = run(SyncMessage.Merged) { manager.merge() }

    fun replaceLocal() = viewModelScope.launch {
        try {
            _messages.tryEmit(SyncMessage.Replaced(manager.replaceLocalWithCloud()))
        } catch (e: Exception) {
            Log.w(TAG, "Replacing failed", e)
            _messages.tryEmit(SyncMessage.Failed)
        }
    }

    fun syncNow() = viewModelScope.launch {
        _messages.tryEmit(if (manager.syncNow()) SyncMessage.Synced else SyncMessage.Failed)
    }

    fun signOut() = viewModelScope.launch { manager.signOut() }

    /** The on/off switch: off pauses sync and keeps the account and key. */
    fun setEnabled(enabled: Boolean) = manager.setPaused(!enabled)

    private fun run(success: SyncMessage, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            _messages.tryEmit(success)
        } catch (e: Exception) {
            Log.w(TAG, "Sync step failed", e)
            _messages.tryEmit(SyncMessage.Failed)
        }
    }

    private companion object {
        const val TAG = "SyncViewModel"
    }
}
