@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.data.preferences.*

@Composable
fun CoreAppearanceScreen(onBack:()->Unit,onAppLock:()->Unit={},viewModel:CoreAppearanceViewModel=hiltViewModel()) {
    val p by viewModel.preferences.state.collectAsStateWithLifecycle();val busy by viewModel.busy.collectAsStateWithLifecycle();val error by viewModel.error.collectAsStateWithLifecycle()
    val context=LocalContext.current;val configuration=LocalConfiguration.current
    val languages=remember(context){CoreUiLanguages.supported(context)}
    val selection=remember(configuration,busy){viewModel.locale.selection()}
    var displayCurrency by rememberSaveable(p.unifiedDisplayCurrency){mutableStateOf(p.unifiedDisplayCurrency.orEmpty())}
    var entryCurrency by rememberSaveable(p.defaultEntryCurrency){mutableStateOf(p.defaultEntryCurrency.orEmpty())}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_ui_appearance))},navigationIcon={TextButton(onClick=onBack,enabled=!busy){Text(stringResource(R.string.core_ui_back))}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter){
            LazyColumn(Modifier.widthIn(max=720.dp).fillMaxWidth().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};error?.let{item{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}}
                item{Text(stringResource(R.string.core_ui_theme),style=MaterialTheme.typography.titleLarge);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(null to R.string.core_ui_system_theme,false to R.string.core_ui_light,true to R.string.core_ui_dark).forEach{(value,label)->FilterChip(selected=p.darkTheme==value,onClick={viewModel.update{it.copy(darkTheme=value)}},enabled=!busy,label={Text(stringResource(label))})}
                }}
                item{UiSwitch(R.string.core_ui_dynamic,p.dynamicColor,!busy){v->viewModel.update{it.copy(dynamicColor=v)}}}
                item{UiSwitch(R.string.core_ui_amoled,p.amoled,!busy){v->viewModel.update{it.copy(amoled=v)}}}
                item{UiSwitch(R.string.core_ui_blur,p.blur,!busy){v->viewModel.update{it.copy(blur=v)}}}
                item{Choice(R.string.core_ui_theme_style,p.themeStyle,ThemeStyle.entries,!busy){v->viewModel.update{it.copy(themeStyle=v)}}}
                item{Choice(R.string.core_ui_font,p.font,AppFont.entries,!busy){v->viewModel.update{it.copy(font=v)}}}
                item{Choice(R.string.core_ui_accent,p.accent,AccentColor.entries,!busy){v->viewModel.update{it.copy(accent=v)}}}
                item{Choice(R.string.core_ui_icon,p.appIcon,AppIcon.entries,!busy,viewModel::appIcon)}
                item{HorizontalDivider();Text(stringResource(R.string.core_ui_language),style=MaterialTheme.typography.titleLarge)
                    var expanded by remember{mutableStateOf(false)}
                    OutlinedButton(onClick={expanded=true},enabled=!busy){Text(languages.firstOrNull{it.first==selection}?.second?:if(selection.isNotEmpty())java.util.Locale.forLanguageTag(selection).getDisplayName(java.util.Locale.forLanguageTag(selection))else stringResource(R.string.core_ui_system_theme))}
                    DropdownMenu(expanded,{expanded=false},Modifier.heightIn(max=360.dp)){
                        DropdownMenuItem(text={Text(stringResource(R.string.core_ui_system_theme))},onClick={expanded=false;viewModel.language("")})
                        languages.forEach{(tag,label)->DropdownMenuItem(text={Text(label)},onClick={expanded=false;viewModel.language(tag)})}
                    }
                }
                item{HorizontalDivider();Text(stringResource(R.string.core_ui_display_currency),style=MaterialTheme.typography.titleLarge)
                    OutlinedTextField(displayCurrency,{displayCurrency=it.uppercase(java.util.Locale.ROOT)},label={Text(stringResource(R.string.core_ui_currency_code))},singleLine=true,enabled=!busy)
                    UiSwitch(R.string.core_ui_unified,p.unifiedDisplayEnabled,!busy){v->viewModel.update{it.copy(unifiedDisplayEnabled=v)}}
                    Button(onClick={viewModel.update{it.copy(unifiedDisplayCurrency=displayCurrency.takeIf(String::isNotBlank))}},enabled=!busy&&(displayCurrency.isEmpty()||displayCurrency.matches(Regex("[A-Z0-9]{1,4}")))){Text(stringResource(R.string.core_ui_save))}
                }
                item{Text(stringResource(R.string.core_ui_entry_currency),style=MaterialTheme.typography.titleMedium)
                    OutlinedTextField(entryCurrency,{entryCurrency=it.uppercase(java.util.Locale.ROOT)},label={Text(stringResource(R.string.core_ui_currency_code))},singleLine=true,enabled=!busy)
                    UiSwitch(R.string.core_ui_default_entry,p.defaultEntryEnabled,!busy){v->viewModel.update{it.copy(defaultEntryEnabled=v)}}
                    Button(onClick={viewModel.update{it.copy(defaultEntryCurrency=entryCurrency.takeIf(String::isNotBlank))}},enabled=!busy&&(entryCurrency.isEmpty()||entryCurrency.matches(Regex("[A-Z0-9]{1,4}")))){Text(stringResource(R.string.core_ui_save))}
                }
                item{HorizontalDivider();Text(stringResource(R.string.core_ui_home_layout),style=MaterialTheme.typography.titleLarge)}
                p.homeOrder.forEachIndexed{index,widget->item(key=widget.name){
                    Column{UiSwitch(widget.titleRes,widget !in p.hiddenHomeWidgets,!busy){v->viewModel.update{it.copy(hiddenHomeWidgets=if(v)it.hiddenHomeWidgets-widget else it.hiddenHomeWidgets+widget)}}
                        Row{TextButton(onClick={viewModel.update{val order=it.homeOrder.toMutableList();java.util.Collections.swap(order,index,index-1);it.copy(homeOrder=order)}},enabled=!busy&&index>0){Text(stringResource(R.string.core_ui_move_up))}
                            TextButton(onClick={viewModel.update{val order=it.homeOrder.toMutableList();java.util.Collections.swap(order,index,index+1);it.copy(homeOrder=order)}},enabled=!busy&&index<p.homeOrder.lastIndex){Text(stringResource(R.string.core_ui_move_down))}}
                    }
                }}
                item{TextButton(onClick={viewModel.update{it.copy(homeOrder=HomeWidget.entries.sortedBy{it.defaultOrder},hiddenHomeWidgets=emptySet())}},enabled=!busy){Text(stringResource(R.string.core_ui_reset_layout))}}
                item{UiSwitch(R.string.core_ui_developer,p.developerMode,!busy){v->viewModel.update{it.copy(developerMode=v)}};UiSwitch(R.string.core_ui_tokens,p.tokenInfo,!busy){v->viewModel.update{it.copy(tokenInfo=v)}}}
                item{OutlinedButton(onClick=onAppLock,enabled=!busy){Text(stringResource(R.string.core_ui_app_lock))}}
            }
        }
    }
}
@Composable
internal fun UiSwitch(label:Int,value:Boolean,enabled:Boolean,changed:(Boolean)->Unit){val name=stringResource(label);Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(name,Modifier.weight(1f).padding(end=12.dp));Switch(value,changed,Modifier.semantics{contentDescription=name},enabled=enabled)}}
@Composable private fun <E:Enum<E>> Choice(label:Int,selected:E,values:List<E>,enabled:Boolean,change:(E)->Unit){
    var expanded by remember{mutableStateOf(false)};Text(stringResource(label),style=MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick={expanded=true},enabled=enabled){Text(selected.name)}
    DropdownMenu(expanded,{expanded=false},Modifier.heightIn(max=360.dp)){values.forEach{v->DropdownMenuItem(text={Text(v.name)},onClick={expanded=false;change(v)},enabled=enabled)}}
}
