package io.github.hootkit_dev.hootbook.data.sync.firebase

/**
 * The Firebase project behind sync, from its google-services.json. None of these is a secret:
 * they name the project and the app; access is guarded by Firebase Auth and the Firestore rules
 * (`users/{uid}/...` readable and writable only by that uid), and the data by end-to-end
 * encryption. Kept as code rather than through the google-services plugin, which fails for
 * variants whose applicationId has no client in the json (the benchmark build).
 */
internal object FirebaseSyncConfig {
    const val APP_NAME = "cashiro-sync"
    const val PROJECT_ID = "cashiro-7a585"
    const val APPLICATION_ID = "1:382165299926:android:a690f59212d7d4bd08cf77"
    const val API_KEY = "AIzaSyBLFf93nyyuJ2XVfIayDYOD8KEFwbOEwmk"
    const val GCM_SENDER_ID = "382165299926"
    const val STORAGE_BUCKET = "cashiro-7a585.firebasestorage.app"

    /** The web OAuth client (client_type 3): Google sign-in issues ID tokens for it, which Firebase Auth accepts */
    const val WEB_CLIENT_ID = "382165299926-0gs2f6jivkmeu0ktn0r6iuj2d4qscq8b.apps.googleusercontent.com"
}
