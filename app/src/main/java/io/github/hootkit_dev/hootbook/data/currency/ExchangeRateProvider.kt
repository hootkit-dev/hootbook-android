package io.github.hootkit_dev.hootbook.data.currency

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Interface for exchange rate providers
 */
interface ExchangeRateProvider {
    suspend fun fetchExchangeRate(fromCurrency: String, toCurrency: String): BigDecimal?
    suspend fun fetchAllExchangeRates(baseCurrency: String): Map<String, BigDecimal>?
    suspend fun fetchAllExchangeRatesWithMetadata(baseCurrency: String): ExchangeRateResponseWithMetadata?
    fun getProviderName(): String
    suspend fun getSupportedCurrencies(): List<String>
    suspend fun fetchAllCurrencies(): Map<String, String>?
}

/**
 * Daily exchange rates from free, keyless sources. By default ([RateServerChoice.AUTO]) they are
 * tried in this order until one answers; the exchange-rate sheet can pin one instead:
 *
 * 1. ExchangeRate-API's open access endpoint (open.er-api.com): about 160 currencies and
 *    exact update times. Its terms ask for attribution, shown on the exchange-rate sheet.
 * 2. Frankfurter (European Central Bank reference rates): about 30 major currencies,
 *    including CNY, HKD, SGD, USD, EUR and JPY.
 * 3. fawazahmed0/currency-api on jsdelivr and its Cloudflare mirror: the most currencies,
 *    but jsdelivr is often slow or unreachable from mainland China, so it comes last.
 *
 * Every request has short timeouts: rates are fetched while screens wait to show converted
 * amounts, and the engine's default would wait 100 s on a source that never answers.
 */
