package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import io.github.hootkit_dev.hootbook.presentation.format.displayTitle
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Category
import dev.hootkit.core.Subcategory
import dev.hootkit.core.Transaction
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Card
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.credit_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.credit_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.investment_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.investment_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.transfer_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.transfer_light
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.formatAmount
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Calendar
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DocumentText2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Paperclip2

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TransactionItem(
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier,
    transaction: Transaction? = null,
    merchantName: String? = null,
    amount: BigDecimal? = null,
    transactionType: TransactionKind? = null,
    categoryEntity: Category? = null,
    subcategoryEntity: Subcategory? = null,
    accountIconResId: Int = 0,
    accountIconName: String? = null,
    accountColorHex: String? = null,
    showDate: Boolean = true,
    useCardStyle: Boolean = false,
    shape: CornerBasedShape = listSingleItemShape,
    onClick: () -> Unit = {},
    subtitleOverride: String? = null,
    amountOverride: String? = null,
    amountColorOverride: Color? = null,
    animatedContentScope: AnimatedVisibilityScope? = null,
    sharedElementKey: String? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    convertedAmount: BigDecimal? = null,
    mainCurrency: String? = null,
    // The rate to mainCurrency is still being fetched: shown instead of a converted amount
    rateLoading: Boolean = false,
    currentAccountContext: String? = null,
    currentBankNameContext: String? = null,
    linkedLoanPersonName: String? = null,
    linkedLoanPersonColor: String? = null,
    linkedLoanPersonAvatar: String? = null,
    useHomeStyle: Boolean = false,
    customSymbols: Map<String, String> = emptyMap(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val attachmentStore = remember(context) { io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore(context) }
    val finalMerchantName = merchantName ?: transaction?.displayTitle(categoryEntity?.displayName) ?: ""
    val finalAmount = amount ?: transaction?.amount ?: BigDecimal.ZERO
    val finalType = transactionType ?: transaction?.kind ?: TransactionKind.EXPENSE
    val isRecurring = transaction?.subscriptionId != null
    val isDark = isAppInDarkTheme
    val effectiveSign = remember(transaction, currentAccountContext, currentBankNameContext) {
        if (transaction?.kind == TransactionKind.TRANSFER && currentAccountContext != null) {
            val isSender = transaction.accountId == currentAccountContext
            val isReceiver = !isSender && transaction.toAccountId == currentAccountContext

            if (isSender) "-" else if (isReceiver) "+" else null
        } else null
    }

    val amountColor = amountColorOverride ?: remember(finalType, isRecurring, isDark, effectiveSign) {
        if (effectiveSign == "-") {
            if (!isDark) expense_light else expense_dark
        } else if (effectiveSign == "+") {
            if (!isDark) income_light else income_dark
        } else {
            when (finalType) {
                TransactionKind.INCOME -> if (!isDark) income_light else income_dark
                TransactionKind.EXPENSE -> if (!isDark) expense_light else expense_dark
                TransactionKind.TRANSFER -> if (!isDark) transfer_light else transfer_dark
                TransactionKind.INVESTMENT -> if (!isDark) investment_light else investment_dark
                TransactionKind.UNKNOWN -> if (!isDark) transfer_light else transfer_dark
                TransactionKind.LOAN_IN -> if (!isDark) income_light else income_dark
                TransactionKind.LOAN_OUT -> if (!isDark) expense_light else expense_dark
            }
        }
    }

    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)
    }
    val defaultSubtitle = remember(transaction?.dateTime) {
        transaction?.dateTime?.toJavaDateTime()?.format(dateTimeFormatter) ?: ""
    }
    val amountText = remember(transaction, amountOverride, finalAmount, effectiveSign) {
        amountOverride ?: transaction?.let {
             // The receiving side of a transfer between currencies shows what arrived there
             val received = it.toAmount?.takeIf { effectiveSign == "+" }
             val formatted = if (received != null) CoreMoneyPresentation.formatCurrency(received, it.toCurrency ?: it.currency)
                 else it.formatAmount()
             if (effectiveSign != null) "$effectiveSign $formatted" else formatted
        } ?: finalAmount.toString()
    }

    val dateTagColor = remember(transaction?.dateTime,) {
        val colors = listOf(income_dark, expense_dark, credit_dark, transfer_dark, investment_dark)

        val dateHash = transaction?.dateTime?.toJavaDateTime()?.toLocalDate()?.hashCode() ?: 0
        val index = Math.abs(dateHash) % colors.size
        colors[index]
    }

    val itemModifier = modifier.then(
        if (sharedTransitionScope != null && animatedContentScope != null && sharedElementKey != null) {
            with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sharedElementKey),
                animatedVisibilityScope = animatedContentScope,
                boundsTransform = { _, _ ->
                    tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
                },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center),
                clipInOverlayDuringTransition = OverlayClip(shape),
                renderInOverlayDuringTransition = false
            )
                .skipToLookaheadSize()
            }
        } else Modifier
    )

    val leadingContent: @Composable () -> Unit = {
        TransactionLeadingSlot(
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onSelectionToggle = onSelectionToggle
        ) {
            if (!linkedLoanPersonName.isNullOrBlank() || !linkedLoanPersonAvatar.isNullOrBlank()) {
                val backgroundColor = remember(linkedLoanPersonColor) {
                    try {
                        Color(linkedLoanPersonColor?.toColorInt() ?: 0xFF4CAF50.toInt())
                    } catch (_: Exception) {
                        Color(0xFF4CAF50)
                    }
                }
                Box(
                    modifier = (if (sharedTransitionScope != null && animatedContentScope != null && transaction != null) {
                        with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "brand_icon_${transaction.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        }
                    } else Modifier)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (!linkedLoanPersonAvatar.isNullOrBlank()) {
                        AsyncImage(
                            model = linkedLoanPersonAvatar?.let(attachmentStore::resolve),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = linkedLoanPersonName?.firstOrNull()?.uppercase()?.toString() ?: finalMerchantName.firstOrNull()?.uppercase()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                BrandIcon(
                    merchantName = finalMerchantName,
                    size = 40.dp,
                    showBackground = true,
                    categoryEntity = categoryEntity,
                    subcategoryEntity = subcategoryEntity,
                    category = categoryEntity?.displayName,
                    subcategory = subcategoryEntity?.displayName,
                    accountIconResId = accountIconResId,
                    accountIconName = accountIconName,
                    accountColorHex = accountColorHex,
                    modifier = if (sharedTransitionScope != null && animatedContentScope != null && transaction != null) {
                        with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "brand_icon_${transaction.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        }
                    } else Modifier
                )
            }
        }
    }

    // Build subtitle parts
    val recurringStr = stringResource(R.string.recurring)
    val (subtitleParts, subtitleFinal) = remember(
        subtitleOverride,
        defaultSubtitle,
        isRecurring,
        recurringStr
    ) {
        val parts = buildList {
            if (subtitleOverride != null) {
                add(subtitleOverride)
            } else {
                if (defaultSubtitle.isNotEmpty()) {
                    add(defaultSubtitle)
                }
                if (isRecurring) add(recurringStr)

            }
        }
        parts to parts.joinToString(" • ")
    }

    if (useHomeStyle) {
        val secondaryAmount = when {
            transaction?.kind == TransactionKind.TRANSFER && transaction.toAmount != null -> stringResource(
                R.string.home_received, CoreMoneyPresentation.format(transaction.toAmount, transaction.toCurrency ?: transaction.currency, customSymbols))
            convertedAmount != null && mainCurrency != null -> CoreMoneyPresentation.format(convertedAmount, mainCurrency, customSymbols)
            else -> null
        }
        Row(
            itemModifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leadingContent()
            Column(Modifier.weight(1f)) {
                Text(finalMerchantName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitleFinal.isNotEmpty()) Text(subtitleFinal, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amountText, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = amountColor, maxLines = 1)
                if (secondaryAmount != null) Text(secondaryAmount, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        return
    }

    if (useCardStyle) {
        ListItemCard(
            title = finalMerchantName,
            subtitle = subtitleFinal,
            amount = amountText,
            amountColor = amountColor,
            onClick = onClick,
            leadingContent = leadingContent,
            modifier = itemModifier
        )
    } else {
        ListItem(
            headline = {
                Text(
                    text = finalMerchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            },
            supporting = {
                // No marquee here: the tag row is a fixed set of short chips, and basicMarquee
                // measures its content with unbounded width on every row of the list.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    var needsSeparator = false

                    @Composable
                    fun TagSeparator() {
                        if (needsSeparator) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                            )
                        }
                    }

                    // Date Tag
                    if (subtitleOverride == null && defaultSubtitle.isNotEmpty()) {
                        TagSeparator()
                        SubtitleTag(
                            icon = {
                                Icon(
                                    imageVector = Iconax.Calendar,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.85f)
                                )
                            },
                            text = defaultSubtitle,
                            color = dateTagColor,
                        )
                        needsSeparator = true
                    }

                    // Category Tag
                    categoryEntity?.let { category ->
                        TagSeparator()
                        SubtitleTag(
                            text = category.displayName,
                            color = try {
                                Color(category.color.toColorInt())
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        needsSeparator = true
                    }

                    if (subtitleOverride != null) {
                        TagSeparator()
                        Text(
                            text = subtitleOverride,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        needsSeparator = true
                    } else {
                        // Recurring Tag
                        if (isRecurring) {
                            TagSeparator()
                            SubtitleTag(
                                text = stringResource(R.string.recurring),
                                color = Color(0xFF5B54D6)
                            )
                            needsSeparator = true
                        }


                        // Description Indicator
                        if (transaction?.notes?.isNotBlank() == true) {
                            TagSeparator()
                            Icon(
                                imageVector = Iconax.DocumentText2,
                                contentDescription = stringResource(R.string.description_indicator_desc),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                            needsSeparator = true
                        }

                        // Attachments Indicator
                        if (transaction?.attachments?.isNotEmpty() == true) {
                            TagSeparator()
                            Icon(
                                imageVector = Iconax.Paperclip2,
                                contentDescription = stringResource(R.string.attachments_indicator_desc),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                            needsSeparator = true
                        }
                    }
                }
            },
            leading = leadingContent,
            trailing = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (subtitleOverride == null) {
                            when (finalType) {

                                TransactionKind.TRANSFER -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_transfer),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) transfer_light else transfer_dark
                                )

                                TransactionKind.INVESTMENT -> Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = stringResource(R.string.type_investment),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) investment_light else investment_dark
                                )

                                TransactionKind.INCOME -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.type_income),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) income_light else income_dark
                                )

                                TransactionKind.EXPENSE -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.type_expense),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) expense_light else expense_dark
                                )

                                TransactionKind.UNKNOWN -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_balance_update),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) transfer_light else transfer_dark
                                )

                                TransactionKind.LOAN_OUT -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.type_lent),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) expense_light else expense_dark
                                )

                                TransactionKind.LOAN_IN -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.type_borrowed),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) income_light else income_dark
                                )
                            }
                        }
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor
                        )
                    }

                    // The amount stays in the currency it was recorded in; the main currency
                    // follows in small type once its rate is known.
                    val foreign = mainCurrency != null && transaction?.currency?.equals(mainCurrency, ignoreCase = true) != true
                    val convertedText = when {
                        !foreign -> null
                        convertedAmount != null -> "≈ ${CoreMoneyPresentation.formatCurrency(convertedAmount, mainCurrency!!)}"
                        transaction == null -> null
                        rateLoading -> stringResource(R.string.rate_loading)
                        else -> stringResource(R.string.rate_unavailable)
                    }
                    if (convertedText != null) {
                        Text(
                            text = convertedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            },
            onClick = {
                if (isSelectionMode) {
                    onSelectionToggle()
                } else {
                    onClick()
                }
            },
            onLongClick = onLongClick,
            shape = shape,
            listColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLow,
            padding = PaddingValues(vertical = 1.5.dp),
            modifier = itemModifier
        )
    }
}



/** A stable 40dp slot avoids two visibility/blur layout trees on every transaction row. */
