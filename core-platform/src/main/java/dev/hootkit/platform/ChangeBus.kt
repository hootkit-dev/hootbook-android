package dev.hootkit.platform

import dev.hootkit.core.ChangeListener
import dev.hootkit.core.ChangeSet
import dev.hootkit.core.Table
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Durable invalidation state, including commits made without any UI collectors. */
class ChangeBus(private val reportFailure: (Throwable) -> Unit = {}) : ChangeListener {
    private val guard = Any()
    private val mutableRevisions = MutableStateFlow(Table.entries.associateWith { 0L })
    val revisions: StateFlow<Map<Table, Long>> = mutableRevisions.asStateFlow()

    override fun onChange(change: ChangeSet) {
        try {
            synchronized(guard) {
                val next = mutableRevisions.value.toMutableMap()
                change.tables.distinct().forEach { table -> next[table] = next.getValue(table) + 1 }
                mutableRevisions.value = next
            }
        } catch (failure: Throwable) {
            // This UniFFI callback has no Throws: reporting must not escape either.
            runCatching { reportFailure(failure) }
        }
    }

    /** Emits an initial snapshot, then only changes to this query's dependent tables. */
    fun observe(tables: Set<Table>): Flow<Map<Table, Long>> {
        require(tables.isNotEmpty())
        return revisions.map { snapshot -> tables.associateWith(snapshot::getValue) }
            .distinctUntilChanged()
    }
}
