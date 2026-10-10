package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.BuildConfig
import io.github.hootkit_dev.hootbook.data.update.GitHubRelease
import io.github.hootkit_dev.hootbook.data.update.GitHubUpdatePreferences
import io.github.hootkit_dev.hootbook.data.update.GitHubUpdateRepository
import io.github.hootkit_dev.hootbook.data.update.PublishChannel
import io.github.hootkit_dev.hootbook.data.update.UpdateAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitHubUpdateUiState(
    val isChecking: Boolean = false,
    val available: GitHubRelease? = null,
    val showDialog: Boolean = false,
    val status: GitHubUpdateStatus = GitHubUpdateStatus.Idle,
    val selectedChannel: PublishChannel = PublishChannel.fromBuildConfig(),
    val selectableChannels: List<PublishChannel> = PublishChannel.selectable()
)

enum class GitHubUpdateStatus {
    Idle,
    Checking,
    UpToDate,
    Available,
    Failed
}

@HiltViewModel
class GitHubUpdateViewModel @Inject constructor(
    private val repository: GitHubUpdateRepository,
    private val preferences: GitHubUpdatePreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubUpdateUiState())
    val uiState: StateFlow<GitHubUpdateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val selected = preferences.getSelectedChannel()
            _uiState.update { it.copy(selectedChannel = selected) }
        }
    }

    fun selectChannel(channel: PublishChannel) {
        viewModelScope.launch {
            preferences.setSelectedChannel(channel)
            val selected = preferences.getSelectedChannel()
            _uiState.update {
                it.copy(
                    selectedChannel = selected,
                    available = null,
                    showDialog = false,
                    status = GitHubUpdateStatus.Idle
                )
            }
        }
    }

    fun checkForUpdate(promptIfAvailable: Boolean) {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            val channel = preferences.getSelectedChannel()
            _uiState.update {
                it.copy(
                    isChecking = true,
                    selectedChannel = channel,
                    status = GitHubUpdateStatus.Checking
                )
            }
            val result = repository.fetchLatestRelease(channel)
            result.fold(
                onSuccess = { release ->
                    val prompt = UpdateAvailability.shouldPrompt(
                        remoteCommitCount = channel.remoteBuild(release),
                        localCommitCount = channel.localBuild(installedVersionCode()),
                        dismissedCommitCount = preferences.getDismissedBuild(channel)
                    )
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            available = release,
                            showDialog = promptIfAvailable && prompt,
                            status = if (prompt) GitHubUpdateStatus.Available else GitHubUpdateStatus.UpToDate
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            available = null,
                            showDialog = false,
                            status = GitHubUpdateStatus.Failed
                        )
                    }
                }
            )
        }
    }

    fun dismissDialog() {
        val release = _uiState.value.available
        val channel = _uiState.value.selectedChannel
        viewModelScope.launch {
            if (release != null) {
                preferences.setDismissedBuild(channel, channel.remoteBuild(release))
            }
            _uiState.update { it.copy(showDialog = false) }
        }
    }

    fun hideDialog() {
        _uiState.update { it.copy(showDialog = false) }
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Int {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            BuildConfig.VERSION_CODE
        }
    }
}
