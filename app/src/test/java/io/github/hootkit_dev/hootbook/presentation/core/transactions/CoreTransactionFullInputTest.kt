package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.app.Application
import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class CoreTransactionFullInputTest {
    @Test fun multiFiltersPreserveStringIdsAndCoreSortWithoutLocalFiltering() {
        val filter = CoreTransactionQuery(accountId = "legacy-route", accountIds = listOf("id/1", "id?2"),
            categoryIds = listOf("cat/1", "cat2"), kinds = listOf(TransactionKind.INCOME, TransactionKind.TRANSFER),
            sort = SortOrder.LARGEST_FIRST, deleted = DeletedFilter.INCLUDE).toFilter()
        assertEquals(listOf("id/1", "id?2", "legacy-route"), filter.accountIds)
        assertEquals(listOf("cat/1", "cat2"), filter.categoryIds)
        assertEquals(listOf(TransactionKind.INCOME, TransactionKind.TRANSFER), filter.kinds)
        assertEquals(SortOrder.LARGEST_FIRST, filter.sort)
    }
    @Test fun blankBulkFieldsMeanUnchangedButCheckedBlankNotesMeanClear() {
        val unchanged = CoreTransactionBulkForm().toPatch()
        assertNull(unchanged.amount); assertNull(unchanged.date); assertNull(unchanged.time); assertNull(unchanged.notes)
        assertEquals("", CoreTransactionBulkForm(replaceNotes = true).toPatch().notes)
    }
    @Test fun bulkDateTimeAndAmountArePassedDirectlyToCore() {
        val patch = CoreTransactionBulkForm("2026-10-11", "13:45:06", "12.340", "category", "child").toPatch()
        assertEquals(BigDecimal("12.340"), patch.amount)
        assertEquals(LocalDate(2026, 10u, 11u), patch.date)
        assertEquals(LocalTime(13u, 45u, 6u, 0u), patch.time)
        assertEquals("category", patch.categoryId); assertEquals("child", patch.subcategoryId)
    }
    @Test fun processDraftRoundtripRetainsEditingTransferAndReferencesButContainsNoCredentials() {
        val form = CoreTransactionForm(editingId = "edit/id", kind = TransactionKind.TRANSFER, amount = "1.25", currency = "USD",
            accountId = "source", toAccountId = "target", toCurrency = "HKD", toAmount = "9.71", notes = "draft",
            attachments = listOf("receipt.png"), subscriptionId = "subscription")
        val bundle = saveTransactionForm(form)
        assertEquals(form, restoreTransactionForm(bundle))
        assertTrue(bundle.keySet().none { it.contains("password", true) || it.contains("token", true) || it.contains("credential", true) })
    }
}
