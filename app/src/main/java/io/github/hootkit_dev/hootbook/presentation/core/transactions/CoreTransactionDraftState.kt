package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.os.Bundle
import dev.hootkit.core.TransactionKind

/** Only editable strings/Core IDs; never stores a Core handle, provider password, API key or credentials. */
internal fun saveTransactionForm(form: CoreTransactionForm): Bundle = Bundle().apply {
    putString("id", form.editingId); putString("kind", form.kind.name)
    putString("amount", form.amount); putString("currency", form.currency); putString("date", form.dateTime)
    putString("account", form.accountId); putString("toAccount", form.toAccountId)
    putString("toCurrency", form.toCurrency); putString("toAmount", form.toAmount)
    putString("category", form.categoryId); putString("subcategory", form.subcategoryId)
    putString("merchant", form.merchant); putString("notes", form.notes); putString("subscription", form.subscriptionId)
    putStringArrayList("attachments", ArrayList(form.attachments))
}
internal fun restoreTransactionForm(bundle: Bundle?): CoreTransactionForm? = bundle?.let {
    runCatching { CoreTransactionForm(editingId = it.getString("id"), kind = TransactionKind.valueOf(it.getString("kind") ?: "EXPENSE"),
        amount = it.getString("amount").orEmpty(), currency = it.getString("currency") ?: "CNY",
        dateTime = it.getString("date") ?: CoreTransactionForm().dateTime, accountId = it.getString("account"),
        toAccountId = it.getString("toAccount"), toCurrency = it.getString("toCurrency").orEmpty(), toAmount = it.getString("toAmount").orEmpty(),
        categoryId = it.getString("category"), subcategoryId = it.getString("subcategory"), merchant = it.getString("merchant").orEmpty(),
        notes = it.getString("notes").orEmpty(), subscriptionId = it.getString("subscription"), attachments = it.getStringArrayList("attachments").orEmpty()) }.getOrNull()
}
