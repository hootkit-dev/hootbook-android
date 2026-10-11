package io.github.hootkit_dev.hootbook.presentation.core.accounts

import android.os.Bundle
import dev.hootkit.core.AccountKind

internal fun saveAccountDraft(value: CoreAccountDraft): Bundle = Bundle().apply {
    putString("id", value.id); putString("name", value.name); putString("last4", value.last4); putString("kind", value.kind.name)
    putString("currency", value.currency); putString("opening", value.openingBalance); putString("at", value.openingAt)
    putString("credit", value.creditLimit); putString("statement", value.statementDay); putString("due", value.dueDay)
    putString("institution", value.institutionKey); putString("icon", value.icon); putString("color", value.color)
}
internal fun restoreAccountDraft(value: Bundle?): CoreAccountDraft? = value?.let {
    runCatching { CoreAccountDraft(id = it.getString("id"), name = it.getString("name").orEmpty(), last4 = it.getString("last4").orEmpty(),
        kind = AccountKind.valueOf(it.getString("kind") ?: "BANK"), currency = it.getString("currency") ?: "CNY",
        openingBalance = it.getString("opening").orEmpty(), openingAt = it.getString("at").orEmpty(), creditLimit = it.getString("credit").orEmpty(),
        statementDay = it.getString("statement").orEmpty(), dueDay = it.getString("due").orEmpty(), institutionKey = it.getString("institution").orEmpty(),
        icon = it.getString("icon").orEmpty(), color = it.getString("color").orEmpty()) }.getOrNull()
}
