package io.github.hootkit_dev.hootbook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateRangeUtilsTest {
    @Test
    fun `Chinese labels follow current locale and include day suffix`() {
        val original = Locale.getDefault()
        try {
            val start = LocalDate.of(2026, 9, 1)
            val end = LocalDate.of(2026, 9, 6)
            Locale.setDefault(Locale.US)
            assertEquals("Sep 1 - Sep 6", DateRangeUtils.formatDateRange(start, end))
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals("9月1日 - 9月6日", DateRangeUtils.formatDateRange(start, end))
            assertEquals("9月1日 - 9月6日", DateRangeUtils.formatDateRange(start to end))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `cross-year range is unambiguous and custom formats are retained`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            val range = LocalDate.of(2025, 12, 25) to LocalDate.of(2026, 1, 5)
            assertEquals("2025年12月25日 - 2026年1月5日", DateRangeUtils.formatDateRange(range))
            assertEquals("2025-12-25 - 2026-01-05", DateRangeUtils.formatDateRange(range, DateTimeFormatter.ISO_LOCAL_DATE))
            assertNull(DateRangeUtils.formatDateRange(null))
        } finally {
            Locale.setDefault(original)
        }
    }
}
