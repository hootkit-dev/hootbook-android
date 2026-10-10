package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactAccountListTest {
    @get:Rule val rule = createComposeRule()
    private val name = "Long personal wallet account name"
    private val account = AccountBalanceEntity(bankName=name, accountLast4="", balance=BigDecimal("1234567.89"), currency="CNY", timestamp=LocalDateTime.now(), isWallet=true)
    @Test fun rowOpensDetailsAndMenuDoesNotTriggerNavigation() {
        var opened = 0
        rule.setContent { HootbookTheme(dynamicColor=false) {
            CompactAccountCard(account,false,false,{opened++},{},{},{},{},{},{},{})
        } }
        rule.onNodeWithText(name).performClick()
        rule.runOnIdle { assertEquals(1,opened) }
        // Native icon button is a separate clickable target from the account row.
        rule.onAllNodes(hasClickAction()).onLast().performClick()
        rule.runOnIdle { assertEquals(1,opened) }
    }
    @Test fun longNameAndLargeAmountRemainVisibleAtDoubleFontScale() {
        rule.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
            HootbookTheme(dynamicColor=false) { Column(Modifier.width(320.dp)) {
                CompactAccountCard(account,false,false,{},{},{},{},{},{},{},{})
            } }
        } }
        rule.onNodeWithText(name).assertIsDisplayed()
        rule.onNodeWithText("¥1,234,567.89").assertIsDisplayed()
        rule.onNodeWithText("CNY").assertIsDisplayed()
    }
}
