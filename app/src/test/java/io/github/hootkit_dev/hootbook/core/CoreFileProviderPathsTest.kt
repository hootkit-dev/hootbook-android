package io.github.hootkit_dev.hootbook.core

import android.app.Application
import io.github.hootkit_dev.hootbook.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** Parses the real Android resource; no Hilt Application, native open, or user database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CoreFileProviderPathsTest {
    @Test fun coreShareRootIsNarrowCachePathNotPrivateImportDirectory() {
        val entries = mutableListOf<Triple<String, String, String>>()
        RuntimeEnvironment.getApplication().resources.getXml(R.xml.file_paths).use { xml ->
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name != "paths") {
                    entries += Triple(xml.name, xml.getAttributeValue(null, "name"), xml.getAttributeValue(null, "path"))
                }
                xml.next()
            }
        }
        val share = entries.filter { it.second == "core_backup_share" }
        assertEquals(listOf(Triple("cache-path", "core_backup_share", "core-backup-share/")), share)
        assertFalse(entries.any { it.first == "root-path" })
        assertFalse(entries.any { it.third.contains("backup-ui") || it.third.contains("hootkit-core") })
    }
}
