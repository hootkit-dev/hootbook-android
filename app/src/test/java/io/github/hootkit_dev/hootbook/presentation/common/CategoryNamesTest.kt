package io.github.hootkit_dev.hootbook.presentation.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNamesTest {
    private val zh = Locale.SIMPLIFIED_CHINESE
    private val tw = Locale.TRADITIONAL_CHINESE

    @Test fun builtInNamesShowInChinese() {
        assertEquals("打车", CategoryNames.display("Cab", zh))
        assertEquals("餐饮", CategoryNames.display("Food & Drinks", zh))
        assertEquals("餐飲", CategoryNames.display("Food & Drinks", tw))
        assertEquals("餐饮", CategoryNames.display("Food & Drinks", Locale.forLanguageTag("zh-Hans-CN")))
    }

    @Test fun typedNamesBrandsAndOtherLanguagesStayAsStored() {
        assertEquals("早饭", CategoryNames.display("早饭", zh))
        assertEquals("Netflix", CategoryNames.display("Netflix", zh))
        assertEquals("Cab", CategoryNames.display("Cab", Locale.ENGLISH))
        assertEquals("", CategoryNames.display(null, zh))
    }

    @Test fun searchFindsEitherName() {
        assertTrue(CategoryNames.matches("Cab", "打车", zh))
        assertTrue(CategoryNames.matches("Cab", "cab", zh))
    }

    @Test fun noNameIsTranslatedTwice() {
        listOf(CategoryNames.HANS_ENTRIES, CategoryNames.HANT_ENTRIES).forEach { entries ->
            val repeated = entries.groupBy { it.first }.filterValues { it.size > 1 }.keys
            assertTrue("translated more than once: $repeated", repeated.isEmpty())
        }
    }

    @Test fun simplifiedAndTraditionalCoverTheSameNames() {
        assertEquals(
            CategoryNames.HANS_ENTRIES.map { it.first }.toSet(),
            CategoryNames.HANT_ENTRIES.map { it.first }.toSet()
        )
    }
}
