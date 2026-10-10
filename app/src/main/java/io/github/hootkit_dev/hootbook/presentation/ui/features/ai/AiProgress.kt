package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.ai.AiStep
import io.github.hootkit_dev.hootbook.data.ai.LedgerChange
import io.github.hootkit_dev.hootbook.data.ai.LedgerTools
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.localizedDateFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.withLast4
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.transactionTypeColor
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Transactions listed under a step before the rest are only counted. */
private const val SHOWN_PER_STEP = 4

/**
 * The run so far: a status card (model, elapsed time, proposals) and then each step the model
 * took, newest last. The last step is the one in progress.
 */
fun LazyListScope.aiProgress(phase: AiPhase.Running, model: String) {
    item(key = "progress-status") { StatusCard(phase, model) }
    timeline(phase, keyPrefix = "progress", live = true)
}

/**
 * A finished run, kept on the review: one row with its totals that opens to the same timeline,
 * every step of which still opens to its raw text.
 */
fun LazyListScope.aiRunLog(run: AiPhase.Running, expanded: Boolean, onToggle: () -> Unit) {
    item(key = "run-log") {
        val replies = run.steps.filterIsInstance<AiStep.Replied>()
        val seconds = (((run.finishedAt ?: run.startedAt) - run.startedAt) / 1000).coerceAtLeast(0)
        val input = replies.mapNotNull { it.inputTokens }.takeIf { it.isNotEmpty() }?.sum()
        val output = replies.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum()
        val totals = listOfNotNull(
            pluralStringResource(R.plurals.ai_run_rounds, replies.size, replies.size),
            "%d:%02d".format(seconds / 60, seconds % 60),
            if (input != null || output != null) {
                stringResource(R.string.ai_step_tokens, input?.let { "%,d".format(it) } ?: "—", output?.let { "%,d".format(it) } ?: "—")
            } else null
        ).joinToString(" · ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClickLabel = stringResource(R.string.ai_step_details), onClick = onToggle)
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_run_log), style = MaterialTheme.typography.titleSmall)
                DetailText(totals)
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (expanded) timeline(run, keyPrefix = "run-log", live = false)
}

/** The files read and each step after; while [live], the last one is in progress. */
private fun LazyListScope.timeline(phase: AiPhase.Running, keyPrefix: String, live: Boolean) {
    item(key = "$keyPrefix-files") {
        val images = phase.images
        StepRow(
            icon = Icons.Rounded.Description,
            active = live && images == null,
            title = when {
                phase.files == 0 -> stringResource(R.string.ai_step_no_files)
                images == null -> pluralStringResource(R.plurals.ai_step_reading_files, phase.files, phase.files)
                // Images only come up when a long screenshot was cut into pieces for the model
                images <= phase.files -> pluralStringResource(R.plurals.ai_step_read_files, phase.files, phase.files)
                else -> pluralStringResource(R.plurals.ai_step_read_files, phase.files, phase.files) + " · " +
                    pluralStringResource(R.plurals.ai_step_images, images, images)
            }
        )
    }
    itemsIndexed(phase.steps, key = { index, _ -> "$keyPrefix-$index" }) { index, step ->
        Step(step, active = live && index == phase.steps.lastIndex)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(phase: AiPhase.Running, model: String) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val seconds = ((now - phase.startedAt) / 1000).coerceAtLeast(0)
    val turns = phase.steps.count { it is AiStep.Asking }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.ai_progress_proposed, phase.proposed, phase.proposed),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        listOfNotNull(
                            model,
                            "%d:%02d".format(seconds / 60, seconds % 60),
                            turns.takeIf { it > 0 }?.let { stringResource(R.string.ai_progress_turn, it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.ai_progress_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Step(step: AiStep, active: Boolean) {
    val dates = localizedDateFormatter(withYear = false)
    when (step) {
        is AiStep.Sent -> StepRow(
            icon = Icons.Rounded.Upload,
            title = stringResource(R.string.ai_step_sent),
            detail = stringResource(R.string.ai_step_sent_detail, step.system.length, step.message.length),
            raw = listOf(
                stringResource(R.string.ai_raw_system) to step.system,
                stringResource(R.string.ai_raw_message) to step.message
            )
        )
        // Once answered, the reply's own row stands for the round
        is AiStep.Asking -> if (active) {
            StepRow(icon = Icons.Rounded.AutoAwesome, active = true, title = stringResource(R.string.ai_step_asking, step.turn))
        }
        is AiStep.Replied -> {
            val usage = if (step.inputTokens != null || step.outputTokens != null) {
                stringResource(
                    R.string.ai_step_tokens,
                    step.inputTokens?.let { "%,d".format(it) } ?: "—",
                    step.outputTokens?.let { "%,d".format(it) } ?: "—"
                )
            } else null
            val calls = step.calls.takeIf { it.isNotEmpty() }?.let { calls ->
                stringResource(R.string.ai_step_calls, calls.joinToString(", ") { it.name })
            }
            StepRow(
                icon = Icons.Rounded.AutoAwesome,
                title = stringResource(R.string.ai_step_replied, step.turn, "%.1f".format(step.millis / 1000.0)),
                detail = listOfNotNull(usage, calls).joinToString(" · ").ifEmpty { null },
                quote = step.text.ifEmpty { null },
                raw = listOfNotNull(
                    step.thinking.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ai_raw_thinking) to it },
                    step.text.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ai_raw_reply) to it }
                ) + step.calls.map { call -> "→ ${call.name}" to pretty(call.input.toString()) }
            )
        }
        is AiStep.Searched -> {
            fun day(text: String?) = text?.let { runCatching { LocalDate.parse(it.take(10)).format(dates) }.getOrDefault(it) }
            val range = listOfNotNull(day(step.from), day(step.to)).distinct().joinToString("–")
            StepRow(
                icon = Icons.Rounded.Search,
                title = listOfNotNull(
                    stringResource(R.string.ai_step_searched),
                    range.ifEmpty { null },
                    step.text?.let { "“$it”" }
                ).joinToString(" · "),
                detail = step.found?.let { pluralStringResource(R.plurals.ai_step_found, it, it) },
                raw = rawCall(step.input.toString(), step.result)
            )
        }
        is AiStep.Proposed -> ProposedStep(step)
    }
}

