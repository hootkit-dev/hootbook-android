package io.github.hootkit_dev.hootbook.widget

import dev.hootkit.core.QuickTemplate
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Original quick-entry UI guarantees, migrated to actual generated Core DTO/String IDs. */
class QuickEntryTest {
    private fun template(amount:String?,prefill:Boolean,currency:String?="CNY")=QuickTemplate(
        id="core-template-string",name="Metro",kind=TransactionKind.EXPENSE,merchant="Metro",categoryId=null,subcategoryId=null,
        amount=amount?.let(::BigDecimal),prefillAmount=prefill,accountId=null,currency=currency,notes=null,sortOrder=0,
    )
    @Test fun `label shows the amount only when the template fills it`() {
        assertEquals("Metro · "+CoreMoneyPresentation.format(BigDecimal("4"),"CNY"),QuickEntry.label(template("4",true)))
        assertEquals("Metro",QuickEntry.label(template("4",false)))
        assertEquals("Metro",QuickEntry.label(template(null,true)))
        assertEquals("Metro",QuickEntry.label(template("4",true,null))) // Do not fabricate a fallback currency.
    }
    @Test fun `template shortcut ids round trip and stay apart from action shortcuts`() {
        assertTrue(QuickEntry.isTemplateShortcut(QuickEntry.shortcutId("core-template-string")))
        assertFalse(QuickEntry.isTemplateShortcut("dyn_add_transfer"))
        assertFalse(QuickEntry.isTemplateShortcut("add_transaction"))
        assertFalse(QuickEntry.isTemplateShortcut("template_7")) // Legacy Long IDs are not Core IDs.
        assertEquals("core_template_core-template-string",QuickEntry.shortcutId("core-template-string"))
    }
}
