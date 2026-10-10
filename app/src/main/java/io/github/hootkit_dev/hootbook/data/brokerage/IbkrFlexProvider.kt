package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class IbkrFlexProvider internal constructor(
    engine: HttpClientEngine,
    private val parser: IbkrFlexParser,
    private val pace: suspend () -> Unit
) : BrokerageProvider {
    @Inject constructor(parser: IbkrFlexParser) : this(Android.create(), parser, { delay(7_000) })

    override val id = ID
    override val institutionId = "ibkr"
    private val mutex = Mutex()
    private val client = HttpClient(engine) {
        followRedirects = false // Never forward a report token to a URL from a response.
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        // Intentionally no HTTP logging: the Flex protocol puts its token in the query string.
    }

    override suspend fun fetchHoldings(credentials: BrokerCredentials): List<BrokerageAccount> = mutex.withLock {
        val token = credentials.fields["token"].orEmpty().trim()
        val queryId = credentials.fields["queryId"].orEmpty().trim()
        if (!token.matches(Regex("[0-9]{6,128}")) || !queryId.matches(Regex("[0-9]{1,32}"))) {
            throw BrokerageException(BrokerageError.INVALID_CREDENTIALS)
        }
        try {
            val reference = parser.reference(request("SendRequest", token, queryId))
            repeat(6) { attempt ->
                try {
                    return@withLock parser.parse(request("GetStatement", token, reference))
                } catch (e: BrokerageException) {
                    if (e.error != BrokerageError.REPORT_NOT_READY || attempt == 5) throw e
                }
            }
            throw BrokerageException(BrokerageError.REPORT_NOT_READY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BrokerageException) {
            throw e
        } catch (_: Exception) {
            // Do not retain the cause: HTTP exceptions can embed the full credential URL.
            throw BrokerageException(BrokerageError.NETWORK)
        }
    }

    private suspend fun request(action: String, token: String, query: String): String {
        // All requests (including new syncs) are spaced by 7 seconds: <= 9/minute.
        pace()
        return client.prepareGet("$BASE/$action") {
            header("User-Agent", "Hootbook-Android/IBKR-Flex-v3")
            parameter("t", token)
            parameter("q", query)
            parameter("v", "3")
        }.execute { response ->
            when (response.status.value) {
                200 -> Unit
                429 -> throw BrokerageException(BrokerageError.RATE_LIMITED)
                401, 403 -> throw BrokerageException(BrokerageError.INVALID_CREDENTIALS)
                else -> throw BrokerageException(BrokerageError.NETWORK)
            }
            val channel = response.bodyAsChannel()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    val count = channel.readAvailable(buffer)
                    if (count == -1) break
                    if (output.size() + count > IbkrFlexParser.MAX_REPORT_BYTES) {
                        throw BrokerageException(BrokerageError.INVALID_REPORT)
                    }
                    output.write(buffer, 0, count)
                }
            } finally { channel.cancel(null) }
            output.toString(Charsets.UTF_8.name())
        }
    }

    internal fun close() = client.close()
    companion object {
        const val ID = "ibkr_flex"
        const val BASE = "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService"
    }
}
