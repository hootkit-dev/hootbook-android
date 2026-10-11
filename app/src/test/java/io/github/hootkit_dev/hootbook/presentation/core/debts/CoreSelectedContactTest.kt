package io.github.hootkit_dev.hootbook.presentation.core.debts

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreSelectedContactTest {
    @Test fun readsOnlyGrantedPhoneRowAndReturnsPlatformFields(){
        val wanted=Uri.parse("content://com.android.contacts/data/phones/7");var queried:Uri?=null
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,object:ContentProvider(){
            override fun onCreate()=true
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {queried=uri;return MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER)).apply{addRow(arrayOf("Selected","+86 123"))}}
            override fun getType(uri:Uri)=ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            override fun insert(uri:Uri,values:ContentValues?):Uri?=null
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
            override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        })
        val context=ApplicationProvider.getApplicationContext<Application>();val result=selectedPhoneContact(context.contentResolver,wanted)
        assertEquals(wanted,queried);assertEquals("Selected",result.first);assertEquals("+86 123",result.second)
    }
    @Test fun otherAuthoritiesAndFileUrisAreNeverRead(){
        val resolver=ApplicationProvider.getApplicationContext<Application>().contentResolver
        for(uri in listOf("file:///private/contact","content://other-provider/contact"))try{selectedPhoneContact(resolver,Uri.parse(uri));fail()}catch(_:IllegalArgumentException){}
    }
}
