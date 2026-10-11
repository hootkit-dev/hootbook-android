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
@Composable
fun CoreMerchantIconsScreen(onBack:()->Unit,merchant:String?=null,viewModel:CoreMerchantIconsViewModel=hiltViewModel()){
    LaunchedEffect(merchant){viewModel.initialize(merchant)}
    val name by viewModel.merchant.collectAsStateWithLifecycle();val busy by viewModel.busy.collectAsStateWithLifecycle();val error by viewModel.error.collectAsStateWithLifecycle();val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val revision by viewModel.assets.revision.collectAsStateWithLifecycle()
    val icon=remember(name,revision){viewModel.assets.merchantIcon(name)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),viewModel::pick)
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_ui_merchant_icons))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.core_ui_back))}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter){
            Column(Modifier.widthIn(max=720.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedTextField(name,viewModel::name,label={Text(stringResource(R.string.core_ui_merchant_name))},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                icon?.let{AsyncImage(it,stringResource(R.string.core_ui_merchant_icons),Modifier.size(96.dp))}
                Row{OutlinedButton(onClick={pick.launch(arrayOf("image/*"))},enabled=!busy&&name.isNotBlank()){Text(stringResource(R.string.core_ui_pick_image))};TextButton(onClick=viewModel::remove,enabled=!busy&&name.isNotBlank()){Text(stringResource(R.string.core_ui_remove))}}
                Text(stringResource(R.string.core_ui_icon_search_note),style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=viewModel::search,enabled=!busy&&name.isNotBlank()){Text(stringResource(R.string.core_ui_search_icons))}
                candidates.forEach{candidate->OutlinedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(12.dp)){Text(candidate.name);Text(candidate.seller,style=MaterialTheme.typography.bodySmall);AsyncImage(candidate.thumbnailUrl,candidate.name,Modifier.size(64.dp));TextButton(onClick={viewModel.use(candidate)},enabled=!busy){Text(stringResource(R.string.core_ui_use_icon))}}
                }}
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
