package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
import io.github.hootkit_dev.hootbook.presentation.common.TransactionDecoration
import io.github.hootkit_dev.hootbook.utils.displayTitle
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
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
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.formatAmount
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
    transaction: TransactionEntity? = null,
    merchantName: String? = null,
    amount: BigDecimal? = null,
    transactionType: TransactionType? = null,
    categoryEntity: CategoryEntity? = null,
    subcategoryEntity: SubcategoryEntity? = null,
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
    balanceAfter: BigDecimal? = null,
    balanceCurrency: String? = null,
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
    // Category, account, person and converted amount looked up for a list row; explicit
    // arguments above take precedence over it
    decoration: TransactionDecoration? = null
) {
    val categoryEntity = categoryEntity ?: decoration?.category
    val subcategoryEntity = subcategoryEntity ?: decoration?.subcategory
    val accountIconResId = accountIconResId.takeIf { it != 0 } ?: decoration?.account?.iconResId ?: 0
    val accountIconName = accountIconName ?: decoration?.account?.iconName
    val accountColorHex = accountColorHex ?: decoration?.account?.color
    val convertedAmount = convertedAmount ?: decoration?.convertedAmount
    val rateLoading = rateLoading || decoration?.rateLoading == true
    val linkedLoanPersonName = linkedLoanPersonName ?: decoration?.person?.name
    val linkedLoanPersonColor = linkedLoanPersonColor ?: decoration?.person?.color
    val linkedLoanPersonAvatar = linkedLoanPersonAvatar ?: decoration?.person?.avatar
    val finalMerchantName = merchantName ?: transaction?.displayTitle() ?: ""
    val finalAmount = amount ?: transaction?.amount ?: BigDecimal.ZERO
    val finalType = transactionType ?: transaction?.transactionType ?: TransactionType.EXPENSE
    val isRecurring = transaction?.isRecurring ?: false
    val isDark = isAppInDarkTheme
    val effectiveSign = remember(transaction, currentAccountContext, currentBankNameContext) {
        if (transaction?.transactionType == TransactionType.TRANSFER && currentAccountContext != null && currentBankNameContext != null) {
            val isSender = transaction.bankName == currentBankNameContext && 
                (transaction.accountNumber == currentAccountContext || transaction.fromAccount == currentAccountContext)
            val isReceiver = !isSender && transaction.toAccount == currentAccountContext
            
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
                TransactionType.INCOME -> if (!isDark) income_light else income_dark
                TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
                TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
                TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
                TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
                TransactionType.BALANCE_UPDATE -> if (!isDark) transfer_light else transfer_dark
                TransactionType.BORROWED -> if (!isDark) income_light else income_dark
                TransactionType.LENT -> if (!isDark) expense_light else expense_dark
            }
        }
    }

    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "MMM d", locale)
    }
    val defaultSubtitle = remember(transaction?.dateTime) { 
        transaction?.dateTime?.format(dateTimeFormatter) ?: "" 
    }
    val amountText = remember(transaction, amountOverride, finalAmount, effectiveSign) {
        amountOverride ?: transaction?.let {
             // The receiving side of a transfer between currencies shows what arrived there
             val received = it.toAmount?.takeIf { effectiveSign == "+" }
             val formatted = if (received != null) CurrencyFormatter.formatCurrency(received, it.toCurrency ?: it.currency)
                 else it.formatAmount()
             if (effectiveSign != null) "$effectiveSign $formatted" else formatted
        } ?: finalAmount.toString()
    }

    val dateTagColor = remember(transaction?.dateTime,) {
        val colors = listOf(income_dark, expense_dark, credit_dark, transfer_dark, investment_dark)

        val dateHash = transaction?.dateTime?.toLocalDate()?.hashCode() ?: 0
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
                            model = linkedLoanPersonAvatar,
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
                    category = transaction?.category,
                    subcategory = transaction?.subcategory,
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
    val balanceAfterStr = balanceAfter?.let { balance ->
        stringResource(R.string.balance_after_format, CurrencyFormatter.formatCurrency(balance, balanceCurrency ?: transaction?.currency ?: "CNY"))
    }
    val (subtitleParts, subtitleFinal) = remember(
        subtitleOverride,
        defaultSubtitle,
        isRecurring,
        recurringStr,
        balanceAfterStr
    ) {
        val parts = buildList {
            if (subtitleOverride != null) {
                add(subtitleOverride)
            } else {
                if (defaultSubtitle.isNotEmpty()) {
                    add(defaultSubtitle)
                }
                if (isRecurring) add(recurringStr)

                balanceAfterStr?.let { add(it) }
            }
        }
        parts to parts.joinToString(" • ")
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
                            text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(category.name),
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


                        // Balance Tag
                        balanceAfter?.let { balance ->
                            TagSeparator()
                            SubtitleTag(
                                text = stringResource(R.string.balance_after_format, CurrencyFormatter.formatCurrency(balance, balanceCurrency ?: transaction?.currency ?: "CNY")),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            needsSeparator = true
                        }

                        // Description Indicator
                        if (transaction?.description?.isNotBlank() == true) {
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
                        if (transaction?.attachments?.isNotBlank() == true) {
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
                                TransactionType.CREDIT -> Icon(
                                    Iconax.Card,
                                    contentDescription = stringResource(R.string.type_credit_card),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) credit_light else credit_dark
                                )

                                TransactionType.TRANSFER -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_transfer),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) transfer_light else transfer_dark
                                )

                                TransactionType.INVESTMENT -> Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = stringResource(R.string.type_investment),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) investment_light else investment_dark
                                )

                                TransactionType.INCOME -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.type_income),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) income_light else income_dark
                                )

                                TransactionType.EXPENSE -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.type_expense),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) expense_light else expense_dark
                                )

                                TransactionType.BALANCE_UPDATE -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_balance_update),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) transfer_light else transfer_dark
                                )

                                TransactionType.LENT -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.type_lent),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isAppInDarkTheme) expense_light else expense_dark
                                )

                                TransactionType.BORROWED -> Icon(
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
                        convertedAmount != null -> "≈ ${CurrencyFormatter.formatCurrency(convertedAmount, mainCurrency!!)}"
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TransactionLeadingSlot(
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Crossfade(
        targetState = isSelectionMode,
        modifier = modifier.size(40.dp),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "transactionLeadingSelection"
    ) { selectionMode ->
        if (selectionMode) {
            HootbookCheckbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            icon()
        }
    }
}
