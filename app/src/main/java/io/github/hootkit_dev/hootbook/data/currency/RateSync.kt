package io.github.hootkit_dev.hootbook.data.currency

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A server rates can be fetched from. */
enum class RateServer(val displayName: String, val homepage: String) {
    EXCHANGE_RATE_API("ExchangeRate-API", "https://www.exchangerate-api.com"),
    FRANKFURTER("Frankfurter (ECB)", "https://frankfurter.dev"),
    CURRENCY_API("currency-api (jsdelivr)", "https://github.com/fawazahmed0/exchange-api"),
    CURRENCY_API_MIRROR("currency-api (Cloudflare)", "https://github.com/fawazahmed0/exchange-api")
}

/** Which servers a sync tries: all in order, or one (currency-api with its mirror). */
enum class RateServerChoice(val servers: List<RateServer>) {
    AUTO(RateServer.entries),
    EXCHANGE_RATE_API(listOf(RateServer.EXCHANGE_RATE_API)),
    FRANKFURTER(listOf(RateServer.FRANKFURTER)),
    CURRENCY_API(listOf(RateServer.CURRENCY_API, RateServer.CURRENCY_API_MIRROR))
}

/** Why a server gave no rates. */
sealed interface RateFailure {
    data object Timeout : RateFailure
    data object Unreachable : RateFailure
    data class Http(val code: Int) : RateFailure
    data object BadResponse : RateFailure
    data class Other(val message: String) : RateFailure

    companion object {
        fun of(e: Throwable): RateFailure = when {
            // Ktor's request, connect and socket timeouts, and java.net's
            generateSequence(e) { it.cause }.any { it.javaClass.simpleName.contains("Timeout") } -> Timeout
            generateSequence(e) { it.cause }.any {
                it is UnknownHostException || it is ConnectException || it is SSLException
            } -> Unreachable
            else -> Other(e.message ?: e.javaClass.simpleName)
        }
    }
}

/** The last sync attempt: when, for which base, which server answered and which failed why. */
data class RateSyncStatus(
    val attemptedAtMillis: Long,
    val baseCurrency: String,
    val succeededWith: RateServer?,
    val failures: List<Pair<RateServer, RateFailure>>
) {
    val succeeded: Boolean get() = succeededWith != null
}

/**
 * The chosen rate servers (kept across launches) and the outcome of the last sync, for the
 * exchange-rate sheet to show why rates are or are not current.
 */
@Singleton
class RateSyncState @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("exchange_rate_prefs", Context.MODE_PRIVATE)

    private val _choice = MutableStateFlow(
        prefs.getString(KEY_CHOICE, null)
            ?.let { name -> RateServerChoice.entries.firstOrNull { it.name == name } }
            ?: RateServerChoice.AUTO
    )
    val choice: StateFlow<RateServerChoice> = _choice.asStateFlow()

    private val _lastSync = MutableStateFlow<RateSyncStatus?>(null)
    val lastSync: StateFlow<RateSyncStatus?> = _lastSync.asStateFlow()

    fun setChoice(choice: RateServerChoice) {
        _choice.value = choice
        prefs.edit { putString(KEY_CHOICE, choice.name) }
    }

    fun record(status: RateSyncStatus) {
        _lastSync.value = status
    }

    private companion object {
        const val KEY_CHOICE = "server_choice"
    }
}
