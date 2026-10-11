package io.github.hootkit_dev.hootbook.presentation.core.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoreProfileUiState(val busy:Boolean=true,val error:Int?=null)
@HiltViewModel
class CoreProfileViewModel @Inject constructor(val preferences:CoreUiPreferencesStore,val assets:CorePlatformAssets):ViewModel(){
    private val mutable=MutableStateFlow(CoreProfileUiState(false));val state=mutable.asStateFlow()
    init {command{preferences.ready()}}
    fun save(name:String,background:Int,showBanner:Boolean)=command{preferences.update{it.copy(userName=name.trim(),profileBackground=background,showBanner=showBanner)}}
    fun image(uri:Uri?,banner:Boolean){if(uri!=null)command{val path=assets.importImage(uri);preferences.update{if(banner)it.copy(banner=path) else it.copy(avatar=path)}}}
    fun removeImage(banner:Boolean)=command{preferences.update{if(banner)it.copy(banner=null)else it.copy(avatar=null)}}
    private fun command(block:suspend()->Unit){if(mutable.value.busy)return;mutable.value=CoreProfileUiState(true);viewModelScope.launch{
        try{block();mutable.value=CoreProfileUiState(false)}catch(failure:CancellationException){throw failure}catch(_:Exception){mutable.value=CoreProfileUiState(false,R.string.core_ui_operation_failed)}
    }}
}
