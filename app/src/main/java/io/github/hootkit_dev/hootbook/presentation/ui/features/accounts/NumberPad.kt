package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.ui.components.maskAccountNumber
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumberPad(
    initialValue: String = "0",
    onDone: (String) -> Unit,
    title: String = stringResource(R.string.enter_amount),
    bankName: String? = null,
    accountLast4: String? = null,
    doneButtonLabel: String = stringResource(R.string.done)
) {
    var expression by remember { mutableStateOf(if (initialValue == "0") "" else initialValue) }
    var result by remember { mutableStateOf(initialValue) }

    // Logic to evaluate expression
    fun evaluate(expr: String): String {
        if (expr.isEmpty()) return "0"
        return try {
            val eval = SimpleMathEvaluator.eval(expr)
            if (eval.isInfinite() || eval.isNaN()) return result
            BigDecimal(eval).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            result
        }
    }

    LaunchedEffect(expression) {
        if (expression.isNotEmpty()) {
            val lastChar = expression.last()
            if (lastChar.isDigit() || lastChar == ')' || lastChar == '%') {
                result = evaluate(expression)
            }
        } else {
            result = "0"
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(if (doneButtonLabel == stringResource(R.string.update_balance)) 0.9f else 0.8f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (bankName != null && accountLast4 != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = maskAccountNumber(accountLast4) ?: stringResource(R.string.type_wallet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Display Area
            val isCalculation = remember(expression) {
                expression.any { it in "+-*/%(" }
            }

            Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                    horizontalAlignment = Alignment.End
            ) {
                if (isCalculation) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
                Text(
                    text = if (isCalculation) result else expression.ifEmpty { "0" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            // Grid of buttons
            val buttons =
                listOf(
                    "AC",
                    "()",
                    "%",
                    "/",
                    "7",
                    "8",
                    "9",
                    "*",
                    "4",
                    "5",
                    "6",
                    "-",
                    "1",
                    "2",
                    "3",
                    "+",
                    "0",
                    ".",
                    "⌫",
                    "="
                )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(buttons) { btn ->
                    NumberPadButton(
                        text = btn,
                        onClick = {
                            when (btn) {
                                "AC" -> {
                                    expression = ""
                                    result = "0"
                                }
                                "⌫" ->
                                    if (expression.isNotEmpty())
                                        expression = expression.dropLast(1)
                                "=" -> { expression = result }
                                "()" -> {
                                    val openCount = expression.count { it == '(' }
                                    val closeCount = expression.count { it == ')' }
                                    if (openCount == closeCount ||
                                        expression.lastOrNull() in
                                        listOf('+', '-', '*', '/', '%', '(')) {
                                        expression += "("
                                    } else {
                                        expression += ")"
                                    }
                                }
                                else -> {
                                    if (btn in "0123456789.") {
                                        expression += btn
                                    } else {
                                        if (expression.isNotEmpty() &&
                                            expression.last() !in "+-*/%.") {
                                            expression += btn
                                        }
                                    }
                                }
                                }
                            },
                        isOperator = btn in listOf("AC", "()", "%", "/", "*", "-", "+", "⌫", "="),
                        isAction = btn == "=",
                        isError = btn == "AC"
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
        Button(
            onClick = { onDone(result) },
            modifier = Modifier
                .padding(horizontal = Dimensions.Padding.content)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(56.dp)
                .padding(top = Spacing.sm),
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(Icons.Default.Done, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(doneButtonLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumberPadButton(
    text: String,
    onClick: () -> Unit,
    isOperator: Boolean = false,
    isAction: Boolean = false,
    isError: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isAction -> MaterialTheme.colorScheme.primary
                isOperator -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant },
            contentColor = when {
                isError -> MaterialTheme.colorScheme.onErrorContainer
                isAction -> MaterialTheme.colorScheme.onPrimary
                isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant },
            ),
        shapes = ButtonDefaults.shapes(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "⌫") {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.backspace),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private object SimpleMathEvaluator {
    fun eval(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) return x // Silent ignore for partial expressions
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm() else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) return 0.0
                        x /= divisor
                    } else if (eat('%'.code)) x %= parseFactor() else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                    while (ch >= '0'.code && ch <= '9'.code ||
                        ch == '.'.code
                    ) nextChar()
                    val subString = str.substring(startPos, pos)
                    x = subString.toDoubleOrNull() ?: 0.0
                } else {
                    x = 0.0
                }
                return x
            }
        }
            .parse()
    }
}
