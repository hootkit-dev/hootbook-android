package io.github.hootkit_dev.hootbook.core.sync.firebase

import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.PlatformException
import dev.hootkit.core.SyncAccount
import io.github.hootkit_dev.hootbook.core.sync.*
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Singleton
class CoreFirebaseSyncPlatform @Inject constructor(@ApplicationContext private val context: Context,
    private val client: CoreFirebaseClient, private val gate: CoreSyncGate,
    private val identity: CoreSyncIdentityStore, private val remote: CoreV2RemoteStore,
    @ApplicationScope private val scope: CoroutineScope) : CoreSyncPlatform {
    override val available = true
    override val events = MutableSharedFlow<CoreAuthEvent>(replay = 1, extraBufferCapacity = 8)
    private var listener: FirebaseAuth.AuthStateListener? = null
    private var observed = false
    private var lastAccount: SyncAccount? = null
    @Synchronized private fun publishSnapshot(account: SyncAccount?, force: Boolean = false) {
        if (observed && lastAccount == account && !force) return
        observed = true; lastAccount = account
        gate.close(); remote.stopListening()
        scope.launch { events.emit(CoreAuthEvent(account)) }
    }
    @Synchronized override fun start() {
        if (listener != null) return
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            try {
                // Close immediately on the SDK callback, before waiting for the Core round lock.
                val snapshot = auth.currentUser?.let { SyncAccount(it.uid, it.email) }
                publishSnapshot(snapshot)
            } catch (_: Throwable) { }
        }
        client.auth.addAuthStateListener(authListener); listener = authListener
    }
    override fun stopRemoteListening() = remote.stopListening()
    override suspend fun acceptIdentity(account: SyncAccount) = identity.accept(account.uid)
    override suspend fun signIn(activity: Context) {
        try {
            val credential = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(CoreFirebaseConfig.WEB_CLIENT_ID).build()).build()).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw PlatformException.Other("Unexpected sign-in credential")
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val user = withTimeout(60_000) { client.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitCoreFirebase() }.user
                ?: throw PlatformException.Unauthorized()
            // Same-UID sign-in may not fire AuthStateListener; explicitly retry a closed handoff.
            publishSnapshot(SyncAccount(user.uid, user.email), force = runCatching { gate.lease() }.isFailure)
        } catch (_: GetCredentialCancellationException) { throw CoreSignInCancelled() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: PlatformException) { throw failure }
        catch (_: Throwable) { throw PlatformException.Other("Google sign-in failed") }
    }
    override suspend fun signOut() {
        gate.close(); remote.stopListening(); client.auth.signOut(); publishSnapshot(null, force = true)
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { /* Firebase is already signed out; no plaintext or token fallback. */ }
    }
}
