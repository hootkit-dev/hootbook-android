package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.LegacyCount
import dev.hootkit.core.LegacyImportSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationPhase
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationPreview
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationUiState
import io.github.hootkit_dev.hootbook.core.migration.LegacyServiceSettings
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Synthetic generated Core records only. No database, JNI handle, service key or Hilt graph. */
class LegacyUpgradeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun summary(transactions: UInt = 17u) = LegacyImportSummary(
        imported = listOf(LegacyCount("accounts", 3u), LegacyCount("transactions", transactions)),
        dropped = listOf(LegacyCount("unknown_transactions", 2u), LegacyCount("downloaded_rates", 7u)),
        mainCurrency = "CNY",
        preferencesJson = "{\"syntheticPrivateMarker\":\"not-for-display\"}",
        platformFiles = emptyList(),
        renamedCurrencies = emptyMap(),
    )

    private fun preview(aiConfigured: Boolean? = null, aiHasKey: Boolean? = null) = LegacyMigrationPreview(
        summary = summary(),
        omittedTemplates = 4,
        omittedDefaultAccount = true,
        missingAttachments = 2,
        missingAvatars = 1,
        services = LegacyServiceSettings.Preview(
            aiPresent = true,
            aiExported = true,
            aiRequiresSetup = aiConfigured == false || aiHasKey == false,
            aiConfigured = aiConfigured,
            aiHasKey = aiHasKey,
            brokeragePresent = true,
            brokerageConnections = 2,
            brokerageAccounts = 3,
            brokerageHoldings = 9,
        ),
    )

    private class Actions {
        var prepare = 0
        val migrations = mutableListOf<Boolean>()
        var resumePlatform = 0
        var reload = 0
        var continueToApp = 0
        var export = 0
    }

    private fun render(
        state: MutableState<LegacyMigrationUiState>,
        actions: Actions,
        becomeBusyOnMigration: Boolean = false,
        becomeBusyOnResume: Boolean = false,
    ) {
        rule.setContent {
            HootbookTheme(dynamicColor = false) {
                LegacyUpgradeContent(
                    state = state.value,
                    onPrepare = { actions.prepare++ },
                    onMigrate = {
                        actions.migrations += it
                        if (becomeBusyOnMigration) state.value = state.value.copy(busy = true)
                    },
                    onResumePlatform = {
                        actions.resumePlatform++
                        if (becomeBusyOnResume) state.value = state.value.copy(busy = true)
                    },
                    onReload = { actions.reload++ },
                    onContinue = { actions.continueToApp++ },
                    onExport = { actions.export++ },
                )
            }
        }
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag("legacy-upgrade-content").performScrollToNode(hasTestTag("legacy-upgrade-$tag"))
        return rule.onNodeWithTag("legacy-upgrade-$tag")
    }

    private fun assertCount(tag: String, expected: String) {
        scrollTo(tag).assert(hasAnyDescendant(hasText(expected)))
    }

    /** Search the entire lazy list, rather than mistake an offscreen action for an absent one. */
    private fun assertActionAbsent(tag: String) {
        var found = true
        try {
            scrollTo(tag)
        } catch (missing: AssertionError) {
            // Other assertion failures still fail the test (for example, an absent list).
            if (missing.message?.contains("No node found") != true) throw missing
            found = false
        }
        assertFalse("Unexpected action: $tag", found)
        rule.onNodeWithTag("legacy-upgrade-$tag").assertDoesNotExist()
    }

    @Test fun reviewShowsCoreAndSourceCountsAndRequiresExplicitLossAcceptance() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(LegacyMigrationPhase.REVIEW, preview = preview()))
        render(state, actions, becomeBusyOnMigration = true)

        assertCount("imported-accounts", "3")
        assertCount("imported-transactions", "17")
        assertCount("dropped-unknown_transactions", "2")
        assertCount("dropped-downloaded_rates", "7")
        assertCount("templates", "4")
        assertCount("default-account", "1")
        assertCount("missing-attachments", "2")
        assertCount("missing-avatars", "1")
        assertCount("service-connections", "2")
        assertCount("service-accounts", "3")
        assertCount("service-holdings", "9")
        rule.onNodeWithTag("legacy-upgrade-content").performScrollToNode(
            hasText(context.getString(R.string.legacy_upgrade_financial_warning_title)),
        )
        rule.onNodeWithText(context.getString(R.string.legacy_upgrade_financial_warning_title)).assertExists()
        scrollTo("migrate").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(emptyList<Boolean>(), actions.migrations) }
        scrollTo("accept-losses").assertIsOff().performClick().assertIsOn()
        scrollTo("migrate").assertIsEnabled().performClick()

        rule.runOnIdle { assertEquals(listOf(true), actions.migrations) }
        scrollTo("migrate").assertIsNotEnabled()
        scrollTo("accept-losses").assertIsNotEnabled()
        scrollTo("export").assertIsNotEnabled()
        rule.onAllNodesWithText("not-for-display", substring = true).assertCountEquals(0)
    }

    @Test fun serviceStatusShowsOnlyConfirmedCoreBooleansAndHidesUnknownStatus() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(
            phase = LegacyMigrationPhase.REVIEW,
            preview = preview(aiConfigured = true, aiHasKey = false),
        ))
        render(state, actions)
        assertCount("ai-configured", context.getString(R.string.legacy_upgrade_yes))
        assertCount("ai-key-present", context.getString(R.string.legacy_upgrade_no))
        // Source settings remain source counts; they do not become accepted-record counts.
        assertCount("service-holdings", "9")

        rule.runOnIdle { state.value = state.value.copy(preview = preview()) }

        assertActionAbsent("ai-configured")
        assertActionAbsent("ai-key-present")
        rule.runOnIdle { assertEquals(emptyList<Boolean>(), actions.migrations) }
    }

    @Test fun replacingThePreviewClearsConsentToThePreviousOmissions() {
        val actions = Actions()
        val original = preview()
        val state = mutableStateOf(LegacyMigrationUiState(LegacyMigrationPhase.REVIEW, preview = original))
        render(state, actions)
        scrollTo("accept-losses").performClick().assertIsOn()
        scrollTo("migrate").assertIsEnabled()

        rule.runOnIdle { state.value = state.value.copy(preview = original.copy(omittedTemplates = 5)) }

        scrollTo("accept-losses").assertIsOff()
        scrollTo("migrate").assertIsNotEnabled()
        assertCount("templates", "5")
        rule.runOnIdle { assertEquals(emptyList<Boolean>(), actions.migrations) }
    }

    @Test fun uncertainOutcomeCannotReplayOrContinueAndExportNeedsItsWarning() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(LegacyMigrationPhase.UNCERTAIN, preview = preview()))
        render(state, actions)
        rule.onNodeWithText(context.getString(R.string.legacy_upgrade_uncertain_description)).assertExists()
        assertActionAbsent("prepare")
        assertActionAbsent("migrate")
        assertActionAbsent("continue")
        assertActionAbsent("resume-platform")

        scrollTo("export").assertIsEnabled().performClick()
        rule.onNodeWithText(context.getString(R.string.legacy_upgrade_export_warning)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, actions.export) }
        rule.onNodeWithText(context.getString(R.string.legacy_upgrade_cancel)).performClick()
        rule.runOnIdle { assertEquals(0, actions.export) }
        scrollTo("export").performClick()
        rule.onNodeWithText(context.getString(R.string.legacy_upgrade_choose_destination)).performClick()
        rule.runOnIdle {
            assertEquals(1, actions.export)
            assertEquals(0, actions.prepare)
            assertEquals(emptyList<Boolean>(), actions.migrations)
            assertEquals(0, actions.resumePlatform)
            assertEquals(0, actions.continueToApp)
        }
    }

    @Test fun platformPendingShowsActualCountsAndOnlyResumesPlatformWork() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(
            phase = LegacyMigrationPhase.PLATFORM_PENDING,
            preview = preview(),
            result = summary(transactions = 13u),
        ))
        render(state, actions, becomeBusyOnResume = true)
        assertCount("imported-transactions", "13")
        assertActionAbsent("prepare")
        assertActionAbsent("migrate")
        assertActionAbsent("continue")
        scrollTo("resume-platform").assertIsEnabled().performClick()

        rule.runOnIdle {
            assertEquals(1, actions.resumePlatform)
            assertEquals(emptyList<Boolean>(), actions.migrations)
            assertEquals(0, actions.continueToApp)
        }
        scrollTo("resume-platform").assertIsNotEnabled()
        scrollTo("export").assertIsNotEnabled()
    }

    @Test fun completeUsesActualImportReportAndContinuesOnlyOnce() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(
            phase = LegacyMigrationPhase.COMPLETE,
            preview = preview(),
            result = summary(transactions = 11u),
        ))
        render(state, actions)
        assertCount("imported-transactions", "11")
        assertCount("dropped-unknown_transactions", "2")
        assertCount("templates", "4")
        assertActionAbsent("migrate")
        assertActionAbsent("resume-platform")
        scrollTo("continue").assertIsEnabled().performClick()

        rule.runOnIdle { assertEquals(1, actions.continueToApp) }
        scrollTo("continue").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(emptyList<Boolean>(), actions.migrations) }
    }

    @Test fun completeWithoutActualResultRequiresReloadAndCannotUsePreviewAsProof() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(
            phase = LegacyMigrationPhase.COMPLETE,
            preview = preview(),
            result = null,
        ))
        render(state, actions)
        assertActionAbsent("continue")
        assertActionAbsent("migrate")
        assertActionAbsent("prepare")
        scrollTo("reload").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, actions.reload)
            assertEquals(0, actions.continueToApp)
            assertEquals(emptyList<Boolean>(), actions.migrations)
        }
    }

    @Test fun completeWithoutSourceOmissionMetadataCannotContinue() {
        val actions = Actions()
        val state = mutableStateOf(LegacyMigrationUiState(
            phase = LegacyMigrationPhase.COMPLETE,
            preview = null,
            result = summary(),
        ))
        render(state, actions)
        assertActionAbsent("continue")
        scrollTo("reload").assertIsEnabled()
        rule.runOnIdle { assertEquals(0, actions.continueToApp) }
    }
}
