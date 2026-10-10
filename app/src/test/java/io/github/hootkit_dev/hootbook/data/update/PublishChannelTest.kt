package io.github.hootkit_dev.hootbook.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PublishChannelTest {
    private fun payload(tag: String, apk: String, prerelease: Boolean = false, body: String = "version_code: 95") =
        """{"tag_name":"$tag","prerelease":$prerelease,"body":"$body","assets":[{"name":"$apk","browser_download_url":"https://example.org/$apk"}]}"""

    private suspend fun check(channel: PublishChannel, body: String, status: HttpStatusCode = HttpStatusCode.OK,
                              abis: List<String> = listOf("arm64-v8a")): Result<GitHubRelease> {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val result = GitHubUpdateRepository(client, channel, abis).fetchLatestRelease()
            assertEquals(listOf(channel.apiUrl), urls) // Including errors: no cross-channel fallback.
            return result
        } finally { client.close() }
    }

    @Test fun debugUsesOnlyRollingTagAndCommitIdentity() = runTest {
        val release = check(PublishChannel.DEBUG, payload("debug-latest",
            "Hootbook-debug-c900-abc-arm64-v8a.apk", true, "commit_count: 900")).getOrThrow()
        assertEquals(900, PublishChannel.DEBUG.remoteBuild(release))
    }

    @Test fun releaseUsesOnlyStableLatestAndVersionCode() = runTest {
        val release = check(PublishChannel.RELEASE,
            payload("v2.2.0", "Hootbook-v2.2.0-arm64-v8a.apk")).getOrThrow()
        assertEquals(95, PublishChannel.RELEASE.remoteBuild(release))
        assertEquals(0, release.commitCount)
    }

    @Test fun unavailableChannelNeverFallsBack() = runTest {
        for (channel in PublishChannel.entries) {
            assertTrue(check(channel, "{}", HttpStatusCode.NotFound).isFailure)
            assertTrue(check(channel, "{}", HttpStatusCode.TooManyRequests).isFailure)
        }
    }

    @Test fun wrongChannelAndPrereleaseAreRejected() = runTest {
        assertTrue(check(PublishChannel.RELEASE, payload("debug-latest", "Hootbook-debug-c9-arm64-v8a.apk", true)).isFailure)
        assertTrue(check(PublishChannel.DEBUG, payload("v2.2.0", "Hootbook-v2.2.0-arm64-v8a.apk")).isFailure)
        assertTrue(check(PublishChannel.RELEASE, payload("v2.2.0-beta", "Hootbook-v2.2.0-beta-arm64-v8a.apk", true)).isFailure)
    }

    @Test fun assetsStayInTheirChannelAndMatchDeviceArchitecture() = runTest {
        assertTrue(check(PublishChannel.RELEASE, payload("v2.2.0", "Hootbook-debug-c9-arm64-v8a.apk")).isFailure)
        assertTrue(check(PublishChannel.DEBUG, payload("debug-latest", "Hootbook-v2.2.0-arm64-v8a.apk")).isFailure)
        assertTrue(check(PublishChannel.RELEASE, payload("v2.2.0", "Hootbook-v2.2.0-arm64-v8a.apk"),
            abis = listOf("armeabi-v7a")).isFailure)
        assertTrue(check(PublishChannel.RELEASE, payload("v2.2.0", "Hootbook-v2.2.0-universal.apk"),
            abis = listOf("armeabi-v7a")).isSuccess)
    }

    @Test fun missingVersionCodeFailsRatherThanReportingUpToDate() = runTest {
        assertTrue(check(PublishChannel.RELEASE, payload("v2.2.0", "Hootbook-v2.2.0-universal.apk", body = "old notes")).isFailure)
        assertEquals(0, PublishChannel.parseVersionCode("version_code: 9999999999999999999"))
    }

    @Test fun testingUsesOnlyRollingTagAndCommitIdentity() = runTest {
        val release = check(
            PublishChannel.TESTING,
            payload("testing-latest", "Hootbook-testing-c1200-abc-arm64-v8a.apk", true, "commit_count: 1200")
        ).getOrThrow()
        assertEquals(1200, PublishChannel.TESTING.remoteBuild(release))
        assertEquals(0, release.versionCode)
    }

    @Test fun testingRejectsOtherChannelsAndAssets() = runTest {
        assertTrue(
            check(
                PublishChannel.TESTING,
                payload("debug-latest", "Hootbook-debug-c9-arm64-v8a.apk", true, "commit_count: 9")
            ).isFailure
        )
        assertTrue(check(PublishChannel.TESTING, payload("v2.2.0", "Hootbook-v2.2.0-arm64-v8a.apk")).isFailure)
        assertTrue(
            check(
                PublishChannel.RELEASE,
                payload("testing-latest", "Hootbook-testing-c9-arm64-v8a.apk", true, "commit_count: 9")
            ).isFailure
        )
        assertTrue(
            check(
                PublishChannel.DEBUG,
                payload("testing-latest", "Hootbook-testing-c9-arm64-v8a.apk", true, "commit_count: 9")
            ).isFailure
        )
        assertTrue(
            check(
                PublishChannel.TESTING,
                payload("testing-latest", "Hootbook-debug-c9-arm64-v8a.apk", true, "commit_count: 9")
            ).isFailure
        )
    }

    @Test fun missingCommitCountFailsTestingChannel() = runTest {
        assertTrue(
            check(
                PublishChannel.TESTING,
                payload("testing-latest", "Hootbook-testing-universal.apk", true, "old notes")
            ).isFailure
        )
    }

    @Test fun selectableChannelsDependOnBuild() {
        assertEquals(listOf(PublishChannel.DEBUG), PublishChannel.selectable(PublishChannel.DEBUG))
        assertEquals(
            listOf(PublishChannel.RELEASE, PublishChannel.TESTING),
            PublishChannel.selectable(PublishChannel.RELEASE)
        )
        assertEquals(
            listOf(PublishChannel.RELEASE, PublishChannel.TESTING),
            PublishChannel.selectable(PublishChannel.TESTING)
        )
    }

    @Test fun fromIdIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(PublishChannel.TESTING, PublishChannel.fromId("TESTING"))
        assertEquals(PublishChannel.RELEASE, PublishChannel.fromIdOrNull("release"))
        assertEquals(null, PublishChannel.fromIdOrNull("nightly"))
    }
}
