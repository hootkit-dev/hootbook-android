package io.github.hootkit_dev.hootbook.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What sync keeps on this device: the signed-in account, the key derived from the passphrase
 * (encrypted preferences; never the passphrase itself), whether the first sync is done, the pull
 * cursor and the last result. None of it is in backups.
 */
@Singleton
class SyncSettings @Inject constructor(@ApplicationContext private val context: Context) : SyncCursorStore {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("cashiro_sync", Context.MODE_PRIVATE) }

    // Without the keystore the key lives only until the app closes, and the passphrase is asked again
    private var memoryKey: ByteArray? = null
    private val secure: SharedPreferences? by lazy {
        try {
            EncryptedSharedPreferences.create(
                "cashiro_sync_secure_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    /** This installation's id, written to each record as `deviceId`. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit { putString(KEY_DEVICE, it) }
        }

    var account: SyncAccount?
        get() = prefs.getString(KEY_UID, null)?.let { SyncAccount(it, prefs.getString(KEY_EMAIL, null)) }
        set(value) = prefs.edit {
            if (value == null) { remove(KEY_UID); remove(KEY_EMAIL) } else { putString(KEY_UID, value.uid); putString(KEY_EMAIL, value.email) }
        }

    var key: ByteArray?
        get() = secure?.getString(KEY_KEY, null)?.let { Base64.getDecoder().decode(it) } ?: memoryKey
        set(value) {
            memoryKey = value
            secure?.edit {
                if (value == null) remove(KEY_KEY) else putString(KEY_KEY, Base64.getEncoder().encodeToString(value))
            }
        }

    /** True once the first sync of this account has run: changes then flow both ways. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    override var cursor: RemoteCursor?
        get() {
            val doc = prefs.getString(KEY_CURSOR_DOC, null) ?: return null
            return RemoteCursor(RemoteTime(prefs.getLong(KEY_CURSOR_SECONDS, 0), prefs.getInt(KEY_CURSOR_NANOS, 0)), doc)
        }
        set(value) = prefs.edit {
            if (value == null) { remove(KEY_CURSOR_DOC); remove(KEY_CURSOR_SECONDS); remove(KEY_CURSOR_NANOS) }
            else {
                putString(KEY_CURSOR_DOC, value.docId)
                putLong(KEY_CURSOR_SECONDS, value.time.seconds)
                putInt(KEY_CURSOR_NANOS, value.time.nanos)
            }
        }

    /** Epoch millis of the last sync that finished without error; 0 when none. */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNC, value) }

    /**
     * Sync switched off on the Sync page: nothing is pushed or pulled, but the account, the key
     * and the cursor stay, so switching it back on resumes where it stopped.
     */
    var paused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) = prefs.edit { putBoolean(KEY_PAUSED, value) }

    /** The last failure as "Type: message", for the status and debug section; null after a success. */
    var lastError: String?
        get() = prefs.getString(KEY_ERROR, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ERROR) else putString(KEY_ERROR, value.take(500)) }

    var lastProblem: SyncProblem?
        get() = prefs.getString(KEY_PROBLEM, null)?.let { runCatching { SyncProblem.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit { if (value == null) remove(KEY_PROBLEM) else putString(KEY_PROBLEM, value.name) }

    /** Forgets the account and everything tied to it (the device id stays). */
    fun clearAccount() {
        account = null
        key = null
        memoryKey = null
        enabled = false
        cursor = null
        lastSyncAt = 0
        lastProblem = null
        lastError = null
        paused = false
    }

    private companion object {
        const val KEY_DEVICE = "device_id"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_KEY = "key"
        const val KEY_ENABLED = "enabled"
        const val KEY_CURSOR_DOC = "cursor_doc"
        const val KEY_CURSOR_SECONDS = "cursor_seconds"
        const val KEY_CURSOR_NANOS = "cursor_nanos"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_PROBLEM = "last_problem"
        const val KEY_PAUSED = "paused"
        const val KEY_ERROR = "last_error"
    }
}
