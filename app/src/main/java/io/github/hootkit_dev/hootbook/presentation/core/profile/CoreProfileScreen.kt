@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreProfileScreen(onBack:()->Unit,onContacts:()->Unit={},onMerchantIcons:()->Unit={},viewModel:CoreProfileViewModel=hiltViewModel()){
    val warnings by viewModel.preferences.migrationWarnings.collectAsStateWithLifecycle()
    val p by viewModel.preferences.state.collectAsStateWithLifecycle();val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(p.userName){mutableStateOf(p.userName)}
    var background by rememberSaveable(p.profileBackground){mutableStateOf(p.profileBackground.toString())}
    var showBanner by rememberSaveable(p.showBanner){mutableStateOf(p.showBanner)}
    val avatar=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){viewModel.image(it,false)}
    val banner=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){viewModel.image(it,true)}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_ui_profile))},navigationIcon={TextButton(onClick=onBack,enabled=!state.busy){Text(stringResource(R.string.core_ui_back))}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter){
            Column(Modifier.widthIn(max=720.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                warnings.forEach{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                state.error?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
                OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.core_ui_name))},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),singleLine=true)
                viewModel.assets.resolve(p.avatar)?.let{AsyncImage(it,stringResource(R.string.core_ui_avatar),Modifier.size(112.dp))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={avatar.launch(arrayOf("image/*"))},enabled=!state.busy){Text(stringResource(R.string.core_ui_avatar))};TextButton(onClick={viewModel.removeImage(false)},enabled=!state.busy){Text(stringResource(R.string.core_ui_remove))}}
                viewModel.assets.resolve(p.banner)?.let{AsyncImage(it,stringResource(R.string.core_ui_banner),Modifier.fillMaxWidth().height(180.dp))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={banner.launch(arrayOf("image/*"))},enabled=!state.busy){Text(stringResource(R.string.core_ui_banner))};TextButton(onClick={viewModel.removeImage(true)},enabled=!state.busy){Text(stringResource(R.string.core_ui_remove))}}
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(showBanner,{showBanner=it},enabled=!state.busy);Text(stringResource(R.string.core_ui_show_banner))}
                OutlinedTextField(background,{background=it},label={Text(stringResource(R.string.core_ui_background))},singleLine=true,enabled=!state.busy,isError=background.toIntOrNull()==null)
                Button(onClick={viewModel.save(name,background.toIntOrNull()?:0,showBanner)},enabled=!state.busy&&background.toIntOrNull()!=null){Text(stringResource(R.string.core_ui_save))}
                OutlinedButton(onClick=onMerchantIcons,enabled=!state.busy){Text(stringResource(R.string.core_ui_merchant_icons))}
                OutlinedButton(onClick=onContacts,enabled=!state.busy){Text(stringResource(R.string.core_ui_contacts))}
            }
        }
    }
}
