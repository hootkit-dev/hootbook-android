package io.github.hootkit_dev.hootbook.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** An app whose icon could stand for a merchant. */
data class AppIconCandidate(val name: String, val seller: String, val thumbnailUrl: String, val iconUrl: String)

class IconSearchException(message: String) : Exception(message)

/**
 * Looks up app icons with Apple's public App Store search, which needs no key. Only called when
 * the user searches, so a merchant name leaves the device only then.
 */
@Singleton
class AppStoreIconSearch internal constructor(private val client: HttpClient) {
    @Inject constructor() : this(
        HttpClient(Android) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(term: String, country: String = "cn"): List<AppIconCandidate> = withContext(Dispatchers.IO) {
        val response = try {
            client.get("https://itunes.apple.com/search") {
                parameter("term", term.trim())
                parameter("country", country)
                parameter("entity", "software")
                parameter("limit", LIMIT)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IconSearchException("Could not reach the App Store")
        }
        if (!response.status.isSuccess()) throw IconSearchException("HTTP ${response.status.value}")
        parse(response.bodyAsText())
    }

    internal fun parse(body: String): List<AppIconCandidate> {
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray }.getOrNull()
            ?: throw IconSearchException("Unexpected answer from the App Store")
        return results.mapNotNull { element ->
            val app = element as? JsonObject ?: return@mapNotNull null
            val icon = app.text("artworkUrl512") ?: return@mapNotNull null
            AppIconCandidate(
                name = app.text("trackName").orEmpty(),
                seller = app.text("sellerName").orEmpty(),
                thumbnailUrl = app.text("artworkUrl100") ?: icon,
                iconUrl = icon
            )
        }
    }

    /** The icon at [ICON_SIZE] pixels square, ready to store. */
    suspend fun download(candidate: AppIconCandidate): Bitmap = withContext(Dispatchers.IO) {
        val response = try {
            client.get(candidate.iconUrl)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IconSearchException("Could not download the icon")
        }
        if (!response.status.isSuccess()) throw IconSearchException("HTTP ${response.status.value}")
        val bytes = response.bodyAsBytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IconSearchException("The icon is not an image")
        Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val ICON_SIZE = 192
        private const val LIMIT = 10
    }
}
