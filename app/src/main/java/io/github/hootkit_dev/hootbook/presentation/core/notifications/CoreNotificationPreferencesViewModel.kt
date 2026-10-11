package io.github.hootkit_dev.hootbook.presentation.core.notifications
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.Subscription
import dev.hootkit.core.Table
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferences
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.core.system.CoreSystemCoordinator
import io.github.hootkit_dev.hootbook.core.system.coreDate
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class CoreNotificationPreferencesViewModel @Inject constructor(val prefs:CoreUiPreferencesStore,
    private val session:CoreSession,changes:ChangeBus,private val coordinator:CoreSystemCoordinator):ViewModel(){
    private val rows=MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions=rows.asStateFlow()
    private val failure=MutableStateFlow<Int?>(null);val error=failure.asStateFlow()
    private val working=MutableStateFlow(false);val busy=working.asStateFlow()
    private var query:Job?=null;private var version=0L
    init {viewModelScope.launch{try{prefs.ready()}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){failure.value=R.string.core_system_preferences_failed}}
        viewModelScope.launch{changes.observe(setOf(Table.SUBSCRIPTIONS)).collect{reload()}}
    }
    fun reload(){val ticket=++version;query?.cancel();query=viewModelScope.launch{
        try{val list=session.awaitCore().plans().use{it.subscriptions(LocalDate.now().coreDate(),true)}
            if(ticket==version){rows.value=list;failure.value=null}}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){if(ticket==version)failure.value=R.string.core_system_subscriptions_failed}
    }}
    fun update(transform:(CoreUiPreferences)->CoreUiPreferences){if(working.value)return;viewModelScope.launch{
        working.value=true
        try{prefs.update(transform);failure.value=null;coordinator.refresh()}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){failure.value=R.string.core_system_preferences_failed}
        finally{working.value=false}
    }}
    fun mute(id:String,muted:Boolean)=update{it.copy(mutedSubscriptionIds=if(muted)it.mutedSubscriptionIds+id else it.mutedSubscriptionIds-id)}
    fun refreshPlatform(){coordinator.refresh()}
}
