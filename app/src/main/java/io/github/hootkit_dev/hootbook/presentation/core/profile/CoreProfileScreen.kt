@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreColorPickerRow
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.debts.CorePersonRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.ErrorBanner
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormTextField
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionTitle
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.detailRow

/** How many contacts the profile lists before "View all". */
private const val CONTACTS_SHOWN = 5

/** The profile: banner, avatar and name, a financial overview, contacts and merchant icons. */
@Composable
fun CoreProfileScreen(onBack: () -> Unit, onContacts: () -> Unit = {}, onMerchantIcons: () -> Unit = {},
    viewModel: CoreProfileViewModel = hiltViewModel(), onPerson: (String) -> Unit = { onContacts() }, onAddPerson: () -> Unit = { onContacts() }) {
    val warnings by viewModel.preferences.migrationWarnings.collectAsStateWithLifecycle()
    val p by viewModel.preferences.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ledger by viewModel.ledgerState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    SecondaryScreen(title = stringResource(R.string.profile), onBack = { if (!state.busy) onBack() }, actions = {
        TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.edit_profile), { editing = true }, enabled = !state.busy)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            ProfileHeader(viewModel.assets.resolve(p.banner).takeIf { p.showBanner }, viewModel.assets.resolve(p.avatar), Color(p.profileBackground)) {
                Text(p.userName.ifBlank { stringResource(R.string.default_user_name) }, style = MaterialTheme.typography.titleLarge)
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.profile_transactions, ledger.transactionCount.toInt(), ledger.transactionCount.toInt()), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            warnings.forEach { NoticeCard(stringResource(it), error = true) }
            ledger.error?.let { ErrorBanner(stringResource(it), viewModel::refresh, actionLabel = stringResource(R.string.core_settings_retry), onAction = viewModel::refresh) }
            state.error?.let { NoticeCard(stringResource(it), error = true) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListSection(title = stringResource(R.string.financial_overview)) {
                    detailRow(stringResource(R.string.net_worth), ledger.overview?.netWorth?.let { CoreMoneyPresentation.format(it.amount, it.currency) } ?: "—",
                        error = ledger.overview?.netWorthComplete == false)
                    detailRow(stringResource(R.string.profile_total_income), ledger.summary?.let { CoreMoneyPresentation.format(it.income, it.currency) } ?: "—")
                    detailRow(stringResource(R.string.profile_total_expense), ledger.summary?.let { CoreMoneyPresentation.format(it.expense, it.currency) } ?: "—")
                    detailRow(stringResource(R.string.profile_subscriptions), ledger.subscriptions?.count?.let { stringResource(R.string.active_subscriptions_plural, it.toInt()) } ?: "—")
                }
                val incomplete = listOfNotNull(
                    stringResource(R.string.account_valuation_incomplete_detail).takeIf { ledger.overview?.netWorthComplete == false },
                    ledger.summary?.takeIf { !it.complete }?.let { stringResource(R.string.core_analytics_missing, it.missingCurrencies.joinToString()) })
                incomplete.forEach { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            ListSection(title = stringResource(R.string.contacts), action = if (ledger.contacts.isNotEmpty()) ({
                TextButton(onClick = onContacts) { Text(stringResource(R.string.view_all)) }
            }) else null) {
                ledger.contacts.take(CONTACTS_SHOWN).forEach { person ->
                    row { shape ->
                        CorePersonRow(person, avatar = { person.avatar?.let { CoreAttachmentImage(it, viewModel.attachments, Modifier.fillMaxSize()) } },
                            shape = shape) { onPerson(person.id) }
                    }
                }
                if (ledger.contacts.isEmpty()) item(leading = { Icon(Icons.Rounded.PersonAdd, null) }, headline = { Text(stringResource(R.string.profile_add_contact)) },
                    supporting = { Text(stringResource(R.string.profile_no_contacts)) }, onClick = onAddPerson)
            }
            ListSection {
                item(leading = { Icon(Icons.Rounded.Storefront, null) }, headline = { Text(stringResource(R.string.merchant_icons)) },
                    supporting = { Text(stringResource(R.string.merchant_icons_help)) }, chevron = true, onClick = onMerchantIcons)
            }
        }
    }
    if (editing) EditProfileSheet(viewModel) { editing = false }
}

