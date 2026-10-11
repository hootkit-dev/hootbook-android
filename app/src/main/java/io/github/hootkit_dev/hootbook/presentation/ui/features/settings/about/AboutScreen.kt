package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hootkit_dev.hootbook.BuildConfig
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.Constants
import io.github.hootkit_dev.hootbook.data.update.PublishChannel
import io.github.hootkit_dev.hootbook.presentation.ui.components.GitHubUpdateDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.RadioRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing

/**
 * About: the app and its version, updates from GitHub, this project's links and licenses, and
 * the upstream Cashiro project Hootbook is built from.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    updateViewModel: GitHubUpdateViewModel = hiltViewModel(),
    coreSchemaVersion: UInt? = null,
) {
    val context = LocalContext.current
    val updateState by updateViewModel.uiState.collectAsState()
    var showChannelDialog by remember { mutableStateOf(false) }
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    SecondaryScreen(title = stringResource(R.string.about), onBack = onNavigateBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                    Image(painterResource(R.drawable.hootbook), stringResource(R.string.hootbook_logo_cd), Modifier.padding(20.dp).size(56.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                coreSchemaVersion?.let {
                    Text(stringResource(R.string.about_data_version, it.toInt()), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ListSection(title = stringResource(R.string.about_updates)) {
                if (updateState.selectableChannels.size > 1) item(leading = { Icon(Icons.Rounded.Tune, null) },
                    headline = { Text(stringResource(R.string.update_channel)) }, supporting = { Text(channelLabel(updateState.selectedChannel)) },
                    chevron = true, onClick = { showChannelDialog = true })
                item(leading = { Icon(Icons.Rounded.SystemUpdate, null) }, headline = { Text(stringResource(R.string.check_for_updates)) },
                    supporting = { Text(when (updateState.status) {
                        GitHubUpdateStatus.Idle -> stringResource(R.string.update_check_desc)
                        GitHubUpdateStatus.Checking -> stringResource(R.string.update_checking)
                        GitHubUpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
                        GitHubUpdateStatus.Available -> stringResource(R.string.update_available_status, updateState.available?.title.orEmpty())
                        GitHubUpdateStatus.Failed -> stringResource(R.string.update_check_failed)
                    }) },
                    onClick = if (updateState.status != GitHubUpdateStatus.Checking) { { updateViewModel.checkForUpdate(promptIfAvailable = true) } } else null)
            }
            ListSection(title = stringResource(R.string.app_name)) {
                link(Icons.Rounded.Code, stringResource(R.string.github), "hootkit-dev/hootbook-android") { open(Constants.Links.GITHUB_URL) }
                link(Icons.Rounded.BugReport, stringResource(R.string.report_bug), stringResource(R.string.report_bug_desc)) { open(Constants.Links.REPORT_BUG_URL) }
                link(Icons.Rounded.PrivacyTip, stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_desc)) { open(Constants.Links.PRIVACY_POLICY_URL) }
                item(leading = { Icon(Icons.Rounded.Description, null) }, headline = { Text(stringResource(R.string.licenses)) },
                    supporting = { Text(stringResource(R.string.licenses_desc)) }, chevron = true, onClick = onNavigateToLicenses)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListSection(title = stringResource(R.string.about_upstream)) {
                    link(Icons.Rounded.Code, stringResource(R.string.github), "ritesh-kanwar/Cashiro") { open(Constants.Links.UPSTREAM_GITHUB_URL) }
                    link(Icons.Rounded.Language, stringResource(R.string.website), stringResource(R.string.about_upstream_website)) { open(Constants.Links.WEBSITE_URL) }
                    link(Icons.Rounded.Forum, stringResource(R.string.discord), stringResource(R.string.join_community)) { open(Constants.Links.DISCORD_URL) }
                }
                Text(stringResource(R.string.about_upstream_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val availableRelease = updateState.available
    if (updateState.showDialog && availableRelease != null) GitHubUpdateDialog(release = availableRelease, onDismiss = { updateViewModel.dismissDialog() },
        onDownload = { item ->
            updateViewModel.hideDialog()
            val target = item.apkUrl ?: item.htmlUrl
            if (target.isNotBlank()) open(target)
        })
    if (showChannelDialog) AlertDialog(onDismissRequest = { showChannelDialog = false }, title = { Text(stringResource(R.string.update_channel)) },
        text = {
            Column {
                updateState.selectableChannels.forEach { channel ->
                    RadioRow(channelLabel(channel), updateState.selectedChannel == channel,
                        { updateViewModel.selectChannel(channel); showChannelDialog = false }, supporting = channelDescription(channel))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showChannelDialog = false }) { Text(stringResource(R.string.cancel)) } })
}

/** A row that opens a web page. */
private fun ListSectionScope.link(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) = item(
    leading = { Icon(icon, null) }, headline = { Text(title) }, supporting = { Text(subtitle) },
    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = onClick)

@Composable
private fun channelLabel(channel: PublishChannel): String = stringResource(when (channel) {
    PublishChannel.DEBUG -> R.string.update_channel_debug
    PublishChannel.TESTING -> R.string.update_channel_testing
    PublishChannel.RELEASE -> R.string.update_channel_release
})

@Composable
private fun channelDescription(channel: PublishChannel): String = stringResource(when (channel) {
    PublishChannel.DEBUG -> R.string.update_channel_debug_desc
    PublishChannel.TESTING -> R.string.update_channel_testing_desc
    PublishChannel.RELEASE -> R.string.update_channel_release_desc
})
