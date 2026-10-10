package io.github.hootkit_dev.hootbook.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Icons the user picked for merchants, kept as files on the device. Every transaction whose
 * merchant has the same name (ignoring case and surrounding spaces) shows the picked icon.
 */
@Singleton
class MerchantIconStore @Inject constructor(@ApplicationContext context: Context) {
    val directory = File(context.filesDir, DIRECTORY)
    private val prefs = context.getSharedPreferences("merchant_icons", Context.MODE_PRIVATE)
    private val _icons = MutableStateFlow(load())

    /** Merchant key to icon file. */
    val icons: StateFlow<Map<String, File>> = _icons.asStateFlow()

    suspend fun save(merchantName: String, icon: Bitmap) = withContext(Dispatchers.IO) {
        val key = key(merchantName)
        directory.mkdirs()
        // A new name each time, so image caches never show the icon it replaces
        val file = File(directory, "${hash(key)}-${System.currentTimeMillis()}.webp")
        file.outputStream().use { out ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
            else Bitmap.CompressFormat.WEBP
            icon.compress(format, 90, out)
        }
        _icons.value[key]?.delete()
        prefs.edit { putString(key, file.name) }
        _icons.value = _icons.value + (key to file)
    }

    fun remove(merchantName: String) {
        val key = key(merchantName)
        _icons.value[key]?.delete()
        prefs.edit { remove(key) }
        _icons.value = _icons.value - key
    }

    /** Merchant key to file name, for a backup that also carries the files in [directory]. */
    fun index(): Map<String, String> = _icons.value.mapValues { it.value.name }

    /** Adds icons restored from a backup, whose files are already in [directory]. */
    fun restore(index: Map<String, String>) {
        val restored = index.mapNotNull { (key, name) ->
            File(directory, File(name).name).takeIf { it.isFile }?.let { key to it }
        }.toMap()
        if (restored.isEmpty()) return
        prefs.edit { restored.forEach { (key, file) -> putString(key, file.name) } }
        _icons.value = _icons.value + restored
    }

    private fun load(): Map<String, File> = prefs.all.mapNotNull { (key, name) ->
        (name as? String)?.let { File(directory, it) }?.takeIf { it.isFile }?.let { key to it }
    }.toMap()

    companion object {
        const val DIRECTORY = "merchant_icons"

        fun key(merchantName: String) = merchantName.trim().lowercase()

        private fun hash(key: String) = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }
}

/** For composables that cannot take the store from a ViewModel, such as the shared BrandIcon. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MerchantIconEntryPoint {
    fun merchantIconStore(): MerchantIconStore
}
