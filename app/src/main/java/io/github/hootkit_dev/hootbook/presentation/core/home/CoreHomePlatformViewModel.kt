package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class CoreHomePlatformViewModel @Inject constructor(val preferences:CoreUiPreferencesStore,val assets:CorePlatformAssets):ViewModel(){
    init{viewModelScope.launch{preferences.ready()}}
}
