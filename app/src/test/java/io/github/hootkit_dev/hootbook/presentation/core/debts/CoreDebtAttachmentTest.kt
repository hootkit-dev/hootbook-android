package io.github.hootkit_dev.hootbook.presentation.core.debts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import java.lang.reflect.Proxy
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreDebtAttachmentTest {
    private fun plans():PlansInterface=Proxy.newProxyInstance(PlansInterface::class.java.classLoader,arrayOf(PlansInterface::class.java,Disposable::class.java)){_,method,args->
        when(method.name.substringBefore('-')){
            "people"->emptyList<Person>()
            "debtSummary"->DebtSummary("CNY",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0u,0u,0u,0u,true)
            "createPerson"->{@Suppress("UNCHECKED_CAST") val continuation=args.last() as Continuation<String>;continuation.resumeWith(Result.failure(CoreException.Storage(StorageErrorKind.IO,"unknown commit")));COROUTINE_SUSPENDED}
            "destroy"->Unit
            else->error(method.name)
        }
    } as PlansInterface
    @Test fun avatarRetainedBeforeUncertainCoreFailureAndNeverDeletedAsDraft()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val views=ViewModelStore()
        try{
            val context=ApplicationProvider.getApplicationContext<Application>();val files=CoreAttachmentStore(context);val vm=CoreDebtsViewModel(ChangeBus(),plans={plans()},attachments=files);views.put("vm",vm)
            vm.state.first{!it.loading};vm.editPerson();vm.personChanged(vm.state.value.personForm!!.copy(name="Person"))
            val uri=Uri.parse("content://fixture/avatar");Shadows.shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream("avatar-source".toByteArray()))
            vm.avatar(uri);vm.state.first{!it.assetLoading};val ref=vm.state.value.personForm!!.avatar!!;assertNotNull(files.resolve(ref))
            vm.savePerson();vm.state.first{!it.saving&&!it.loading};assertNotNull(vm.state.value.error)
            vm.dismissEditor();files.discardDraft(listOf(ref));assertNotNull(files.resolve(ref))
        }finally{views.clear();Dispatchers.resetMain()}
    }
    @Test fun cancelledPickerDoesNotChangeDraftOrCreateFile()=runTest{
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val views=ViewModelStore()
        try{val vm=CoreDebtsViewModel(ChangeBus(),plans={plans()});views.put("vm",vm);runCurrent();vm.editPerson();val before=vm.state.value.personForm;vm.avatar(null);assertEquals(before,vm.state.value.personForm);assertFalse(vm.state.value.assetLoading)}
        finally{views.clear();Dispatchers.resetMain()}
    }
}
