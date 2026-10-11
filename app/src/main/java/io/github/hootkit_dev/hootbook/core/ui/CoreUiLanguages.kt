package io.github.hootkit_dev.hootbook.core.ui

import android.content.Context
import io.github.hootkit_dev.hootbook.R
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

object CoreUiLanguages {
    /** Keep the existing manifest language inventory; do not narrow it to English/Chinese. */
    fun supported(context:Context):List<Pair<String,String>> {
        val tags=mutableListOf<String>()
        context.resources.getXml(R.xml.locales_config).use { xml->
            while(xml.eventType!=XmlPullParser.END_DOCUMENT){
                if(xml.eventType==XmlPullParser.START_TAG&&xml.name=="locale")
                    xml.getAttributeValue("http://schemas.android.com/apk/res/android","name")?.let(tags::add)
                xml.next()
            }
        }
        return tags.distinct().map{tag->tag to Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(tag))}
    }
}
