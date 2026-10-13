package io.github.hootkit_dev.hootbook.core.sync.firebase

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CoreFirebaseClient @Inject constructor(@ApplicationContext private val context: Context) {
    private val acceptance = io.github.hootkit_dev.hootbook.BuildConfig.DEBUG && io.github.hootkit_dev.hootbook.BuildConfig.CORE_ACCEPTANCE
    private val appName get() = if (acceptance) "hootkit-core-isolated-acceptance" else CoreFirebaseConfig.APP_NAME
    private val app by lazy {
        FirebaseApp.getApps(context).firstOrNull { it.name == appName } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId(if (acceptance) "demo-hootkit-core" else CoreFirebaseConfig.PROJECT_ID).setApplicationId(if (acceptance) "1:1234567890:android:0000000000000000" else CoreFirebaseConfig.APPLICATION_ID)
                .setApiKey(if (acceptance) "AIza" + "0".repeat(35) else CoreFirebaseConfig.API_KEY).setGcmSenderId(if (acceptance) "1234567890" else CoreFirebaseConfig.GCM_SENDER_ID).setStorageBucket(if (acceptance) "demo-hootkit-core.invalid" else CoreFirebaseConfig.STORAGE_BUCKET).build(), appName)
    }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { if (acceptance) useEmulator("10.0.2.2", 9099) } }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(app).apply {
        if (acceptance) useEmulator("10.0.2.2", 8080)
        firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    } }
}
/** SDK tasks have no transaction cancellation handle: cancelled result is discarded; fixed UID remains. */
internal suspend fun <T> Task<T>.awaitCoreFirebase(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val error = task.exception
        if (error != null) continuation.resumeWithException(error)
        else if (task.isCanceled) continuation.resumeWithException(CancellationException("Firebase task cancelled"))
        else continuation.resume(task.result)
    }
}
