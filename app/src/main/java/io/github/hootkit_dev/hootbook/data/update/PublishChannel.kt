package io.github.hootkit_dev.hootbook.data.update

import io.github.hootkit_dev.hootbook.BuildConfig

/** Identity of a GitHub publish lane. Fetching one channel never falls back to another. */
enum class PublishChannel(val id: String, val releasePath: String, val pagePath: String) {
    DEBUG("debug", "tags/debug-latest", "tag/debug-latest"),
    TESTING("testing", "tags/testing-latest", "tag/testing-latest"),
    RELEASE("release", "latest", "latest");

    val apiUrl get() = "https://api.github.com/repos/hootkit-dev/hootbook-android/releases/$releasePath"
    val pageUrl get() = "https://github.com/hootkit-dev/hootbook-android/releases/$pagePath"

    val usesCommitCount: Boolean get() = this != RELEASE

    fun acceptsRelease(tag: String, draft: Boolean, prerelease: Boolean): Boolean =
        !draft && when (this) {
            DEBUG -> tag == "debug-latest"
            TESTING -> tag == "testing-latest"
            RELEASE -> !prerelease && tag.startsWith("v")
        }

    fun acceptsAsset(name: String): Boolean = name.endsWith(".apk", ignoreCase = true) &&
        when (this) {
            DEBUG -> name.startsWith("Hootbook-debug-", ignoreCase = true)
            TESTING -> name.startsWith("Hootbook-testing-", ignoreCase = true)
            RELEASE -> name.startsWith("Hootbook-v", ignoreCase = true)
        }

    fun remoteBuild(release: GitHubRelease): Int = when (this) {
        DEBUG, TESTING -> release.commitCount
        RELEASE -> release.versionCode
    }

    fun localBuild(installedVersionCode: Int = BuildConfig.VERSION_CODE): Int = when (this) {
        DEBUG, TESTING -> BuildConfig.GIT_COMMIT_COUNT
        RELEASE -> installedVersionCode
    }

    companion object {
        fun fromIdOrNull(id: String): PublishChannel? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun fromId(id: String): PublishChannel =
            fromIdOrNull(id) ?: error("Unknown publish channel: $id")

        fun fromBuildConfig(): PublishChannel = fromId(BuildConfig.UPDATE_CHANNEL)

        /** Debug builds follow only debug: their versionCode orders by commit, not release. */
        fun selectable(buildChannel: PublishChannel = fromBuildConfig()): List<PublishChannel> =
            when (buildChannel) {
                DEBUG -> listOf(DEBUG)
                TESTING, RELEASE -> listOf(RELEASE, TESTING)
            }

        fun parseVersionCode(body: String?): Int =
            Regex("(?m)^version_code:\\s*(\\d+)\\s*$").find(body.orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }
}
