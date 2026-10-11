package io.github.hootkit_dev.hootbook.core.sync

import android.app.Application
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreSyncIdentityStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun freshLedgerFirstUidIsDurableAndOnlySameUidCanReturnAfterRestart() = runTest {
        val file = File(temp.newFolder(), "install-auth.json"); val store = CoreSyncIdentityStore(file, true, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, store.accept("uid-A")); assertTrue(file.exists())
        val restarted = CoreSyncIdentityStore(file, false, Unit); assertEquals(CoreIdentityDecision.ACCEPTED, restarted.accept("uid-A"))
        assertEquals(CoreIdentityDecision.DIFFERENT_UID_BLOCKED, restarted.accept("uid-B")); assertFalse(file.readText().contains("uid-B"))
    }
    @Test fun offlineFreshSessionWritesUnboundWitnessBeforeCoreOpenAndColdFirstLoginWorks() = runTest {
        val file = File(temp.newFolder(), "install-auth.json")
        CoreSyncIdentityStore(file, true, Unit) // Capabilities stage, before native Core DB exists.
        val json = org.json.JSONObject(file.readText())
        assertEquals(true, json.getBoolean("freshBeforeCoreOpen")); assertTrue(json.has("boundUid")); assertTrue(json.isNull("boundUid"))
        // A later process now observes an existing Core DB, but has the durable App fresh witness.
        val cold = CoreSyncIdentityStore(file, false, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, cold.accept("uid-A"))
        assertEquals(CoreIdentityDecision.DIFFERENT_UID_BLOCKED, cold.accept("uid-B"))
    }
    @Test fun missingUnboundWitnessIsNotRecreatedAfterCoreHasOpened() = runTest {
        val file = File(temp.newFolder(), "install-auth.json")
        val fresh = CoreSyncIdentityStore(file, true, Unit); assertTrue(file.delete())
        assertEquals(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, fresh.accept("uid-A"))
        assertEquals(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, CoreSyncIdentityStore(file, false, Unit).accept("uid-A"))
        assertFalse(file.exists())
    }
    @Test fun boundLegacySchemaOneWithoutWitnessKeepsOriginalUidRestriction() = runTest {
        val file = temp.newFile().apply { writeText("{\"schema\":1,\"boundUid\":\"uid-A\"}") }
        val store = CoreSyncIdentityStore(file, false, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, store.accept("uid-A")); assertEquals(CoreIdentityDecision.DIFFERENT_UID_BLOCKED, store.accept("uid-B"))
    }
    @Test fun nullUidWithoutBooleanFreshWitnessNeverPermitsFirstBinding() = runTest {
        for (marker in listOf("{}", "{\"schema\":1,\"boundUid\":null}", "{\"schema\":1,\"freshBeforeCoreOpen\":\"true\",\"boundUid\":null}", "{\"schema\":1,\"freshBeforeCoreOpen\":true,\"boundUid\":17}")) {
            val file = temp.newFile().apply { writeText(marker) }; val original = file.readText()
            assertNotEquals(CoreIdentityDecision.ACCEPTED, CoreSyncIdentityStore(file, false, Unit).accept("uid-A")); assertEquals(original, file.readText())
        }
    }
    @Test fun existingCoreLedgerWithoutIdentityIsBlockedEvenWithNoCursorOrSuccess() = runTest {
        val file = File(temp.newFolder(), "install-auth.json"); val store = CoreSyncIdentityStore(file, false, Unit)
        assertEquals(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, store.accept("uid-A")); assertFalse(file.exists())
    }
    @Test fun removedIdentityIsNotRecreatedInSameInstanceOrOnExistingLedgerRestart() = runTest {
        val file = File(temp.newFolder(), "install-auth.json"); val store = CoreSyncIdentityStore(file, true, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, store.accept("uid-A")); assertTrue(file.delete())
        assertEquals(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, store.accept("uid-B"))
        assertEquals(CoreIdentityDecision.MISSING_IDENTITY_BLOCKED, CoreSyncIdentityStore(file, false, Unit).accept("uid-A"))
    }
    @Test fun storageFailureDoesNotMakeAnInMemoryBindingOrPlaintextFallback() = runTest {
        val parentFile = temp.newFile(); val store = CoreSyncIdentityStore(File(parentFile, "install-auth.json"), true, Unit)
        assertEquals(CoreIdentityDecision.STORAGE_FAILED, store.accept("uid-A")); assertEquals(CoreIdentityDecision.STORAGE_FAILED, store.accept("uid-A"))
        assertEquals(0, parentFile.length())
    }
    @Test fun corruptIdentityIsBlockedNotGuessedOrOverwritten() = runTest {
        val file = temp.newFile().apply { writeText("not-json") }; val original = file.readText()
        assertEquals(CoreIdentityDecision.STORAGE_FAILED, CoreSyncIdentityStore(file, true, Unit).accept("uid-A")); assertEquals(original, file.readText())
    }
    @Test fun interruptedAtomicFileUpdateRecoversOldBindingInsteadOfGuessingAnotherUid() = runTest {
        val file = File(temp.newFolder(), "install-auth.json"); val store = CoreSyncIdentityStore(file, true, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, store.accept("uid-A"))
        File(file.path + ".bak").writeText(file.readText()); file.writeText("incomplete-write")
        val restarted = CoreSyncIdentityStore(file, false, Unit)
        assertEquals(CoreIdentityDecision.ACCEPTED, restarted.accept("uid-A"))
        assertEquals(CoreIdentityDecision.DIFFERENT_UID_BLOCKED, restarted.accept("uid-B"))
    }
    @Test fun concurrentFirstUidAttemptsCanBindExactlyOneUid() = runTest {
        val file = File(temp.newFolder(), "install-auth.json"); val store = CoreSyncIdentityStore(file, true, Unit)
        val a = async { store.accept("uid-A") }; val b = async { store.accept("uid-B") }
        assertEquals(1, listOf(a.await(), b.await()).count { it == CoreIdentityDecision.ACCEPTED })
        val text = file.readText(); assertTrue(text.contains("uid-A") xor text.contains("uid-B"))
    }
}
