package dev.hootkit.platform

import androidx.test.platform.app.InstrumentationRegistry
import dev.hootkit.core.*
import java.math.BigDecimal
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Runs against Android bindings, JNA AAR and the packaged Android .so, never a host JVM library. */
class CoreSmokeTest {
    @Test fun openWriteCloseAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "core-smoke-${UUID.randomUUID()}").apply { mkdirs() }
        val changes = ChangeBus()
        val store = KeystoreSecureStore(context)
        val config = CoreConfig(File(directory, "hootkit.sqlite").absolutePath,
            File(directory, "backups").absolutePath, File(directory, "attachments").absolutePath, "zh-CN",
            LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u))
        suspend fun open() = openCore(config, changes, LogcatLogger(), store, OkHttpTransport(), null, null)
        try {
            val first = open()
            val accountId: String
            try {
                assertEquals("1.1", first.version().spec)
                accountId = first.accounts().create(NewAccount("Smoke Wallet", null, AccountKind.WALLET, "CNY",
                    BigDecimal("100.00"), LocalDateTime(2026, 10u, 1u, 0u, 0u, 0u, 0u),
                    null, null, null, null, null, null, emptyList()))
                first.ledger().add(TransactionDraft(
                    kind = TransactionKind.EXPENSE, amount = BigDecimal("10.25"), currency = "CNY",
                    dateTime = LocalDateTime(2026, 10u, 10u, 12u, 0u, 0u, 0u), accountId = accountId,
                    toAccountId = null, toCurrency = null, toAmount = null,
                    categoryId = first.categories().list(CategoryKind.EXPENSE, false).first().id,
                    subcategoryId = null, merchant = "Core smoke", notes = null,
                    subscriptionId = null, attachments = emptyList(),
                ))
                assertEquals(0, first.accounts().get(accountId).pockets.single().balance.compareTo(BigDecimal("89.75")))
                assertEquals(SyncPhase.UNAVAILABLE, first.sync().status().phase)
            } finally { first.shutdown(); first.close() }
            val second = open()
            try {
                assertEquals(0, second.accounts().get(accountId).pockets.single().balance.compareTo(BigDecimal("89.75")))
            } finally { second.shutdown(); second.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun secureValuesPersistAndRemove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "test.${UUID.randomUUID()}"
        val first = KeystoreSecureStore(context)
        try {
            first.set(key, byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), KeystoreSecureStore(context).get(key))
            first.remove(key)
            assertNull(KeystoreSecureStore(context).get(key))
        } finally { first.remove(key) }
    }
}
