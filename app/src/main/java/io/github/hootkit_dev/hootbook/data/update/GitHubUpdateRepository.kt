package io.github.hootkit_dev.hootbook.data.update

import android.os.Build
import io.github.hootkit_dev.hootbook.BuildConfig
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Singleton
class GitHubUpdateRepository internal constructor(
    private val client: HttpClient,
    val channel: PublishChannel,
    private val supportedAbis: List<String>
) {
    @Inject constructor() : this(
        defaultClient(), PublishChannel.fromBuildConfig(), Build.SUPPORTED_ABIS.toList()
    )

    suspend fun fetchLatestRelease(channel: PublishChannel = this.channel): Result<GitHubRelease> {
        return try {
            Result.success(fetchRelease(channel) ?: error("No release available in ${channel.id} channel"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetchRelease(channel: PublishChannel): GitHubRelease? {
        val response = client.get(channel.apiUrl) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, USER_AGENT)
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            return null
        }
        val payload = response.body<GitHubReleaseDto>()
        if (!channel.acceptsRelease(payload.tagName.orEmpty(), payload.draft, payload.prerelease)) return null
        if (channel == PublishChannel.RELEASE && PublishChannel.parseVersionCode(payload.body) == 0) {
            error("Release is missing version_code metadata")
        }
        val publishedAt = payload.publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val title = payload.name?.takeIf { it.isNotBlank() } ?: payload.tagName.orEmpty()
        val commitCount = DebugBuildIdentity.parseCommitCount(
            title = title,
            body = payload.body,
            assetNames = payload.assets.map { it.name }
        )
        if (channel.usesCommitCount && commitCount <= 0) {
            error("${channel.id} release is missing commit_count metadata")
        }
        return GitHubRelease(
            tag = payload.tagName.orEmpty(),
            title = title,
            htmlUrl = payload.htmlUrl.orEmpty(),
            publishedAtMillis = publishedAt,
            apkUrl = pickApkUrl(channel, payload.assets) ?: error("No compatible APK in ${channel.id} channel"),
            versionCode = PublishChannel.parseVersionCode(payload.body),
            commitCount = commitCount
        )
    }

    private fun pickApkUrl(channel: PublishChannel, assets: List<GitHubAssetDto>): String? {
        val apks = assets.filter { channel.acceptsAsset(it.name) }
        return supportedAbis.firstNotNullOfOrNull { abi ->
            apks.firstOrNull { it.name.contains("-$abi", ignoreCase = true) }?.browserDownloadUrl
        } ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.browserDownloadUrl
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAssetDto> = emptyList()
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null
    )

    companion object {
        private fun defaultClient() = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }

        private const val USER_AGENT = "Hootbook-UpdateCheck"
        val RELEASES_PAGE: String get() = PublishChannel.fromBuildConfig().pageUrl
    }
}
