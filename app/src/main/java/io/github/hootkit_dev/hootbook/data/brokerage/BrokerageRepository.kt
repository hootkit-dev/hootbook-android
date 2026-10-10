package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Public state deliberately omits credentials. Provider/account IDs are scoped per connection. */
data class BrokerConnection(
    val id: String,
    val providerId: String,
    val label: String,
    val accounts: List<BrokerageAccount>,
    val syncedAt: Long
)

private val backupJson = Json { ignoreUnknownKeys = true }

@Singleton
class BrokerageRepository internal constructor(
    private val store: BrokerageStore,
    providers: Set<BrokerageProvider>,
    private val now: () -> Long
) {
    @Inject internal constructor(store: BrokerageStore, providers: Set<@JvmSuppressWildcards BrokerageProvider>) :
        this(store, providers, System::currentTimeMillis)

    private val providers = providers.associateBy { it.id }.also { require(it.size == providers.size) }

    /** Every registered provider, in a stable order: the rows of the "Broker auto-sync" list. */
    val availableProviders: List<BrokerageProvider> = this.providers.values.sortedBy { it.id }
    private val mutex = Mutex()
    private var saved: List<SavedBrokerConnection>? = null
    private val _connections = MutableStateFlow<List<BrokerConnection>>(emptyList())
    val connections = _connections.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { loadLocked() } }

    suspend fun connect(providerId: String, label: String, credentials: BrokerCredentials) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            if (saved!!.any { it.providerId == providerId && it.credentials.fields == credentials.fields }) {
                throw BrokerageException(BrokerageError.DUPLICATE_CONNECTION)
            }
            val accounts = provider(providerId).fetchHoldings(credentials)
            // Two queries for the same IB account should not create duplicate portfolio entries.
            if (saved!!.any { old -> old.providerId == providerId && old.accounts.any { a -> accounts.any { it.accountId == a.accountId } } }) {
                throw BrokerageException(BrokerageError.DUPLICATE_CONNECTION)
            }
            persist(saved!! + SavedBrokerConnection(UUID.randomUUID().toString(), providerId,
                label.trim().take(60).ifBlank { providerId }, credentials, accounts, now()))
        }
    }

    suspend fun refresh(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val old = saved!!.firstOrNull { it.id == id } ?: return@withLock
            val accounts = provider(old.providerId).fetchHoldings(old.credentials)
            if (saved!!.any { other -> other.id != id && other.providerId == old.providerId &&
                    other.accounts.any { a -> accounts.any { it.accountId == a.accountId } } }) {
                throw BrokerageException(BrokerageError.DUPLICATE_CONNECTION)
            }
            // Retain the last good snapshot on failure, including older/out-of-order reports.
            val merged = accounts.map { incoming ->
                old.accounts.find { it.accountId == incoming.accountId && it.asOf > incoming.asOf } ?: incoming
            }
            persist(saved!!.map { if (it.id == id) SavedBrokerConnection(id, old.providerId, old.label,
                old.credentials, merged, now()) else it })
        }
    }

    suspend fun disconnect(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            persist(saved!!.filterNot { it.id == id })
        }
    }

    /**
     * Every connection with its credentials, for a backup the user chose to include them in.
     * Null when there are none.
     */
    suspend fun exportWithCredentials(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            saved!!.takeIf { it.isNotEmpty() }?.let { backupJson.encodeToString(it) }
        }
    }

    /**
     * Adds the connections from a backup. One whose provider already has the same credentials or
     * any of the same accounts here is skipped, as connecting it again would be. Returns how many
     * were added.
     */
    suspend fun restore(exported: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val incoming = backupJson.decodeFromString<List<SavedBrokerConnection>>(exported)
            val added = mutableListOf<SavedBrokerConnection>()
            incoming.filter { it.providerId in providers }.forEach { connection ->
                val present = saved!! + added
                val duplicate = present.any { old ->
                    old.providerId == connection.providerId && (old.credentials.fields == connection.credentials.fields ||
                        old.accounts.any { a -> connection.accounts.any { it.accountId == a.accountId } })
                }
                if (!duplicate) added += SavedBrokerConnection(
                    UUID.randomUUID().toString(), connection.providerId, connection.label,
                    connection.credentials, connection.accounts, connection.syncedAt
                )
            }
            if (added.isNotEmpty()) persist(saved!! + added)
            added.size
        }
    }

    private fun provider(id: String) = providers[id] ?: throw BrokerageException(BrokerageError.UNSUPPORTED_PROVIDER)
    private fun loadLocked() {
        if (saved == null) { saved = store.read(); publish() }
    }
    private fun persist(next: List<SavedBrokerConnection>) {
        store.write(next) // Commit first; failed disk writes leave both previous states intact.
        saved = next
        publish()
    }
    private fun publish() {
        _connections.value = saved.orEmpty().map { BrokerConnection(it.id, it.providerId, it.label, it.accounts, it.syncedAt) }
    }
}
