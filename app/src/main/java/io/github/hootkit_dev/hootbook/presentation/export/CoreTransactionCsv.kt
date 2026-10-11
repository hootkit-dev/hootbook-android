package io.github.hootkit_dev.hootbook.presentation.export

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.inputText
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray

/** CSV is a requested presentation export, not a second ledger or balance calculator. */
object CoreTransactionCsv {
    fun quote(value: String, protectFormula: Boolean = true): String {
        val unsafe =
            value.startsWith('\t') ||
                value.startsWith('\r') ||
                value.startsWith('\n') ||
                value.trimStart { it.isWhitespace() || it == '\uFEFF' }.firstOrNull() in
                    listOf('=', '+', '-', '@')
        val protected = if (protectFormula && unsafe) "'$value" else value
        return "\"${protected.replace("\"", "\"\"")}\""
    }

    fun row(transaction: Transaction, accounts: List<Account>, categories: List<Category>): String {
        val category = categories.find { it.id == transaction.categoryId }
        val values =
            listOf(
                transaction.id,
                transaction.dateTime.inputText(),
                transaction.kind.name,
                transaction.amount.toPlainString(),
                transaction.currency,
                transaction.accountId.orEmpty(),
                accounts.find { it.id == transaction.accountId }?.name.orEmpty(),
                transaction.toAccountId.orEmpty(),
                accounts.find { it.id == transaction.toAccountId }?.name.orEmpty(),
                transaction.toAmount?.toPlainString().orEmpty(),
                transaction.toCurrency.orEmpty(),
                transaction.categoryId.orEmpty(),
                category?.displayName.orEmpty(),
                transaction.subcategoryId.orEmpty(),
                category
                    ?.subcategories
                    ?.find { it.id == transaction.subcategoryId }
                    ?.displayName
                    .orEmpty(),
                transaction.merchant,
                transaction.notes.orEmpty(),
                transaction.subscriptionId.orEmpty(),
                transaction.debtId.orEmpty(),
                JSONArray(transaction.attachments).toString(),
                transaction.deleted.toString(),
            )
        return values
            .mapIndexed { index, value -> quote(value, protectFormula = index !in setOf(3, 9, 20)) }
            .joinToString(",") + "\r\n"
    }

    const val HEADER =
        "id,date_time,kind,amount,currency,account_id,account_name,to_account_id,to_account_name,to_amount,to_currency,category_id,category_name,subcategory_id,subcategory_name,merchant,notes,subscription_id,debt_id,attachments,deleted\r\n"

    /**
     * Fetch every Core page. Reject changing totals/duplicate IDs instead of silently exporting a
     * partial list.
     */
    suspend fun export(
        ledger: LedgerInterface,
        filter: TransactionFilter,
        output: OutputStream,
        accounts: List<Account>,
        categories: List<Category>,
        onProgress: (UInt, UInt) -> Unit = { _, _ -> },
        isCurrent: () -> Boolean = { true },
    ): UInt {
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        output.write(HEADER.toByteArray(Charsets.UTF_8))
        var offset = 0u
        var total: UInt? = null
        val seen = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Ledger changed during export; export again" }
            val page = ledger.list(filter, PageRequest(offset, 200u))
            check(total == null || page.total == total) {
                "Ledger changed during export; export again"
            }
            total = page.total
            check(page.items.isNotEmpty() || offset >= page.total) {
                "Core returned an incomplete export page"
            }
            for (transaction in page.items) {
                check(seen.add(transaction.id)) { "Ledger changed during export; export again" }
                output.write(row(transaction, accounts, categories).toByteArray(Charsets.UTF_8))
            }
            offset += page.items.size.toUInt()
            check(offset <= page.total) { "Core returned an inconsistent export total" }
            check(isCurrent()) { "Ledger changed during export; export again" }
            onProgress(offset, page.total)
        } while (offset < requireNotNull(total))
        check(offset == total) { "Core returned an incomplete export" }
        output.flush()
        return offset
    }
}