@Composable
private fun rawCall(input: String, result: String) = listOf(
    stringResource(R.string.ai_raw_input) to pretty(input),
    stringResource(R.string.ai_raw_result) to pretty(result)
)

private val prettyJson = Json { prettyPrint = true }

/** JSON indented for reading; anything else as it is. */
private fun pretty(text: String): String =
    runCatching { prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(text)) }.getOrDefault(text)

@Composable
private fun ProposedStep(step: AiStep.Proposed) {
    val changes = step.changes
    val rejected = if (step.rejected) stringResource(R.string.ai_step_rejected) else null
    if (changes.isEmpty()) {
        StepRow(
            icon = Icons.Rounded.WarningAmber,
            title = rejected ?: stringResource(R.string.ai_step_nothing, step.tool),
            raw = rawCall(step.input.toString(), step.result)
        )
        return
    }
    val dates = localizedDateFormatter(withYear = false)
    val (icon, title) = when (step.tool) {
        LedgerTools.ADD -> Icons.Rounded.AddCircleOutline to
            pluralStringResource(R.plurals.ai_step_added, changes.size, changes.size)
        LedgerTools.UPDATE -> Icons.Rounded.Edit to
            pluralStringResource(R.plurals.ai_step_updated, changes.size, changes.size)
        LedgerTools.DELETE -> Icons.Rounded.DeleteOutline to
            pluralStringResource(R.plurals.ai_step_deleted, changes.size, changes.size)
        else -> Icons.Rounded.AccountBalance to stringResource(R.string.ai_step_accounts)
    }
    StepRow(icon = icon, title = title, detail = rejected, raw = rawCall(step.input.toString(), step.result)) {
        changes.take(SHOWN_PER_STEP).forEach { change ->
            when (change) {
                is LedgerChange.Add -> TransactionLine(
                    change.draft.dateTime.toLocalDate().format(dates),
                    change.draft.merchant,
                    CurrencyFormatter.formatCurrency(change.draft.amount, change.draft.currency),
                    transactionTypeColor(change.draft.type)
                )
                is LedgerChange.Update -> TransactionLine(
                    change.after.dateTime.toLocalDate().format(dates),
                    if (change.before.merchantName != change.after.merchantName) {
                        "${change.before.merchantName} → ${change.after.merchantName}"
                    } else {
                        listOfNotNull(change.after.merchantName, io.github.hootkit_dev.hootbook.presentation.common.categoryName(change.after.category)).joinToString(" · ")
                    },
                    CurrencyFormatter.formatCurrency(change.after.amount, change.after.currency),
                    transactionTypeColor(change.after.transactionType)
                )
                is LedgerChange.Delete -> TransactionLine(
                    change.transaction.dateTime.toLocalDate().format(dates),
                    change.transaction.merchantName,
                    CurrencyFormatter.formatCurrency(change.transaction.amount, change.transaction.currency),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                is LedgerChange.CreateAccount -> DetailText(
                    stringResource(R.string.ai_step_new_account, accountName(change.account.bankName, change.account.accountLast4))
                )
                is LedgerChange.SetBalance -> DetailText(
                    stringResource(
                        R.string.ai_step_balance,
                        accountName(change.account.bankName, change.account.accountLast4),
                        CurrencyFormatter.formatCurrency(change.balance, change.account.currency)
                    )
                )
                is LedgerChange.UpdateAccount -> DetailText(
                    stringResource(
                        R.string.ai_step_account_edit,
                        accountName(change.before.bankName, change.before.accountLast4),
                        accountName(change.after.bankName, change.after.accountLast4)
                    )
                )
            }
        }
        if (changes.size > SHOWN_PER_STEP) {
            DetailText(stringResource(R.string.ai_step_more, changes.size - SHOWN_PER_STEP))
        }
    }
}

private fun accountName(bankName: String, last4: String) = withLast4(bankName, last4) ?: bankName

@Composable
private fun TransactionLine(date: String, merchant: String, amount: String, amountColor: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            merchant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(amount, style = MaterialTheme.typography.bodySmall, color = amountColor)
    }
}

@Composable
private fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Raw text shown in a step's details before the rest is cut off. */
private const val RAW_LIMIT = 8_000

/**
 * One line of the timeline: an icon (a spinner while [active]), a title, what it found and
 * [quote], the model's own words. A row with [raw] sections opens on tap to show them in full.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepRow(
    icon: ImageVector,
    title: String,
    active: Boolean = false,
    detail: String? = null,
    quote: String? = null,
    raw: List<Pair<String, String>> = emptyList(),
    content: @Composable () -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandable = raw.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (expandable) Modifier.clickable(onClickLabel = stringResource(R.string.ai_step_details)) { expanded = !expanded } else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (active) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            detail?.let { DetailText(it) }
            if (quote != null && !expanded) {
                Text(
                    quote,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            content()
            AnimatedVisibility(visible = expanded) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        raw.forEach { (label, text) ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (text.length > RAW_LIMIT) {
                                    text.take(RAW_LIMIT) + "\n" + stringResource(R.string.ai_raw_truncated, text.length - RAW_LIMIT)
                                } else text,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
