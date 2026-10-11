package io.github.hootkit_dev.hootbook.presentation.core.debts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract

/** Read only the user-selected phone-row URI grant; no global contacts permission or enumeration. */
internal fun selectedPhoneContact(resolver:ContentResolver,uri:Uri):Pair<String?,String?> {
    require(uri.scheme=="content" && uri.authority==ContactsContract.AUTHORITY)
    val projection=arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER)
    return resolver.query(uri,projection,null,null,null)?.use{cursor->
        require(cursor.moveToFirst());cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME).let{if(it>=0)cursor.getString(it)else null} to
            cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER).let{if(it>=0)cursor.getString(it)else null}
    } ?: error("Selected contact is unavailable")
}
