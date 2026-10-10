package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BrokerageRepositoryTest {
    private class MemoryStore : BrokerageStore {
        var data = emptyList<SavedBrokerConnection>()
        var failWrite = false
        override fun read() = data
        override fun write(connections: List<SavedBrokerConnection>) {
            if (failWrite) throw BrokerageException(BrokerageError.STORAGE)
            data = connections
        }
    }
    private class FakeProvider(override val id: String = "fake") : BrokerageProvider {
        var accounts = listOf(BrokerageAccount("TEST", "2026-09-04", listOf(Holding("1", "TEST", "Test", "USD", "1", "100"))))
        var error: BrokerageError? = null
        var calls = 0
        override suspend fun fetchHoldings(credentials: BrokerCredentials): List<BrokerageAccount> {
            calls++; error?.let { throw BrokerageException(it) }; return accounts
        }
    }
    private val store = MemoryStore()
    private val provider = FakeProvider()
    private val credentials = BrokerCredentials(mapOf("token" to "PRIVATE_TOKEN"))
    private val repo = BrokerageRepository(store, setOf(provider)) { 1000L }

    @Test fun connectsAndReloadsFromStoreWithoutNetwork() = runTest {
        repo.connect("fake", "Personal", credentials)
        assertEquals("Personal", repo.connections.value.single().label)
        val reloaded = BrokerageRepository(store, setOf(provider)) { 2000L }
        reloaded.load()
        assertEquals(repo.connections.value, reloaded.connections.value)
        assertEquals(1, provider.calls)
        assertFalse(repo.connections.value.toString().contains("PRIVATE_TOKEN"))
        assertFalse(credentials.toString().contains("PRIVATE_TOKEN"))
    }
    @Test fun everyRegisteredProviderIsListedForAutoSync() {
        val second = FakeProvider("another")
        val listed = BrokerageRepository(store, setOf(provider, second)) { 1000L }.availableProviders
        assertEquals(listOf("another", "fake"), listed.map { it.id })
        // Without its own catalog entry, a provider is shown under its id
        assertEquals("fake", provider.institutionId)
    }
    @Test fun refreshReplacesInsteadOfAppendingHoldings() = runTest {
        repo.connect("fake", "Personal", credentials)
        provider.accounts = listOf(BrokerageAccount("TEST", "2026-09-05", emptyList()))
        repo.refresh(repo.connections.value.single().id)
        assertTrue(repo.connections.value.single().accounts.single().holdings.isEmpty())
    }
    @Test fun failedSyncRetainsLastGoodSnapshot() = runTest {
        repo.connect("fake", "Personal", credentials)
        val before = repo.connections.value
        provider.error = BrokerageError.NETWORK
        try { repo.refresh(before.single().id); fail() } catch (_: BrokerageException) { }
        assertEquals(before, repo.connections.value)
    }
    @Test fun olderReportCannotReplaceNewerSnapshot() = runTest {
        repo.connect("fake", "Personal", credentials)
        provider.accounts = listOf(BrokerageAccount("TEST", "2026-09-03", emptyList()))
        repo.refresh(repo.connections.value.single().id)
        assertEquals("2026-09-04", repo.connections.value.single().accounts.single().asOf)
        assertEquals(1, repo.connections.value.single().accounts.single().holdings.size)
    }
    @Test fun disconnectDeletesCredentialsAndCachedHoldings() = runTest {
        repo.connect("fake", "Personal", credentials)
        repo.disconnect(repo.connections.value.single().id)
        assertTrue(store.data.isEmpty()); assertTrue(repo.connections.value.isEmpty())
    }
    @Test fun rejectsDuplicateCredentialsWithoutAnotherRequest() = runTest {
        repo.connect("fake", "Personal", credentials)
        try { repo.connect("fake", "Copy", credentials); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.DUPLICATE_CONNECTION, e.error) }
        assertEquals(1, provider.calls)
    }
    @Test fun rejectsOverlappingAccountsFromDifferentQueries() = runTest {
        repo.connect("fake", "Personal", credentials)
        try { repo.connect("fake", "Copy", BrokerCredentials(mapOf("token" to "OTHER"))); fail() }
        catch (e: BrokerageException) { assertEquals(BrokerageError.DUPLICATE_CONNECTION, e.error) }
        assertEquals(1, repo.connections.value.size)
    }
    @Test fun secondProviderUsesSameRepositoryAndAccountModel() = runTest {
        val other = FakeProvider("other_broker")
        val multi = BrokerageRepository(store, setOf(provider, other)) { 1000L }
        multi.connect("fake", "One", credentials)
        multi.connect("other_broker", "Two", credentials)
        assertEquals(2, multi.connections.value.size)
        assertEquals(setOf("fake", "other_broker"), multi.connections.value.map { it.providerId }.toSet())
    }
    @Test fun failedSaveDoesNotPublishOrReplaceStoredState() = runTest {
        repo.connect("fake", "Personal", credentials)
        val before = repo.connections.value
        store.failWrite = true
        try { repo.disconnect(before.single().id); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.STORAGE, e.error) }
        assertEquals(before, repo.connections.value); assertEquals(1, store.data.size)
    }
    @Test fun failedInitialConnectionDoesNotSaveSecrets() = runTest {
        provider.error = BrokerageError.INVALID_CREDENTIALS
        try { repo.connect("fake", "Personal", credentials); fail() } catch (_: BrokerageException) { }
        assertTrue(store.data.isEmpty()); assertTrue(repo.connections.value.isEmpty())
    }
    @Test fun missingProviderProducesControlledError() = runTest {
        try { repo.connect("unknown", "Personal", credentials); fail() }
        catch (e: BrokerageException) { assertEquals(BrokerageError.UNSUPPORTED_PROVIDER, e.error) }
    }

    @Test fun backupRestoresConnectionsAndSkipsOnesAlreadyPresent() = runTest {
        repo.connect("fake", "Personal", credentials)
        val exported = repo.exportWithCredentials()!!

        val fresh = BrokerageRepository(MemoryStore(), setOf(provider)) { 3000L }
        assertEquals(1, fresh.restore(exported))
        assertEquals("Personal", fresh.connections.value.single().label)
        assertEquals(repo.connections.value.single().accounts, fresh.connections.value.single().accounts)
        // Restoring the same backup again adds nothing, and the provider is never called
        assertEquals(0, fresh.restore(exported))
        assertEquals(0, repo.restore(exported))
        assertEquals(1, provider.calls)
    }

    @Test fun noBackupWithoutConnections() = runTest {
        assertNull(repo.exportWithCredentials())
    }
}
