package io.github.hootkit_dev.hootbook.data.update

data class GitHubRelease(
    val tag: String,
    val title: String,
    val htmlUrl: String,
    val publishedAtMillis: Long,
    val apkUrl: String?,
    val commitCount: Int = 0,
    val versionCode: Int = 0
)