class FreeExchangeRateProvider @Inject constructor(
    private val syncState: RateSyncState
) : ExchangeRateProvider {

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 8_000
            requestTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val FAWAZ_URL_BASE = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1"
    private val FAWAZ_FALLBACK_URL_BASE = "https://latest.currency-api.pages.dev/v1"

    override suspend fun fetchExchangeRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return BigDecimal.ONE
        }

        return try {
            val rates = fetchAllExchangeRates(fromCurrency)
            rates?.get(toCurrency.uppercase())
        } catch (e: Exception) {
            println("Failed to fetch exchange rate from API: ${e.message}")
            null
        }
    }

    override suspend fun fetchAllExchangeRates(baseCurrency: String): Map<String, BigDecimal>? {
        val response = fetchAllExchangeRatesWithMetadata(baseCurrency)
        return response?.rates
    }

    private fun url(server: RateServer, base: String): String = when (server) {
        RateServer.EXCHANGE_RATE_API -> "https://open.er-api.com/v6/latest/$base"
        RateServer.FRANKFURTER -> "https://api.frankfurter.dev/v1/latest?base=$base"
        RateServer.CURRENCY_API -> "$FAWAZ_URL_BASE/currencies/${base.lowercase()}.json"
        RateServer.CURRENCY_API_MIRROR -> "$FAWAZ_FALLBACK_URL_BASE/currencies/${base.lowercase()}.json"
    }

    private fun parse(server: RateServer, body: String, base: String): ExchangeRateResponseWithMetadata? =
        when (server) {
            RateServer.EXCHANGE_RATE_API -> parseOpenErApi(json, body, base)
            RateServer.FRANKFURTER -> parseFrankfurter(json, body, base)
            RateServer.CURRENCY_API, RateServer.CURRENCY_API_MIRROR -> parseFawaz(json, body, base)
        }

    /** Tries the chosen servers in order and records which answered and why the others did not. */
    override suspend fun fetchAllExchangeRatesWithMetadata(baseCurrency: String): ExchangeRateResponseWithMetadata? {
        val base = baseCurrency.uppercase()
        return withContext(Dispatchers.IO) {
            val failures = mutableListOf<Pair<RateServer, RateFailure>>()
            var result: ExchangeRateResponseWithMetadata? = null
            var answered: RateServer? = null
            for (server in syncState.choice.value.servers) {
                when (val outcome = fetchFrom(server, base)) {
                    is FetchOutcome.Rates -> { result = outcome.rates; answered = server; break }
                    is FetchOutcome.Failed -> failures += server to outcome.failure
                }
            }
            syncState.record(RateSyncStatus(System.currentTimeMillis(), base, answered, failures))
            result
        }
    }

    private sealed interface FetchOutcome {
        data class Rates(val rates: ExchangeRateResponseWithMetadata) : FetchOutcome
        data class Failed(val failure: RateFailure) : FetchOutcome
    }

    private suspend fun fetchFrom(server: RateServer, base: String): FetchOutcome {
        val url = url(server, base)
        return try {
            val response = client.get(url) { header("User-Agent", "Hootbook/1.0") }
            if (response.status.value !in 200..299) return FetchOutcome.Failed(RateFailure.Http(response.status.value))
            val rates = try {
                parse(server, response.body<String>(), base)
            } catch (e: Exception) {
                null
            }
            rates?.let { FetchOutcome.Rates(it) } ?: FetchOutcome.Failed(RateFailure.BadResponse)
        } catch (e: Exception) {
            println("Exchange rates not loaded from $url: ${e.message}")
            FetchOutcome.Failed(RateFailure.of(e))
        }
    }

    override fun getProviderName(): String = PROVIDER_OPEN_ER_API

    override suspend fun fetchAllCurrencies(): Map<String, String>? {
        val endpoint = "currencies.json"
        return try {
            withContext(Dispatchers.IO) {
                var responseBody: String? = null
                
                // Try Primary URL
                try {
                    val response = client.get("$FAWAZ_URL_BASE/$endpoint")
                    if (response.status.value in 200..299) {
                        responseBody = response.body<String>()
                    }
                } catch (e: Exception) {}
                
                // Try Fallback URL
                if (responseBody == null) {
                    try {
                        val response = client.get("$FAWAZ_FALLBACK_URL_BASE/$endpoint")
                        if (response.status.value in 200..299) {
                            responseBody = response.body<String>()
                        }
                    } catch (e: Exception) {}
                }

                if (responseBody != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    val currencies = json.parseToJsonElement(responseBody).jsonObject
                    currencies.mapValues { it.value.jsonPrimitive.content }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            println("Failed to fetch currencies mapping: ${e.message}")
            null
        }
    }

    override suspend fun getSupportedCurrencies(): List<String> {
        val currencies = fetchAllCurrencies()
        return currencies?.keys?.map { it.uppercase() }?.toList() ?: listOf(
            "AED", "USD", "EUR", "GBP", "INR", "THB", "MYR", "SGD", "KWD", "KRW",
            "CAD", "AUD", "JPY", "CNY", "HKD", "TWD", "MOP", "NPR", "ETB"
        )
    }
}

const val PROVIDER_OPEN_ER_API = "ExchangeRate-API (open.er-api.com)"
const val PROVIDER_FRANKFURTER = "Frankfurter (ECB)"
const val PROVIDER_FAWAZ = "fawazahmed0/currency-api"

private const val DAY_SECONDS = 24 * 3600L

/** Rates keyed by upper-case code, six decimals, with the base itself at exactly 1. */
private fun normalizedRates(base: String, raw: Map<String, String>): Map<String, BigDecimal> {
    val rates = mutableMapOf(base to BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP))
    raw.forEach { (code, value) ->
        val upper = code.uppercase()
        if (upper != base) {
            value.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let {
                rates[upper] = it.setScale(6, RoundingMode.HALF_UP)
            }
        }
    }
    return rates
}

private fun JsonObject.stringValues(): Map<String, String> =
    mapValues { (_, v) -> v.jsonPrimitive.content }

/** A published date's start, in the device's zone; sources without times update daily. */
private fun dayStart(date: String): Long =
    LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

