package io.github.hootkit_dev.hootbook.data.sync.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import io.github.hootkit_dev.hootbook.data.sync.RemoteStore
import io.github.hootkit_dev.hootbook.data.sync.SignInCancelledException
import io.github.hootkit_dev.hootbook.data.sync.SyncAccount
import io.github.hootkit_dev.hootbook.data.sync.SyncBackend
import io.github.hootkit_dev.hootbook.data.sync.SyncProblem
import io.github.hootkit_dev.hootbook.data.sync.SyncRemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync through Firebase: Google sign-in with Credential Manager, then a Firebase Auth session;
 * records in Firestore under `users/{uid}`. The Firebase app is a named one built from
 * [FirebaseSyncConfig], so nothing depends on google-services resources.
 */
@Singleton
class FirebaseSyncBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncBackend {
    override val available = true
    override val projectId: String = FirebaseSyncConfig.PROJECT_ID

    private val app: FirebaseApp by lazy {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseSyncConfig.APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(FirebaseSyncConfig.PROJECT_ID)
                    .setApplicationId(FirebaseSyncConfig.APPLICATION_ID)
                    .setApiKey(FirebaseSyncConfig.API_KEY)
                    .setGcmSenderId(FirebaseSyncConfig.GCM_SENDER_ID)
                    .setStorageBucket(FirebaseSyncConfig.STORAGE_BUCKET)
                    .build(),
                FirebaseSyncConfig.APP_NAME
            )
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app) }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(app).apply {
            // The outbox is the durable queue and pulls read the server, so no disk cache: the
            // ledger is not kept a second time, decrypted or not
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    override fun currentAccount(): SyncAccount? = auth.currentUser?.let { SyncAccount(it.uid, it.email) }

    override suspend fun signIn(activityContext: Context): SyncAccount {
        val credentialManager = CredentialManager.create(activityContext)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(FirebaseSyncConfig.WEB_CLIENT_ID).build())
            .build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelledException(e)
        } catch (e: NoCredentialException) {
            // No Google account on the device (or none allowed)
            throw SyncRemoteException(SyncProblem.OTHER, e)
        } catch (e: GetCredentialException) {
            throw SyncRemoteException(SyncProblem.OTHER, e)
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SyncRemoteException(SyncProblem.OTHER, IllegalStateException("Unexpected credential ${credential.type}"))
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val user = firebaseCall { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }.user
            ?: throw SyncRemoteException(SyncProblem.PERMISSION)
        return SyncAccount(user.uid, user.email)
    }

    override suspend fun signOut() {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override fun store(account: SyncAccount): RemoteStore = FirestoreRemoteStore(firestore, account.uid)
}
