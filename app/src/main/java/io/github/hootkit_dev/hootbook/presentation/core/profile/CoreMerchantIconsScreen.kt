@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.data.icons.AppIconCandidate
import io.github.hootkit_dev.hootbook.data.icons.AppStoreIconSearch
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormTextField
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.NoticeCard
import io.github.hootkit_dev.hootbook.presentation.ui.components.ScreenContentPadding
import io.github.hootkit_dev.hootbook.presentation.ui.components.SecondaryScreen
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionSpacing
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoreMerchantIconsViewModel @Inject constructor(val assets:CorePlatformAssets,private val search:AppStoreIconSearch):ViewModel(){
    val merchant=MutableStateFlow("");private val working=MutableStateFlow(false);val busy=working.asStateFlow()
    private val failure=MutableStateFlow<Int?>(null);val error=failure.asStateFlow()
    private val results=MutableStateFlow<List<AppIconCandidate>>(emptyList());val candidates=results.asStateFlow()
    fun initialize(name:String?){if(merchant.value.isBlank()&&name!=null)merchant.value=name}
    fun name(value:String){if(!working.value){merchant.value=value;results.value=emptyList()}}
    fun pick(uri:Uri?){if(uri!=null)command{assets.saveMerchantIcon(merchant.value,uri)}}
    fun remove()=command{assets.removeMerchantIcon(merchant.value)}
    fun search()=command{results.value=search.search(merchant.value)}
    fun use(candidate:AppIconCandidate)=command{val bitmap=search.download(candidate);try{assets.saveMerchantIcon(merchant.value,bitmap)}finally{bitmap.recycle()}}
    private fun command(block:suspend()->Unit){if(working.value||merchant.value.isBlank()||merchant.value.length>200)return;working.value=true;failure.value=null;viewModelScope.launch{
        try{block()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failure.value=R.string.core_ui_operation_failed}finally{working.value=false}
    }}
}
/**
 * The icon a merchant shows with: the current one, a photo from the gallery, or an App Store icon
 * found by searching the name (only when the user asks; the name is sent to Apple's search).
 */
@Composable
fun CoreMerchantIconsScreen(onBack: () -> Unit, merchant: String? = null, viewModel: CoreMerchantIconsViewModel = hiltViewModel()) {
    LaunchedEffect(merchant) { viewModel.initialize(merchant) }
    val name by viewModel.merchant.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val revision by viewModel.assets.revision.collectAsStateWithLifecycle()
    val custom = remember(name, revision) { viewModel.assets.merchantIcon(name) != null }
    val brand = remember(name) { io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons.getIconResource(name) != null }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::pick)
    val enabled = !busy && name.isNotBlank()
    SecondaryScreen(title = stringResource(R.string.merchant_icons), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(ScreenContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
            FormTextField(name, stringResource(R.string.core_ui_merchant_name), viewModel::name, enabled = !busy)
            error?.let { NoticeCard(stringResource(it), error = true) }
            ListSection(title = stringResource(R.string.micon_current)) {
                item(leading = { CoreMerchantIcon(name, viewModel.assets, Modifier.size(48.dp)) },
                    headline = { Text(name.ifBlank { stringResource(R.string.micon_enter_name) }) },
                    supporting = { Text(stringResource(when { custom -> R.string.micon_custom; brand -> R.string.micon_brand; else -> R.string.micon_none })) })
                item(leading = { Icon(Icons.Rounded.PhotoLibrary, null) }, headline = { Text(stringResource(R.string.profile_pick_photo)) },
                    onClick = if (enabled) { { pick.launch(arrayOf("image/*")) } } else null)
                if (custom) item(leading = { Icon(Icons.Rounded.Delete, null) }, headline = { Text(stringResource(R.string.micon_remove)) },
                    onClick = if (enabled) viewModel::remove else null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListSection(title = stringResource(R.string.micon_app_store)) {
                    item(leading = { Icon(Icons.Rounded.Search, null) }, headline = { Text(stringResource(R.string.core_ui_search_icons)) },
                        onClick = if (enabled) viewModel::search else null)
                    candidates.forEach { candidate ->
                        item(leading = { AsyncImage(candidate.thumbnailUrl, candidate.name, Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)) },
                            headline = { Text(candidate.name, maxLines = 1) }, supporting = { Text(candidate.seller, maxLines = 1) },
                            trailing = { TextButton(onClick = { viewModel.use(candidate) }, enabled = !busy) { Text(stringResource(R.string.micon_use)) } })
                    }
                }
                Text(stringResource(R.string.micon_search_note), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A merchant's icon in a circle: the one the user picked, else a bundled brand logo, else
 * [fallback] (a record icon name) or a generic receipt on the secondary container.
 */
@Composable
fun CoreMerchantIcon(merchant: String, assets: CorePlatformAssets, modifier: Modifier = Modifier, fallback: String? = null) {
    val revision by assets.revision.collectAsStateWithLifecycle()
    val image = remember(merchant, revision) { assets.merchantIcon(merchant) }
    val brand = remember(merchant) { io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons.getIconResource(merchant) }
    androidx.compose.foundation.layout.Box(modifier.clip(androidx.compose.foundation.shape.CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        when {
            image != null || brand != null -> AsyncImage(image ?: brand, merchant, Modifier.fillMaxSize())
            !fallback.isNullOrBlank() -> io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon(fallback, Modifier.fillMaxSize(0.62f))
            else -> Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, Modifier.fillMaxSize(0.5f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
