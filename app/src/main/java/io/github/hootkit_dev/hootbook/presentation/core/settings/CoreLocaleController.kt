package io.github.hootkit_dev.hootbook.presentation.core.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI language is not a ledger setting. Core's runtime locale is updated only after accepting a command. */
interface CoreUiLocalePreference {
    fun selection(): String
    fun effectiveTag(selection: String): String
    suspend fun apply(selection: String)
}

/**
 * The small UI-only store is readable before an AppCompatActivity exists. AndroidX autoStoreLocales
 * alone restores on Activity attach, after Application can already start Core on API 26–32.
 * LocaleManagerCompat is the public cold-start reader for the existing AndroidX/OS locale choice.
 */
class CoreLocaleController internal constructor(private val context: Context, private val writeCompat: (String) -> Unit) : CoreUiLocalePreference {
    @Inject constructor(@ApplicationContext context: Context) : this(context, { selection ->
        writeAndroidLocale(context, selection)
    })
    private val preferences get() = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    override fun selection(): String = synchronized(guard) {
        if (Build.VERSION.SDK_INT >= 33) {
            // OS per-app language selection remains authoritative (including external Settings changes).
            val osSelection = LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()
            if (!preferences.contains(SELECTION_KEY) || preferences.getString(SELECTION_KEY, null) != osSelection) {
                check(preferences.edit().putString(SELECTION_KEY, osSelection).commit()) { "Language preference persistence failed" }
            }
            return@synchronized osSelection
        }
        if (!preferences.contains(SELECTION_KEY)) {
            val persisted = LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()
            val selected = persisted.ifEmpty { AppCompatDelegate.getApplicationLocales().toLanguageTags() }
            check(preferences.edit().putString(SELECTION_KEY, selected).commit()) { "Language preference persistence failed" }
        }
        preferences.getString(SELECTION_KEY, "").orEmpty()
    }

    override fun effectiveTag(selection: String): String = localeForSelection(selection,
        LocaleManagerCompat.getSystemLocales(context)[0] ?: Locale.getDefault())

    /** CoreHostModule must call this on EACH open, including retries and explicit restarts. */
    fun resolveStartupTag(): String = effectiveTag(selection())

    /** Invoke at the start of MainActivity.attachBaseContext, before super, in Core-only mode. */
    fun applyStartupPreference() { writeCompat(selection()) }

    override suspend fun apply(selection: String) = withContext(Dispatchers.Main.immediate) {
        synchronized(guard) {
            val previous = this@CoreLocaleController.selection()
            val canonical = LocaleListCompat.forLanguageTags(selection).toLanguageTags()
            try {
                check(preferences.edit().putString(SELECTION_KEY, canonical).commit()) { "Language preference persistence failed" }
                writeCompat(canonical)
            } catch (failure: Throwable) {
                try { check(preferences.edit().putString(SELECTION_KEY, previous).commit()) { "Language preference rollback failed" } }
                catch (rollback: Throwable) { failure.addSuppressed(rollback) }
                try { writeCompat(previous) } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
                throw failure
            }
        }
    }

    companion object {
        // Pure UI preference; do not put main currency/default-account/rate settings in this store.
        const val STORE_NAME = "hootkit_core_ui"
        const val SELECTION_KEY = "locale_selection"
        private val guard = Any()
    }
}
internal fun localeForSelection(selection: String, system: Locale): String =
    LocaleListCompat.forLanguageTags(selection)[0]?.toLanguageTag() ?: system.toLanguageTag()

private fun writeAndroidLocale(context: Context, selection: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        val manager = checkNotNull(context.getSystemService(LocaleManager::class.java)) { "Android locale service missing" }
        manager.applicationLocales = LocaleList.forLanguageTags(selection)
    }
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selection))
}
