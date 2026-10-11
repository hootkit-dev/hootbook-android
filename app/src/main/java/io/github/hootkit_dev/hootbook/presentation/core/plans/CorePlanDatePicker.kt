@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreTextField
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Calendar/clock input adaptation only: UTC picker dates never shift the chosen local day. */
@Composable
internal fun CorePlanDateField(value:String,label:String,saving:Boolean,allowClear:Boolean=true,change:(String)->Unit){
    var showing by remember{mutableStateOf(false)}
    CoreTextField(value,label,saving,change)
    TextButton(onClick={showing=true},enabled=!saving){Text(stringResource(R.string.core_detail_choose_date))}
    if(showing){val initial=runCatching{LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()}.getOrNull()
        val state=rememberDatePickerState(initialSelectedDateMillis=initial,yearRange=1..9999)
        DatePickerDialog(onDismissRequest={showing=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{change(java.time.Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString())};showing=false},enabled=state.selectedDateMillis!=null){Text(stringResource(R.string.core_budgets_confirm))}},dismissButton={Row{
            if(allowClear)TextButton(onClick={change("");showing=false}){Text(stringResource(R.string.core_detail_clear))}
            TextButton(onClick={showing=false}){Text(stringResource(R.string.core_budgets_cancel))}
        }}){DatePicker(state)}
    }
}
@Composable
internal fun CorePlanDateTimeField(value:String,label:String,saving:Boolean,change:(String)->Unit){
    CoreTextField(value,label,saving,change)
    val parsed=runCatching{LocalDateTime.parse(value)}.getOrNull()
    var dateDialog by remember{mutableStateOf(false)};var timeDialog by remember{mutableStateOf(false)}
    Row{TextButton(onClick={dateDialog=true},enabled=!saving){Text(stringResource(R.string.core_detail_choose_date))};TextButton(onClick={timeDialog=true},enabled=!saving){Text(stringResource(R.string.core_detail_choose_time))}}
    if(dateDialog){val state=rememberDatePickerState(initialSelectedDateMillis=parsed?.toLocalDate()?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),yearRange=1..9999)
        DatePickerDialog(onDismissRequest={dateDialog=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{val day=java.time.Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate();change(day.atTime(parsed?.toLocalTime()?:java.time.LocalTime.NOON).toString())};dateDialog=false},enabled=state.selectedDateMillis!=null){Text(stringResource(R.string.core_budgets_confirm))}},dismissButton={TextButton(onClick={dateDialog=false}){Text(stringResource(R.string.core_budgets_cancel))}}){DatePicker(state)}
    }
    if(timeDialog){val clock=rememberTimePickerState(initialHour=parsed?.hour?:12,initialMinute=parsed?.minute?:0)
        AlertDialog(onDismissRequest={timeDialog=false},title={Text(stringResource(R.string.core_detail_choose_time))},text={TimePicker(clock)},confirmButton={TextButton(onClick={change((parsed?:LocalDateTime.now()).withHour(clock.hour).withMinute(clock.minute).withSecond(0).withNano(0).toString());timeDialog=false}){Text(stringResource(R.string.core_budgets_confirm))}},dismissButton={TextButton(onClick={timeDialog=false}){Text(stringResource(R.string.core_budgets_cancel))}})
    }
}
