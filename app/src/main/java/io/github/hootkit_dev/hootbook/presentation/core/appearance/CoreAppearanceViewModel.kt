package io.github.hootkit_dev.hootbook.presentation.core.appearance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.data.preferences.AppIcon
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController
import io.github.hootkit_dev.hootbook.utils.IconSwitchingUtils
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CoreAppearanceViewModel @Inject constructor(@ApplicationContext private val context:Context,
    val preferences:CoreUiPreferencesStore,val locale:CoreLocaleController,private val session:CoreSession):ViewModel(){
    private val mutable=MutableStateFlow(false);val busy=mutable.asStateFlow()
    private val failure=MutableStateFlow<Int?>(null);val error=failure.asStateFlow()
    init{command{preferences.ready()}}
    fun update(transform:(CoreUiPreferences)->CoreUiPreferences)=command{preferences.update(transform)}
    fun appIcon(icon:AppIcon)=command{
        val previous=preferences.ready().appIcon
        try{withContext(Dispatchers.Main.immediate){IconSwitchingUtils.switchAppIcon(context,icon)};preferences.update{it.copy(appIcon=icon)}}
        catch(failure:Throwable){try{IconSwitchingUtils.switchAppIcon(context,previous)}catch(rollback:Throwable){failure.addSuppressed(rollback)};throw failure}
    }
    fun language(selection:String)=command{
        val previous=locale.selection();val settings=session.awaitCore().settings()
        try{settings.setLocale(locale.effectiveTag(selection));try{locale.apply(selection)}catch(failure:Throwable){
            try{settings.setLocale(locale.effectiveTag(previous))}catch(rollback:Throwable){failure.addSuppressed(rollback)};throw failure
        }}finally{settings.close()}
    }
    private fun command(block:suspend()->Unit){if(mutable.value)return;mutable.value=true;failure.value=null;viewModelScope.launch{
        try{block()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failure.value=R.string.core_ui_operation_failed}finally{mutable.value=false}
    }}
}
