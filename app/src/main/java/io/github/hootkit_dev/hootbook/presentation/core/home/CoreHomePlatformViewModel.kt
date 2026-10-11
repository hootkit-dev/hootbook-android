package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.launch
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget

@HiltViewModel
class CoreHomePlatformViewModel @Inject constructor(val preferences:CoreUiPreferencesStore,val assets:CorePlatformAssets):ViewModel(){
    init{viewModelScope.launch{preferences.ready()}}
    fun setWidgetVisible(widget: HomeWidget, visible: Boolean) = update { current ->
        current.copy(hiddenHomeWidgets = if (visible) current.hiddenHomeWidgets - widget else current.hiddenHomeWidgets + widget)
    }
    fun reorderWidgets(order: List<HomeWidget>) = update { current ->
        current.copy(homeOrder = (listOf(HomeWidget.NETWORTH_SUMMARY, HomeWidget.ACCOUNT_CAROUSEL) + order + current.homeOrder).distinct())
    }
    fun resetLayout() = update { it.copy(homeOrder = HomeWidget.entries.sortedBy { widget -> widget.defaultOrder }, hiddenHomeWidgets = emptySet()) }
    fun toggleBanner() = update { it.copy(showBanner = !it.showBanner) }
    private fun update(transform: (io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferences) -> io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferences) {
        viewModelScope.launch { preferences.update(transform) }
    }
}