/**
 * When a daily source's rates expire. Their date often lags a day, so a day after it can
 * already be past; rates fetched now stay current for at least six hours.
 */
private fun dailyExpiry(lastUpdateUnix: Long): Long =
    maxOf(lastUpdateUnix + DAY_SECONDS, System.currentTimeMillis() / 1000 + 6 * 3600)

/** open.er-api.com: `{"result":"success","time_last_update_unix":..,"time_next_update_unix":..,"rates":{..}}` */
internal fun parseOpenErApi(json: Json, body: String, base: String): ExchangeRateResponseWithMetadata? {
    val root = json.parseToJsonElement(body).jsonObject
    if (root["result"]?.jsonPrimitive?.content != "success") return null
    val rates = root["rates"]?.jsonObject ?: return null
    val last = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull ?: return null
    val next = root["time_next_update_unix"]?.jsonPrimitive?.longOrNull ?: (last + DAY_SECONDS)
    return ExchangeRateResponseWithMetadata(
        rates = normalizedRates(base, rates.stringValues()),
        nextUpdateTimeUnix = next,
        lastUpdateTimeUnix = last,
        provider = PROVIDER_OPEN_ER_API,
        baseCurrency = base
    )
}

/** Frankfurter: `{"base":"USD","date":"2026-10-02","rates":{..}}` (the base is not in rates). */
internal fun parseFrankfurter(json: Json, body: String, base: String): ExchangeRateResponseWithMetadata? {
    val root = json.parseToJsonElement(body).jsonObject
    val rates = root["rates"]?.jsonObject ?: return null
    val last = dayStart(root["date"]?.jsonPrimitive?.content ?: return null)
    return ExchangeRateResponseWithMetadata(
        rates = normalizedRates(base, rates.stringValues()),
        nextUpdateTimeUnix = dailyExpiry(last),
        lastUpdateTimeUnix = last,
        provider = PROVIDER_FRANKFURTER,
        baseCurrency = base
    )
}

/** fawazahmed0/currency-api: `{"date":"2026-10-02","usd":{"cny":6.7,..}}` with lower-case codes. */
internal fun parseFawaz(json: Json, body: String, base: String): ExchangeRateResponseWithMetadata? {
    val root = json.parseToJsonElement(body).jsonObject
    val rates = root[base.lowercase()]?.jsonObject ?: return null
    val last = root["date"]?.jsonPrimitive?.content?.let { runCatching { dayStart(it) }.getOrNull() }
        ?: (System.currentTimeMillis() / 1000)
    return ExchangeRateResponseWithMetadata(
        rates = normalizedRates(base, rates.stringValues()),
        nextUpdateTimeUnix = dailyExpiry(last),
        lastUpdateTimeUnix = last,
        provider = PROVIDER_FAWAZ,
        baseCurrency = base
    )
}

/**
 * Data class for parsing ExchangeRate-API response (Deprecated)
 */
@Serializable
data class ExchangeRateApiResponse(
    val result: String = "",
    val provider: String = "",
    val documentation: String = "",
    val terms_of_use: String = "",
    val time_last_update_unix: Long = 0,
    val time_last_update_utc: String = "",
    val time_next_update_unix: Long = 0,
    val time_next_update_utc: String = "",
    val time_eol_unix: Long = 0,
    val base_code: String = "",
    val rates: Map<String, Double> = emptyMap()
)

/**
 * Extended response data class that includes the full API response with timestamps
 */
data class ExchangeRateResponseWithMetadata(
    val rates: Map<String, BigDecimal>,
    val nextUpdateTimeUnix: Long,
    val lastUpdateTimeUnix: Long,
    val provider: String,
    val baseCurrency: String
)

/**
 * Factory for creating exchange rate providers
 */
object ExchangeRateProviderFactory {
    fun createProvider(syncState: RateSyncState): ExchangeRateProvider {
        return FreeExchangeRateProvider(syncState)
    }
}