package io.github.hootkit_dev.hootbook.data.backup

import io.github.hootkit_dev.hootbook.data.database.entity.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Extension functions to sanitize entities deserialized from backup.
 * Gson's reflection-based deserialization can bypass Kotlin's null-safety,
 * resulting in null values for non-nullable fields if they were missing in the JSON.
 * These functions ensure all non-nullable fields have valid default values.
 */

fun CategoryEntity.sanitize(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name ?: "",
        color = color ?: "#757575",
        iconResId = iconResId,
        iconName = iconName ?: "",
        description = description ?: "",
        isSystem = isSystem,
        isIncome = isIncome,
        displayOrder = displayOrder,
        defaultName = defaultName,
        defaultColor = defaultColor,
        defaultIconResId = defaultIconResId,
        defaultIconName = defaultIconName,
        defaultDescription = defaultDescription,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun SubcategoryEntity.sanitize(): SubcategoryEntity {
    return SubcategoryEntity(
        id = id,
        categoryId = categoryId,
        name = name ?: "",
        iconResId = iconResId,
        iconName = iconName ?: "",
        color = color ?: "#757575",
        isSystem = isSystem,
        defaultName = defaultName,
        defaultIconResId = defaultIconResId,
        defaultIconName = defaultIconName,
        defaultColor = defaultColor,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun TransactionEntity.sanitize(): TransactionEntity {
    return TransactionEntity(
        id = id,
        amount = amount ?: BigDecimal.ZERO,
        merchantName = merchantName ?: "",
        category = category ?: "",
        subcategory = subcategory,
        transactionType = transactionType ?: TransactionType.EXPENSE,
        dateTime = dateTime ?: LocalDateTime.now(),
        description = description,
        bankName = bankName,
        accountNumber = accountNumber,
        balanceAfter = balanceAfter,
        transactionHash = transactionHash ?: "",
        isRecurring = isRecurring,
        isDeleted = isDeleted,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        currency = currency ?: "CNY",
        fromAccount = fromAccount,
        toAccount = toAccount,
        toAmount = toAmount,
        accountId = accountId,
        toAccountId = toAccountId,
        toCurrency = toCurrency,
        billingCycle = billingCycle,
        attachments = attachments ?: "",
        isSample = isSample,
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun CardEntity.sanitize(): CardEntity {
    return CardEntity(
        id = id,
        cardLast4 = cardLast4 ?: "",
        cardType = cardType ?: CardType.DEBIT,
        bankName = bankName ?: "",
        accountLast4 = accountLast4,
        nickname = nickname,
        isActive = isActive,
        lastBalance = lastBalance,
        lastBalanceDate = lastBalanceDate,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        currency = currency ?: "CNY",
        isSample = isSample,
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun SubscriptionEntity.sanitize(): SubscriptionEntity {
    return SubscriptionEntity(
        id = id,
        merchantName = merchantName ?: "",
        amount = amount ?: BigDecimal.ZERO,
        nextPaymentDate = nextPaymentDate,
        state = state ?: SubscriptionState.ACTIVE,
        bankName = bankName,
        category = category,
        subcategory = subcategory,
        notes = notes,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        currency = currency ?: "CNY",
        billingCycle = billingCycle,
        lastPaidDate = lastPaidDate,
        isSample = isSample,
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun BudgetEntity.sanitize(): BudgetEntity {
    return BudgetEntity(
        id = id,
        name = name ?: "",
        amount = amount ?: BigDecimal.ZERO,
        year = year,
        month = month,
        currency = currency ?: "CNY",
        isActive = isActive,
        createdAt = createdAt ?: LocalDateTime.now(),
        updatedAt = updatedAt ?: LocalDateTime.now(),
        startDate = startDate ?: LocalDateTime.now(),
        endDate = endDate ?: (startDate ?: LocalDateTime.now()).plusMonths(1),
        periodType = periodType ?: BudgetPeriod.MONTHLY,
        trackType = trackType ?: BudgetTrackType.ALL_TRANSACTIONS,
        budgetType = budgetType ?: BudgetType.EXPENSE,
        accountIds = accountIds ?: emptyList(),
        color = color ?: "#4CAF50",
        isSample = isSample,
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}

fun AccountBalanceEntity.sanitize(): AccountBalanceEntity {
    return AccountBalanceEntity(
        id = id,
        iconResId = iconResId,
        iconName = iconName ?: "",
        bankName = bankName ?: "",
        accountLast4 = accountLast4 ?: "",
        balance = balance ?: BigDecimal.ZERO,
        timestamp = timestamp ?: LocalDateTime.now(),
        transactionId = transactionId,
        creditLimit = creditLimit,
        isCreditCard = isCreditCard,
        
        sourceType = sourceType,
        createdAt = createdAt ?: LocalDateTime.now(),
        currency = currency ?: "CNY",
        isWallet = isWallet,
        color = color ?: "#33B5E5",
        isSample = isSample,
        accountId = accountId,
        syncId = syncId ?: "",
        syncUpdatedAt = syncUpdatedAt
    )
}
