package io.github.hootkit_dev.hootbook.presentation.ui.features.settings

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.LanguageSelectionBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ArchiveBook
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Box2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DollarCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DirectboxSend
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Fireworks7
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.NotificationBing
import io.github.hootkit_dev.hootbook.presentation.ui.icons.SecuritySafe
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Status
import io.github.hootkit_dev.hootbook.presentation.ui.icons.VideoTime
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.orange_dark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToTags: () -> Unit = {},
    onNavigateToDuplicates: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onNavigateToQuickTemplates: () -> Unit = {},
    onNavigateToAiAssistant: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToLendBorrow: () -> Unit = {},
    onNavigateToDataPrivacy: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToCurrency: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    blurEffects: Boolean
) {
    val operationError by settingsViewModel.error.collectAsStateWithLifecycle()
    val totalTransactionsCount by settingsViewModel.totalTransactions.collectAsStateWithLifecycle()
    val userPreferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    val currentLanguageCode = remember(androidx.compose.ui.platform.LocalConfiguration.current) {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            locales.get(0)?.language ?: "en"
        } else {
            java.util.Locale.getDefault().language
        }
    }
    val currentLanguageRes = remember(currentLanguageCode) {
 when (currentLanguageCode) {
            "en" -> R.string.english
            "af" -> R.string.afrikaans
            "ar" -> R.string.arabic
            "az" -> R.string.azerbaijani
            "bg" -> R.string.bulgarian
            "bn" -> R.string.bengali
            "bo" -> R.string.tibetan
            "ca" -> R.string.catalan
            "cs" -> R.string.czech
            "da" -> R.string.danish
            "de" -> R.string.german
            "dz" -> R.string.dzongkha
            "el" -> R.string.greek
            "es" -> R.string.spanish
            "fa" -> R.string.persian
            "fr" -> R.string.french
            "gu" -> R.string.gujarati
            "haw" -> R.string.hawaiian
            "he" -> R.string.hebrew
            "hi" -> R.string.hindi
            "hr" -> R.string.croatian
            "hu" -> R.string.hungarian
            "id" -> R.string.indonesian
            "is" -> R.string.icelandic
            "it" -> R.string.italiano
            "ja" -> R.string.japanese
            "kab" -> R.string.kabyle
            "kn" -> R.string.kannada
            "ks" -> R.string.kashmiri
            "la" -> R.string.latin
            "ml" -> R.string.malayalam
            "mr" -> R.string.marathi
            "ne" -> R.string.nepali
            "nl" -> R.string.dutch
            "no" -> R.string.norwegian
            "ny" -> R.string.chichewa
            "or" -> R.string.odia
            "os" -> R.string.iron
            "pa" -> R.string.punjabi
            "pl" -> R.string.polish
            "pt" -> R.string.portuguese
            "ro" -> R.string.romanian
            "ru" -> R.string.russian
            "sk" -> R.string.slovak
            "sl" -> R.string.slovenian
            "sv" -> R.string.swedish
            "ta" -> R.string.tamil
            "te" -> R.string.telugu
            "th" -> R.string.thai
            "tk" -> R.string.turkmençe
            "tr" -> R.string.turkish
            "uk" -> R.string.ukrainian
            "ur" -> R.string.urdu
            "uz" -> R.string.uzbek
            "val" -> R.string.valencian
            "vi" -> R.string.vietnamese
            "zh" -> R.string.chinese
            else -> null
        }
    }
    val currentLanguageName = if (currentLanguageRes != null) {
        stringResource(currentLanguageRes)
    } else {
        java.util.Locale.forLanguageTag(currentLanguageCode).displayName
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    val errorSnackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val operationFailed = stringResource(R.string.onboarding_operation_failed)
    androidx.compose.runtime.LaunchedEffect(operationError) { operationError?.let { errorSnackbar.showSnackbar(operationFailed) } }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(errorSnackbar) },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.settings),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(
                    state = rememberScrollState()
                )
                .padding(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content +
                            paddingValues.calculateTopPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                SettingsGroupLabel(stringResource(R.string.settings_group_personal))
                val profileImageUri = settingsViewModel.assets.resolve(userPreferences.avatar)?.let { android.net.Uri.fromFile(it) }
                val profileBackgroundColor = Color(userPreferences.profileBackground)

                ListItem(
                    headline = {
                        Text(
                            text = userPreferences.userName.ifBlank { stringResource(R.string.default_user_name) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    supporting = {
                        Text(
                            text = stringResource(R.string.transactions_count_short, totalTransactionsCount.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .background(profileBackgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileImageUri != null) {
                                AsyncImage(
                                    model = profileImageUri,
                                    contentDescription = stringResource(R.string.profile),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.avatar_1),
                                    contentDescription = stringResource(R.string.profile),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    onClick = { onNavigateToProfile() },
                    shape = ListItemPosition.Top.toShape(),
                    listColor = MaterialTheme.colorScheme.primaryContainer,
                    padding = PaddingValues(0.dp)
                )

                ListItem(
                    headline = {
                        Text(
                            text = stringResource(R.string.appearances),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = stringResource(R.string.appearances_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { onNavigateToAppearance() },
                    shape = ListItemPosition.Middle.toShape(),
                    padding = PaddingValues(0.dp)
                )

                ListItem(
                    headline = {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = stringResource(R.string.language_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    },
                    trailing = {
                        LanguageTrailing(languageName = currentLanguageName)
                    },
                    onClick = { showLanguageBottomSheet = true },
                    shape = ListItemPosition.Middle.toShape(),
                    padding = PaddingValues(0.dp)
                )

                // Currency
                ListItem(
                    headline = {
                        Text(
                            text = stringResource(R.string.currency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = stringResource(R.string.currency_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Iconax.DollarCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { onNavigateToCurrency() },
                    shape = ListItemPosition.Bottom.toShape(),
                    padding = PaddingValues(0.dp)
                )

                Spacer(modifier = Modifier.height(Spacing.md))
                SettingsGroupLabel(stringResource(R.string.settings_group_finances))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    // Manage Accounts
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.title_accounts),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.accounts_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                            contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToManageAccounts() },
                        shape = ListItemPosition.Top.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Budgets
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.budgets),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.budgets_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Status,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToBudgets() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Subscriptions
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.subscriptions),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.subscriptions_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.VideoTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToSubscriptions() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Lendings & Borrowings (Khata)
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.lend_borrow_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.loan_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.ArchiveBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToLendBorrow() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Categories
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.categories),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.categories_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Box2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToCategories() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Tags
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.core_tags_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.core_tags_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Box2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToTags() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Duplicate Detection
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.core_duplicates_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.core_duplicates_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Status,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToDuplicates() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Quick templates
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.quick_add_templates),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.quick_templates_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToQuickTemplates() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // AI bookkeeping
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.ai_assistant_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.ai_assistant_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToAiAssistant() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Data Privacy
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.data_privacy_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.data_privacy_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.SecuritySafe,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToDataPrivacy() },
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp)
                    )

                    // Local backup and restore
                    ListItem(
                        headline = {
                            Text(stringResource(R.string.core_shell_backup),
                                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        },
                        supporting = {
                            Text(stringResource(R.string.core_shell_backup_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leading = {
                            Box(
                                modifier = Modifier.size(48.dp).background(
                                    MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Iconax.DirectboxSend, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                        trailing = {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        onClick = onNavigateToBackup,
                        shape = ListItemPosition.Middle.toShape(),
                        padding = PaddingValues(0.dp),
                    )

                    // Ledger sync
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.core_shell_sync),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.core_shell_sync_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToSync() },
                        shape = ListItemPosition.Bottom.toShape(),
                        padding = PaddingValues(0.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                SettingsGroupLabel(stringResource(R.string.settings_group_automation))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    // Notifications
                    ListItem(
                        headline = {
                            Text(
                                text = stringResource(R.string.notifications),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supporting = {
                            Text(
                                text = stringResource(R.string.notifications_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.NotificationBing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { onNavigateToNotifications() },
                        shape = ListItemPosition.Single.toShape(),
                        padding = PaddingValues(0.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                SettingsGroupLabel(stringResource(R.string.settings_group_app))
                ListItem(
                    headline = {
                        Text(
                            text = stringResource(R.string.about),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supporting = {
                        Text(
                            text = stringResource(R.string.about_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.hootbook_icon_white),
                                contentDescription = stringResource(R.string.hootbook_logo_cd),
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { onNavigateToAbout() },
                    shape = ListItemPosition.Single.toShape(),
                    padding = PaddingValues(0.dp)
                )
                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        if (showLanguageBottomSheet) {
            LanguageSelectionBottomSheet(
                selectedLanguageCode = currentLanguageCode,
                onLanguageSelected = { code ->
                    settingsViewModel.language(code)
                },
                onDismiss = { showLanguageBottomSheet = false }
            )
        }
    }
}

@Composable
fun LanguageTrailing(
    languageName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.widthIn(max = 120.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = languageName,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsGroupLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 16.dp, top = 8.dp, bottom = 10.dp)
    )
}
