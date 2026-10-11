package io.github.hootkit_dev.hootbook.presentation.core.transactions

import dev.hootkit.core.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoreTransactionInputTest {
    @Test fun templateNullSemanticsAndTransferLinksArePreserved() {
        val form = CoreTransactionForm(kind = TransactionKind.EXPENSE, amount = "123", currency = "JPY",
            accountId = "account", categoryId = "old-category", subcategoryId = "old-child",
            merchant = "old", notes = "old notes", dateTime = "2026-10-10T09:30",
            toAccountId = "target", toAmount = "9", toCurrency = "USD",
            subscriptionId = "subscription", attachments = listOf("keep.jpg"))
        val result = form.withTemplate(TemplatePrefill(TransactionKind.TRANSFER, "new", null, null, null, null, null, null))
        assertEquals(TransactionKind.TRANSFER, result.kind)
        assertEquals("new", result.merchant)
        assertNull(result.categoryId); assertNull(result.subcategoryId); assertEquals("", result.notes)
        assertEquals(form.amount, result.amount); assertEquals(form.accountId, result.accountId); assertEquals(form.currency, result.currency)
        assertEquals(form.dateTime, result.dateTime); assertEquals(form.toAccountId, result.toAccountId)
        assertEquals(form.attachments, result.attachments); assertEquals(form.subscriptionId, result.subscriptionId)
        assertEquals(form.toAmount, result.toAmount); assertEquals(form.toCurrency, result.toCurrency)
    }

    @Test fun templateProvidedValuesOverwriteOnlyTheirDocumentedFields() {
        val result = CoreTransactionForm(dateTime = "2026-10-10T09:30").withTemplate(
            TemplatePrefill(TransactionKind.INCOME, "salary", "income", "salary-child", "note", BigDecimal("88"), "new-account", "USD"))
        assertEquals("88", result.amount); assertEquals("USD", result.currency); assertEquals("new-account", result.accountId)
        assertEquals("income", result.categoryId); assertEquals("salary-child", result.subcategoryId)
        assertEquals("note", result.notes); assertEquals("2026-10-10T09:30", result.dateTime)
    }

    @Test(expected = IllegalStateException::class)
    fun templateDoesNotApplyToExistingTransaction() {
        CoreTransactionForm(editingId = "existing").withTemplate(TemplatePrefill(TransactionKind.EXPENSE, "", null, null, null, null, null, null))
    }

    @Test fun choosingAccountNeverRelabelsAnEnteredCurrencyAmount() {
        val form = CoreTransactionForm(amount = "1500", currency = "JPY")
        assertEquals("JPY", form.withAccount("cny-account", "CNY").currency)
        assertEquals("1500", form.withAccount("cny-account", "CNY").amount)
        assertEquals("CNY", form.copy(amount = "").withAccount("cny-account", "CNY").currency)
    }

    @Test fun crossCurrencyTransferUsesExplicitIncomingAmountAndStringIds() {
        val draft = CoreTransactionForm(kind = TransactionKind.TRANSFER, amount = "10.123456789",
            currency = "USD", accountId = "account-usd", toAccountId = "account-cny", toCurrency = "CNY",
            toAmount = "71.23", dateTime = "2026-10-10T12:30:00.123456789", categoryId = "ignored").toDraft()
        assertEquals(BigDecimal("10.123456789"), draft.amount)
        assertEquals(BigDecimal("71.23"), draft.toAmount)
        assertEquals("account-usd", draft.accountId)
        assertEquals("account-cny", draft.toAccountId)
        assertNull(draft.categoryId)
        assertEquals(123456789u, draft.dateTime.nanosecond)
    }

    @Test fun sameCurrencyTransferDoesNotInventIncomingAmount() {
        val draft = CoreTransactionForm(kind = TransactionKind.TRANSFER, amount = "10", currency = "CNY",
            toCurrency = "CNY", dateTime = "2026-10-10T12:30").toDraft()
        assertNull(draft.toAmount)
    }

    @Test fun editingPreservesAttachmentsSubscriptionAndNanoseconds() {
        val at = LocalDateTime(2026, 10u, 10u, 12u, 30u, 0u, 123u)
        val transaction = Transaction("uuid", TransactionKind.EXPENSE, BigDecimal("12.00"), "CNY", at,
            "account", null, null, null, "category", "child", "商户", "备注", "subscription", "debt",
            listOf("relative/image.jpg"), false, at, at)
        val form = CoreTransactionForm.from(transaction)
        val draft = form.toDraft()
        assertEquals("uuid", form.editingId)
        assertEquals(transaction.dateTime, draft.dateTime)
        assertEquals(transaction.attachments, draft.attachments)
        assertEquals(transaction.subscriptionId, draft.subscriptionId)
        assertEquals(transaction.categoryId, draft.categoryId)
        assertEquals(transaction.subcategoryId, draft.subcategoryId)
    }

    @Test fun nonTransferDraftClearsTransferOnlyFields() {
        val draft = CoreTransactionForm(amount = "20", toAccountId = "stale", toCurrency = "USD", toAmount = "abc",
            dateTime = "2026-10-10T12:30", categoryId = "food").toDraft()
        assertNull(draft.toAccountId)
        assertNull(draft.toCurrency)
        assertNull(draft.toAmount)
        assertEquals("food", draft.categoryId)
    }

    @Test fun inputBoundaryDoesNotCalculateOrEnforceLedgerRules() {
        // Negative/same-account/type-category constraints must be rejected by Core, not duplicate UI rules.
        val draft = CoreTransactionForm(amount = "-1", dateTime = "2026-10-10T12:30").toDraft()
        assertEquals(BigDecimal("-1"), draft.amount)
    }

    @Test fun filterKeepsExclusiveDateBoundaryAndRealCoreSort() {
        val filter = CoreTransactionQuery(text = "  咖啡 ", accountId = "account", categoryId = "cat", currency = "USD",
            fromDate = "2026-10-01", toDateExclusive = "2026-11-01", sort = SortOrder.LARGEST_FIRST,
            deleted = DeletedFilter.ONLY).toFilter()
        assertEquals("咖啡", filter.text)
        assertEquals(listOf("account"), filter.accountIds)
        assertEquals(listOf("cat"), filter.categoryIds)
        assertEquals(LocalDateTime(2026, 11u, 1u, 0u, 0u, 0u, 0u), filter.to)
        assertEquals(SortOrder.LARGEST_FIRST, filter.sort)
        assertEquals(DeletedFilter.ONLY, filter.deleted)
    }

    @Test fun blankFilterMeansUnlimitedNotCurrentPagePostFiltering() {
        val filter = CoreTransactionQuery().toFilter()
        assertNull(filter.text)
        assertNull(filter.currency)
        assertNull(filter.from)
        assertTrue(filter.accountIds.isEmpty())
        assertTrue(filter.kinds.isEmpty())
    }

    @Test(expected = java.time.DateTimeException::class)
    fun malformedCalendarDateIsNotSilentlyNormalized() { CoreTransactionQuery(fromDate = "2026-02-30").toFilter() }
}
