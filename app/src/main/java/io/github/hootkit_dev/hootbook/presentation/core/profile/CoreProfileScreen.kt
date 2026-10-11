@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.hootkit.core.PersonRelation
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

@Composable
fun CoreProfileScreen(onBack: () -> Unit, onContacts: () -> Unit = {}, onMerchantIcons: () -> Unit = {},
    viewModel: CoreProfileViewModel = hiltViewModel(), onPerson: (String) -> Unit = { onContacts() }, onAddPerson: () -> Unit = { onContacts() }) {
    val warnings by viewModel.preferences.migrationWarnings.collectAsStateWithLifecycle()
    val p by viewModel.preferences.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ledger by viewModel.ledgerState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(topBar = { CustomTitleTopAppBar(title = stringResource(R.string.profile), scrollBehaviorSmall = scroll, scrollBehaviorLarge = scroll,
        hasBackButton = true, navigationContent = { NavigationContent { if (!state.busy) onBack() } }, actionContent = {
            IconButton(onClick = { editing = true }, enabled = !state.busy) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
        }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                val banner = viewModel.assets.resolve(p.banner)
                if (banner != null && p.showBanner) AsyncImage(banner, stringResource(R.string.banner), Modifier.fillMaxSize().alpha(.5f), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.banner_bg_image), stringResource(R.string.banner), Modifier.fillMaxSize().alpha(.5f), contentScale = ContentScale.Crop)
                Spacer(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().height(180.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileAvatar(viewModel.assets.resolve(p.avatar), Color(p.profileBackground), Modifier.size(90.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(p.userName.ifBlank { stringResource(R.string.default_user_name) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.transactions_count_format, ledger.transactionCount.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(warnings) { Text(stringResource(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                ledger.error?.let { item { Text(stringResource(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.core_settings_retry)) } } }
                state.error?.let { item { Text(stringResource(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) } }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.financial_overview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileMetric(R.string.net_worth, ledger.overview?.netWorth?.let { CurrencyFormatter.formatCurrency(it.amount, it.currency) } ?: "—", Modifier.weight(1f))
                                ProfileMetric(R.string.upcoming, ledger.subscriptions?.count?.let { stringResource(R.string.active_subscriptions_plural, it.toInt()) } ?: "—", Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProfileMetric(R.string.expense, ledger.summary?.let { CurrencyFormatter.formatCurrency(it.expense, it.currency) } ?: "—", Modifier.weight(1f))
                                ProfileMetric(R.string.income, ledger.summary?.let { CurrencyFormatter.formatCurrency(it.income, it.currency) } ?: "—", Modifier.weight(1f))
                            }
                            if (ledger.overview?.netWorthComplete == false) Text(stringResource(R.string.account_valuation_incomplete_detail), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            ledger.summary?.takeIf { !it.complete }?.let { Text(stringResource(R.string.core_analytics_missing, it.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.contacts), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onContacts) { Text(stringResource(R.string.view_all)) }
                    }
                    if (ledger.contacts.isEmpty()) Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_contacts_desc), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onAddPerson) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_person)) }
                    } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ledger.contacts, key = { it.id }) { person ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(person.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                            Card(onClick = { onPerson(person.id) }, modifier = Modifier.width(120.dp).height(160.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color)) {
                                Box(Modifier.fillMaxSize()) {
                                    if (person.avatar != null) CoreAttachmentImage(person.avatar!!, viewModel.attachments, Modifier.fillMaxSize())
                                    else Text(person.name.take(1), Modifier.align(Alignment.Center), style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = .3f))
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = .9f)))))
                                    Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(stringResource(when (person.relation) { PersonRelation.FRIEND -> R.string.core_debts_friend; PersonRelation.FAMILY -> R.string.core_debts_family; PersonRelation.COLLEAGUE -> R.string.core_debts_colleague; else -> R.string.core_debts_other }), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
                item { TextButton(onClick = onMerchantIcons, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.core_ui_merchant_icons)) } }
            }
        }
    }
    if (editing) CoreEditProfileSheet(viewModel) { editing = false }
}

@Composable
private fun ProfileMetric(label: Int, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileAvatar(model: Any?, color: Color, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest).background(color)) {
        if (model != null) AsyncImage(model, stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.avatar_1), stringResource(R.string.profile), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CoreEditProfileSheet(viewModel: CoreProfileViewModel, onDismiss: () -> Unit) {
    val p by viewModel.preferences.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(p.userName) }
    var background by rememberSaveable { mutableIntStateOf(p.profileBackground) }
    var avatarPath by rememberSaveable { mutableStateOf(p.avatar) }
    var bannerPath by rememberSaveable { mutableStateOf(p.banner) }
    var showBanner by rememberSaveable { mutableStateOf(p.showBanner) }
    val avatar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.prepareImage(it) { path -> avatarPath = path } }
    val banner = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.prepareImage(it) { path -> bannerPath = path; showBanner = true } }
    HootbookModalBottomSheet(onDismissRequest = { if (!state.busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.edit_profile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))) {
                val image = viewModel.assets.resolve(bannerPath)
                if (image != null && showBanner) AsyncImage(image, stringResource(R.string.banner), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.banner_bg_image), stringResource(R.string.banner), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                ProfileAvatar(viewModel.assets.resolve(avatarPath), Color(background), Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(70.dp))
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_label)) }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(CoreProfileViewModel.PRESET_AVATARS) { res -> Image(painterResource(res), stringResource(R.string.core_ui_avatar), Modifier.size(80.dp).clip(CircleShape).clickable(enabled = !state.busy) { viewModel.presetAvatar(res) { avatarPath = it } }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { avatar.launch(arrayOf("image/*")) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.gallery)) }
                FilledTonalButton(onClick = { avatarPath = null }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear)) }
            }
            FilledTonalButton(onClick = { banner.launch(arrayOf("image/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_banner)) }
            TextButton(onClick = { bannerPath = null }, enabled = !state.busy) { Text(stringResource(R.string.core_ui_remove)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.core_ui_show_banner), Modifier.weight(1f)); Switch(showBanner, { showBanner = it }, enabled = !state.busy) }
            ColorPickerContent(initialColor = background, onColorChanged = { if (!state.busy) background = it })
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.save(name, background, showBanner, avatarPath, bannerPath, onDismiss) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
        }
    }
}
