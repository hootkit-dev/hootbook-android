package io.github.hootkit_dev.hootbook.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.hootkit.core.TransactionKind

/**
 * Whether the app is drawing its dark scheme. The app's own theme setting can differ from the
 * system's, so read it from the color scheme rather than isSystemInDarkTheme().
 */
val isAppInDarkTheme: Boolean
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The color of an amount of this type, in the light or dark variant the app is showing. */
@Composable
@ReadOnlyComposable
fun transactionTypeColor(type: TransactionKind): Color {
    val dark = isAppInDarkTheme
    return when (type) {
        TransactionKind.INCOME, TransactionKind.LOAN_IN -> if (dark) income_dark else income_light
        TransactionKind.EXPENSE, TransactionKind.LOAN_OUT -> if (dark) expense_dark else expense_light
        TransactionKind.TRANSFER, TransactionKind.UNKNOWN -> if (dark) transfer_dark else transfer_light
        TransactionKind.INVESTMENT -> if (dark) investment_dark else investment_light
    }
}

/** Positive / caution colors for meters and statuses (utilization, overdue), theme-aware. */
val successColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme) success_dark else success_light

val warningColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme) warning_dark else warning_light

/**
 * Money in and out as tonal roles (tone 40 on light, 80 on dark, with containers), for figures
 * that compare the two. Spending is not `error`: spending money is not a mistake.
 */
data class MoneyColors(
    val income: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color
)

val moneyColors: MoneyColors
    @Composable @ReadOnlyComposable
    get() = if (isAppInDarkTheme) MoneyColors(
        income = Color(0xFF97D5A6), incomeContainer = Color(0xFF13512E), onIncomeContainer = Color(0xFFB2F1C0),
        expense = Color(0xFFFFB4A9), expenseContainer = Color(0xFF842520), onExpenseContainer = Color(0xFFFFDAD5)
    ) else MoneyColors(
        income = Color(0xFF2E6A44), incomeContainer = Color(0xFFB2F1C0), onIncomeContainer = Color(0xFF00210E),
        expense = Color(0xFFA43C33), expenseContainer = Color(0xFFFFDAD5), onExpenseContainer = Color(0xFF410001)
    )
