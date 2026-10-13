package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.inputText
import io.github.hootkit_dev.hootbook.presentation.format.parseCoreDateTime
import java.math.BigDecimal

/** Text fields are presentation input only; Core validates semantics when applying. */
data class AiDraftForm(
    val kind: TransactionKind,
    val amount: String,
    val currency: String,
    val dateTime: String,
    val merchant: String,
    val categoryId: String?,
    val subcategoryId: String?,
    val notes: String,
    val account: AiAccountRef?,
    val toAccount: AiAccountRef?,
    val toCurrency: String,
    val toAmount: String,
) {
    fun toDraft() =
        AiTransactionDraft(
            kind,
            BigDecimal(amount.trim()),
            currency.trim(),
            parseCoreDateTime(dateTime),
            merchant,
            categoryId,
            subcategoryId,
            notes.ifBlank { null },
            account,
            toAccount,
            toCurrency.ifBlank { null },
            toAmount.trim().takeIf { it.isNotEmpty() }?.let(::BigDecimal),
        )

    companion object {
        fun from(draft: AiTransactionDraft) =
            AiDraftForm(
                draft.kind,
                draft.amount.toPlainString(),
                draft.currency,
                draft.dateTime.inputText(),
                draft.merchant,
                draft.categoryId,
                draft.subcategoryId,
                draft.notes.orEmpty(),
                draft.account,
                draft.toAccount,
                draft.toCurrency.orEmpty(),
                draft.toAmount?.toPlainString().orEmpty(),
            )
    }
}

/** No secret in generated settings; key draft never enters SavedState, diagnostics or toString. */
class AiConfigDraft(
    val protocol: AiProtocol = AiProtocol.CLAUDE,
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val removeKey: Boolean = false,
) {
    fun copy(
        protocol: AiProtocol = this.protocol,
        baseUrl: String = this.baseUrl,
        model: String = this.model,
        apiKey: String = this.apiKey,
        removeKey: Boolean = this.removeKey,
    ) = AiConfigDraft(protocol, baseUrl, model, apiKey, removeKey)

    override fun toString() = "AiConfigDraft(protocol=$protocol, model=$model, key=<redacted>)"
}

internal fun aiFailureText(error: Throwable): String =
    when (error) {
        is CoreException.Ai ->
            when (error.failure) {
                AiFailure.NOT_CONFIGURED -> "Configure a provider, model, and API key first."
                AiFailure.REFUSED -> "The provider declined this request."
                AiFailure.NO_ANSWER -> "The provider returned no answer."
                AiFailure.TRUNCATED -> "The reply was truncated. Send fewer pages in each request."
            }
        is CoreException.Network ->
            when (error.status) {
                401.toUShort(),
                403.toUShort() -> "The provider rejected authentication; check the API key."
                429.toUShort() -> "The provider is rate limiting requests. Retry later."
                else ->
                    "Provider network error (${error.status ?: "connection"}). Retry explicitly."
            }
        is CoreException.Invalid -> "Something went wrong. Please try again."
        is CoreException.NotFound ->
            "This record or undo token is no longer available. Refresh and review again."
        is CoreException.Cancelled -> "Request cancelled. No proposals saved."
        is CoreException.Platform ->
            "Secure storage or platform operation failed. No plaintext fallback was used."
        else -> "AI operation failed. Review the ledger before retrying a save."
    }
