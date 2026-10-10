package io.github.hootkit_dev.hootbook.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BaseCurrencyMigrationTest {
    private val currency = stringPreferencesKey("base_currency")
    private val onboarding = booleanPreferencesKey("has_shown_scan_tutorial")

    @Test
    fun `fresh install pins yuan once before onboarding completes`() = runBlocking {
        val migration = BaseCurrencyMigration()
        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val migrated = migration.migrate(emptyPreferences())
        assertEquals("CNY", migrated[currency])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `established install keeps historical implicit rupees and unrelated preferences`() = runBlocking {
        val migration = BaseCurrencyMigration()
        val migrated = migration.migrate(preferencesOf(onboarding to true))
        assertEquals("INR", migrated[currency])
        assertEquals(true, migrated[onboarding])
    }

    @Test
    fun `explicit currency skips migration and is never overwritten`() = runBlocking {
        val migration = BaseCurrencyMigration()
        val existing = preferencesOf(currency to "HKD", onboarding to true)
        assertFalse(migration.shouldMigrate(existing))
        assertEquals("HKD", migration.migrate(existing)[currency])
    }
}