/** The banner (or the bundled one) in a rounded card, the avatar over its bottom edge, then [details]. */
@Composable
private fun ProfileHeader(banner: Any?, avatar: Any?, background: Color, avatarSize: Dp = 96.dp, details: @Composable ColumnScope.() -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(140.dp + avatarSize / 2)) {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.extraLarge)) {
                if (banner != null) AsyncImage(banner, stringResource(R.string.banner), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.banner_bg_image), stringResource(R.string.banner), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            ProfileAvatar(avatar, background, Modifier.align(Alignment.BottomCenter).size(avatarSize)
                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        details()
    }
}

@Composable
private fun ProfileAvatar(model: Any?, color: Color, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(color)) {
        if (model != null) AsyncImage(model, stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.avatar_1), stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Name, avatar (preset or from the gallery) and its background, and the banner (own or bundled). Saved together. */
@Composable
private fun EditProfileSheet(viewModel: CoreProfileViewModel, onDismiss: () -> Unit) {
    val p by viewModel.preferences.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(p.userName) }
    var background by rememberSaveable { mutableIntStateOf(p.profileBackground) }
    var avatarPath by rememberSaveable { mutableStateOf(p.avatar) }
    var bannerPath by rememberSaveable { mutableStateOf(p.banner) }
    var showBanner by rememberSaveable { mutableStateOf(p.showBanner) }
    val avatar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.prepareImage(it) { path -> avatarPath = path } }
    val banner = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.prepareImage(it) { path -> bannerPath = path; showBanner = true } }
    val enabled = !state.busy
    FormSheet(title = stringResource(R.string.edit_profile), onDismiss = onDismiss, busy = state.busy, error = state.error?.let { stringResource(it) },
        onConfirm = { viewModel.save(name, background, showBanner, avatarPath, bannerPath, onDismiss) }) {
        ProfileHeader(viewModel.assets.resolve(bannerPath).takeIf { showBanner }, viewModel.assets.resolve(avatarPath), Color(background), avatarSize = 80.dp)
        FormTextField(name, stringResource(R.string.name_label), { name = it }, enabled = enabled)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.profile_avatar), indent = 0.dp, action = {
                Row {
                    TextButton(onClick = { avatar.launch(arrayOf("image/*")) }, enabled = enabled) { Text(stringResource(R.string.profile_pick_photo)) }
                    if (avatarPath != null) TextButton(onClick = { avatarPath = null }, enabled = enabled) { Text(stringResource(R.string.ui_clear)) }
                }
            })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(CoreProfileViewModel.PRESET_AVATARS) { res ->
                    Image(painterResource(res), stringResource(R.string.core_ui_avatar), Modifier.size(56.dp).clip(CircleShape)
                        .clickable(enabled = enabled) { viewModel.presetAvatar(res) { avatarPath = it } })
                }
            }
        }
        CoreColorPickerRow("#%06X".format(background and 0xFFFFFF), enabled, label = stringResource(R.string.profile_avatar_background)) { color ->
            background = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(background)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.banner), indent = 0.dp)
            ListSection {
                item(leading = { Icon(Icons.Rounded.Image, null) }, headline = { Text(stringResource(R.string.change_banner)) },
                    onClick = if (enabled) { { banner.launch(arrayOf("image/*")) } } else null)
                if (bannerPath != null && showBanner) item(leading = { Icon(Icons.Rounded.HideImage, null) }, headline = { Text(stringResource(R.string.profile_default_banner)) },
                    onClick = if (enabled) { { bannerPath = null; showBanner = false } } else null)
            }
        }
    }
}
