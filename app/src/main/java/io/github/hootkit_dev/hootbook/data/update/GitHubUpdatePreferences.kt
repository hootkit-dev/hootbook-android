package io.github.hootkit_dev.hootbook.data.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gitHubUpdateStore by preferencesDataStore(name = "github_update")

@Singleton
class GitHubUpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val selectedChannelKey = stringPreferencesKey("selected_publish_channel")
    private val legacyDismissedCount = intPreferencesKey("dismissed_github_commit_count")
    private val legacyDismissedRelease = intPreferencesKey("dismissed_github_release_version_code")

    private fun dismissedKey(channel: PublishChannel) =
        intPreferencesKey("dismissed_github_build_${channel.id}")

    suspend fun getSelectedChannel(
        buildChannel: PublishChannel = PublishChannel.fromBuildConfig()
    ): PublishChannel {
        val stored = context.gitHubUpdateStore.data.map { it[selectedChannelKey] }.first()
        val parsed = stored?.let { PublishChannel.fromIdOrNull(it) }
        val allowed = PublishChannel.selectable(buildChannel)
        return if (parsed != null && parsed in allowed) parsed else buildChannel
    }

    suspend fun setSelectedChannel(channel: PublishChannel) {
        val allowed = PublishChannel.selectable()
        if (channel !in allowed) return
        context.gitHubUpdateStore.edit { it[selectedChannelKey] = channel.id }
    }

    suspend fun getDismissedBuild(channel: PublishChannel): Int {
        return context.gitHubUpdateStore.data.map { prefs ->
            prefs[dismissedKey(channel)]
                ?: when (channel) {
                    PublishChannel.DEBUG -> prefs[legacyDismissedCount]
                    PublishChannel.RELEASE -> prefs[legacyDismissedRelease]
                    PublishChannel.TESTING -> null
                }
                ?: 0
        }.first()
    }

    suspend fun setDismissedBuild(channel: PublishChannel, build: Int) {
        context.gitHubUpdateStore.edit { it[dismissedKey(channel)] = build }
    }
}
