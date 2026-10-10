package io.github.hootkit_dev.hootbook.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAvailabilityTest {

    @Test
    fun newerCommitCountPrompts() {
        assertTrue(UpdateAvailability.shouldPrompt(20, 10, 0))
    }

    @Test
    fun sameOrOlderCommitCountDoesNotPrompt() {
        assertFalse(UpdateAvailability.shouldPrompt(10, 10, 0))
        assertFalse(UpdateAvailability.shouldPrompt(9, 10, 0))
        assertFalse(UpdateAvailability.shouldPrompt(0, 10, 0))
    }

    @Test
    fun dismissedCommitCountDoesNotPromptAgain() {
        assertFalse(UpdateAvailability.shouldPrompt(20, 10, 20))
    }

    @Test
    fun newerThanDismissedPrompts() {
        assertTrue(UpdateAvailability.shouldPrompt(21, 10, 20))
    }

    @Test
    fun parseCommitCountPrefersBody() {
        val count = DebugBuildIdentity.parseCommitCount(
            title = "Debug c12 (abc1234)",
            body = "commit_count: 42\ncommit: abc",
            assetNames = listOf("Hootbook-debug-c7-abc1234-arm64-v8a.apk")
        )
        assertEquals(42, count)
    }

    @Test
    fun parseCommitCountFromApkName() {
        val count = DebugBuildIdentity.parseCommitCount(
            title = "Debug build",
            body = null,
            assetNames = listOf("Hootbook-debug-c1842-b73f2c3-arm64-v8a.apk")
        )
        assertEquals(1842, count)
    }

    @Test
    fun parseCommitCountFromTestingApkName() {
        val count = DebugBuildIdentity.parseCommitCount(
            title = "Testing build",
            body = null,
            assetNames = listOf("Hootbook-testing-c1901-b73f2c3-arm64-v8a.apk")
        )
        assertEquals(1901, count)
    }
}
