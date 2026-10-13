@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.MerchantIconSheet
import io.github.hootkit_dev.hootbook.presentation.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class MerchantIconsViewModel @Inject constructor(val assets: CorePlatformAssets) : ViewModel() {
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val failure = MutableStateFlow<Int?>(null)
    val error = failure.asStateFlow()
    fun pick(name: String, uri: Uri?) { if (uri != null) command(name) { assets.saveMerchantIcon(name, uri) } }
    fun remove(name: String) = command(name) { assets.removeMerchantIcon(name) }
    private fun command(name: String, block: suspend () -> Unit) {
        if (working.value || name.isBlank() || name.length > 200) return
        working.value = true; failure.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure.value = R.string.core_ui_operation_failed }
            finally { working.value = false }
        }
    }
}

/** Main had a merchant icon sheet, but no management destination. Reuse that original sheet. */
@Composable
fun MerchantIconsScreen(onNavigateBack: () -> Unit, merchant: String? = null, viewModel: MerchantIconsViewModel = hiltViewModel()) {
    var name by rememberSaveable(merchant) { mutableStateOf(merchant.orEmpty()) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val revision by viewModel.assets.revision.collectAsStateWithLifecycle()
    val image = remember(name, revision) { viewModel.assets.merchantIcon(name) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.pick(name, it) }
    val haze = remember { HazeState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pinned = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(topBar = { CustomTitleTopAppBar(title = stringResource(R.string.merchant_icon_title),
        scrollBehaviorSmall = pinned, scrollBehaviorLarge = scroll, hazeState = haze,
        hasBackButton = true, navigationContent = { NavigationContent(onNavigateBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().hazeSource(haze).verticalScroll(rememberScrollState()).padding(padding)
            .padding(Dimensions.Padding.content), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextField(
                name,
                { name = it },
                enabled = !busy,
                label = { Text(stringResource(R.string.core_ui_merchant_name)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = hootbookTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            image?.let { AsyncImage(it, name, Modifier.size(96.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                ListItem(headline = { Text(stringResource(R.string.merchant_icon_search_hint)) },
                    trailing = { Icon(Icons.Rounded.ChevronRight, null) },
                    onClick = if (name.isBlank() || busy) null else ({ showSearch = true }), shape = ListItemPosition.Top.toShape(), padding = PaddingValues(0.dp))
                ListItem(headline = { Text(stringResource(R.string.core_ui_pick_image)) },
                    onClick = if (name.isBlank() || busy) null else ({ pick.launch(arrayOf("image/*")) }), shape = ListItemPosition.Middle.toShape(), padding = PaddingValues(0.dp))
                ListItem(headline = { Text(stringResource(R.string.core_ui_remove)) },
                    onClick = if (image == null || busy) null else ({ viewModel.remove(name) }), shape = ListItemPosition.Bottom.toShape(), padding = PaddingValues(0.dp))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showSearch) MerchantIconSheet(name, onDismiss = { showSearch = false })
}
