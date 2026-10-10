package io.github.hootkit_dev.hootbook.data.update

object DebugBuildIdentity {
    private val bodyCount = Regex("(?im)^commit_count:\\s*(\\d+)\\s*$")
    private val titleCount = Regex("\\bc(\\d+)\\b")
    private val apkCount = Regex("Hootbook-(?:debug|testing)-c(\\d+)-", RegexOption.IGNORE_CASE)

    fun parseCommitCount(
        title: String,
        body: String? = null,
        assetNames: List<String> = emptyList()
    ): Int {
        bodyCount.find(body.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        titleCount.find(title)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        assetNames.forEach { name ->
            apkCount.find(name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        return 0
    }
}
