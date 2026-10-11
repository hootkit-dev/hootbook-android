package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreDate
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreTime
import java.math.BigDecimal

/** Saved editable input only. Core handles and lookup snapshots never enter saved instance state. */
internal fun saveAddDraft(handle: SavedStateHandle, state: AddUiState) {
    handle["add_draft"] = Bundle().apply {
        val tx = state.transaction
        putString("kind", tx.kind.name); putString("amount", state.amount); putString("currency", tx.currency)
        putString("date", tx.dateTime.toJavaDateTime().toString()); putString("account", tx.accountId)
        putString("toAccount", tx.toAccountId); putString("toCurrency", tx.toCurrency); putString("toAmount", state.targetAmount)
        putString("category", tx.categoryId); putString("subcategory", tx.subcategoryId); putString("merchant", tx.merchant)
        putString("notes", tx.notes); putString("subscription", tx.subscriptionId)
        putStringArrayList("attachments", ArrayList(tx.attachments)); putString("person", state.personId)
        putBoolean("template", state.saveAsTemplate); putBoolean("pad", state.amountPadRequested)
        putString("created", state.createdTransactionId); putBoolean("linked", state.loanLinked); putBoolean("saved", state.saved)
        val sub = state.subscription
        putString("subId", state.subscriptionId); putString("subName", sub.name); putString("subAmount", state.subscriptionAmount)
        putString("subCurrency", sub.amount.currency); putLong("every", sub.cycle.every.toLong()); putString("unit", sub.cycle.unit.name)
        putString("end", sub.endDate?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()).toString() })
        putString("due", sub.nextDue?.let { java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt()).toString() })
        putString("subAccount", sub.accountId); putString("subCategory", sub.categoryId); putString("subSubcategory", sub.subcategoryId)
        putString("subNotes", sub.notes); putBoolean("custom", state.customCycle)
    }
}
internal fun restoreAddDraft(handle: SavedStateHandle): AddUiState? = handle.get<Bundle>("add_draft")?.let { b ->
    runCatching {
        val amount = b.getString("amount").orEmpty()
        val subAmount = b.getString("subAmount").orEmpty()
        AddUiState(
            transaction = TransactionDraft(TransactionKind.valueOf(requireNotNull(b.getString("kind"))), amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                b.getString("currency"), java.time.LocalDateTime.parse(b.getString("date")).coreTime(), b.getString("account"), b.getString("toAccount"),
                b.getString("toCurrency"), b.getString("toAmount")?.toBigDecimalOrNull(), b.getString("category"), b.getString("subcategory"),
                b.getString("merchant").orEmpty(), b.getString("notes"), b.getString("subscription"), b.getStringArrayList("attachments").orEmpty()),
            subscription = SubscriptionDraft(b.getString("subName").orEmpty(), Money(subAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO, requireNotNull(b.getString("subCurrency"))),
                Cycle(b.getLong("every").toUInt(), CycleUnit.valueOf(requireNotNull(b.getString("unit")))), b.getString("end")?.let { java.time.LocalDate.parse(it).coreDate() },
                b.getString("due")?.let { java.time.LocalDate.parse(it).coreDate() }, b.getString("subAccount"), b.getString("subCategory"), b.getString("subSubcategory"), b.getString("subNotes")),
            subscriptionId = b.getString("subId"), amount = amount, targetAmount = b.getString("toAmount").orEmpty(), subscriptionAmount = subAmount,
            personId = b.getString("person"), saveAsTemplate = b.getBoolean("template"), customCycle = b.getBoolean("custom"),
            amountPadRequested = b.getBoolean("pad"), createdTransactionId = b.getString("created"), loanLinked = b.getBoolean("linked"), saved = b.getBoolean("saved"),
        )
    }.getOrNull()
}
