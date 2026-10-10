package io.github.hootkit_dev.hootbook.widget

import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryTest {
    private fun template(amount: String?, prefill: Boolean) = QuickTemplateEntity(
        id = 7, name = "Metro", merchantName = "Metro", category = "Transport",
        amount = amount?.let(::BigDecimal), prefillAmount = prefill, currency = "CNY"
    )

    @Test fun `label shows the amount only when the template fills it`() {
        assertEquals("Metro · " + CurrencyFormatter.formatCurrency(BigDecimal("4"), "CNY"),
            QuickEntry.label(template("4", prefill = true)))
        assertEquals("Metro", QuickEntry.label(template("4", prefill = false)))
        assertEquals("Metro", QuickEntry.label(template(null, prefill = true)))
    }

    @Test fun `template shortcut ids round trip and stay apart from action shortcuts`() {
        assertTrue(QuickEntry.isTemplateShortcut(QuickEntry.shortcutId(7)))
        assertFalse(QuickEntry.isTemplateShortcut("dyn_add_transfer"))
        assertFalse(QuickEntry.isTemplateShortcut("add_transaction"))
    }
}
