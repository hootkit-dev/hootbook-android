package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.BuildConfig
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconResource
import io.github.hootkit_dev.hootbook.presentation.ui.icons.*
import io.github.hootkit_dev.hootbook.presentation.ui.theme.*
import androidx.compose.foundation.clickable
import io.github.hootkit_dev.hootbook.core.Constants
import io.github.hootkit_dev.hootbook.data.update.PublishChannel
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    updateViewModel: GitHubUpdateViewModel = hiltViewModel(),
    blurEffects: Boolean,
    coreSchemaVersion: UInt? = null
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    var showChannelDialog by remember { mutableStateOf(false) }
    val updateState by updateViewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.about),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            //Banner image
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                val primary = MaterialTheme.colorScheme.primary
                val tertiary = MaterialTheme.colorScheme.tertiary
                val alternatingIcons = remember {
                    listOf(
                        IconResource.VectorIcon(Iconax.Hootbook, primary),
                        IconResource.VectorIcon(Iconax.HootbookOutline, tertiary)
                    )
                }

                TiledScrollingIconBackground(
                    iconResources = alternatingIcons,
                    opacity = 0.05f,
                    iconSize = 56.dp
                )
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .verticalScroll(
                        state = scrollState,
                    )
                    .padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content +
                                paddingValues.calculateTopPadding(),
                        bottom = 0.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // App Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.Padding.card),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.hootbook),
                            contentDescription = stringResource(R.string.hootbook_logo_cd),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Padding.content))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = coreSchemaVersion?.let { stringResource(R.string.version_format, BuildConfig.VERSION_NAME, it.toInt()) } ?: BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }

                val showChannelPicker = updateState.selectableChannels.size > 1
                val updateStatusText = when (updateState.status) {
                    GitHubUpdateStatus.Idle -> stringResource(R.string.update_check_desc)
                    GitHubUpdateStatus.Checking -> stringResource(R.string.update_checking)
                    GitHubUpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
                    GitHubUpdateStatus.Available -> stringResource(
                        R.string.update_available_status,
                        updateState.available?.title.orEmpty()
                    )
                    GitHubUpdateStatus.Failed -> stringResource(R.string.update_check_failed)
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    if (showChannelPicker) {
                        AboutListItem(
                            title = stringResource(R.string.update_channel),
                            subtitle = channelLabel(updateState.selectedChannel),
                            icon = Iconax.HierarchySquare3,
                            iconColor = blue_dark,
                            iconBackground = blue_light,
                            onClick = { showChannelDialog = true },
                            position = ListItemPosition.Top
                        )
                    }
                    AboutListItem(
                        title = stringResource(R.string.check_for_updates),
                        subtitle = updateStatusText,
                        icon = Iconax.RefreshCircle,
                        iconColor = cyan_dark,
                        iconBackground = cyan_light,
                        onClick = { updateViewModel.checkForUpdate(promptIfAvailable = true) },
                        position = if (showChannelPicker) ListItemPosition.Bottom else ListItemPosition.Single
                    )
                }

                // Connect Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    AboutListItem(
                        title = stringResource(R.string.website),
                        subtitle = "cashiro.showcase",
                        icon = Iconax.Shop,
                        iconColor = orange_dark,
                        iconBackground = orange_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.WEBSITE_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Top
                    )
                    AboutListItem(
                        title = stringResource(R.string.github),
                        subtitle = "hootkit-dev/hootbook-android",
                        icon = Iconax.Github,
                        iconColor = green_dark,
                        iconBackground = green_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.GITHUB_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Middle
                    )
                    AboutListItem(
                        title = stringResource(R.string.discord),
                        subtitle = stringResource(R.string.join_community),
                        icon = Iconax.Discord,
                        iconColor = purple_dark,
                        iconBackground = purple_light,
                        isLink = true,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_VIEW, Constants.Links.DISCORD_URL.toUri())
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Bottom
                    )

                }

                // Links Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {

                    AboutListItem(
                        title = stringResource(R.string.faq),
                        subtitle = stringResource(R.string.faq_desc),
                        icon = Iconax.MessageQuestion,
                        iconColor = orange_dark,
                        iconBackground = orange_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.FAQ_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Top
                    )

                    AboutListItem(
                        title = stringResource(R.string.guides),
                        subtitle = stringResource(R.string.guides_desc),
                        icon = Iconax.DocumentText2,
                        iconColor = purple_dark,
                        iconBackground = purple_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.GUIDE_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.report_bug),
                        subtitle = stringResource(R.string.report_bug_desc),
                        icon = Iconax.Ghost,
                        iconColor = red_dark,
                        iconBackground = red_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.REPORT_BUG_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.privacy_policy),
                        subtitle = stringResource(R.string.privacy_policy_desc),
                        icon = Iconax.SecuritySafe,
                        iconColor = green_dark,
                        iconBackground = green_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.PRIVACY_POLICY_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.terms_of_service),
                        subtitle = stringResource(R.string.terms_of_service_desc),
                        icon = Iconax.DocumentText2,
                        iconColor = cyan_dark,
                        iconBackground = cyan_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.TERMS_OF_SERVICE_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.licenses),
                        subtitle = stringResource(R.string.licenses_desc),
                        icon = Iconax.Status,
                        iconColor = orange_dark,
                        iconBackground = orange_light,
                        onClick = onNavigateToLicenses,
                        position = ListItemPosition.Middle
                    )


                }

                Spacer(modifier = Modifier.height(Dimensions.Padding.card))

                Text(
                    text = stringResource(R.string.built_with_love),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                )

            }
        }
    }

    val availableRelease = updateState.available
    if (updateState.showDialog && availableRelease != null) {
        GitHubUpdateDialog(
            release = availableRelease,
            onDismiss = { updateViewModel.dismissDialog() },
            onDownload = { item ->
                updateViewModel.hideDialog()
                val target = item.apkUrl ?: item.htmlUrl
                if (target.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                }
            }
        )
    }

    if (showChannelDialog) {
        UpdateChannelDialog(
            selected = updateState.selectedChannel,
            options = updateState.selectableChannels,
            onSelect = { channel ->
                updateViewModel.selectChannel(channel)
                showChannelDialog = false
            },
            onDismiss = { showChannelDialog = false }
        )
    }


}

@Composable
private fun channelLabel(channel: PublishChannel): String = stringResource(
    when (channel) {
        PublishChannel.DEBUG -> R.string.update_channel_debug
        PublishChannel.TESTING -> R.string.update_channel_testing
        PublishChannel.RELEASE -> R.string.update_channel_release
    }
)

@Composable
private fun channelDescription(channel: PublishChannel): String = stringResource(
    when (channel) {
        PublishChannel.DEBUG -> R.string.update_channel_debug_desc
        PublishChannel.TESTING -> R.string.update_channel_testing_desc
        PublishChannel.RELEASE -> R.string.update_channel_release_desc
    }
)

@Composable
private fun UpdateChannelDialog(
    selected: PublishChannel,
    options: List<PublishChannel>,
    onSelect: (PublishChannel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_channel)) },
        text = {
            Column {
                options.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == channel,
                                role = Role.RadioButton,
                                onClick = { onSelect(channel) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The row is the target; the radio only shows the state
                        RadioButton(selected = selected == channel, onClick = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = channelLabel(channel),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = channelDescription(channel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@Composable
fun AboutListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    onClick: () -> Unit,
    isLink: Boolean = false,
    position: ListItemPosition = ListItemPosition.Middle,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headline = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        },
        supporting = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leading = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = iconBackground,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailing = {
            Icon(
                if (isLink) Iconax.ExportArrow02 else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}
