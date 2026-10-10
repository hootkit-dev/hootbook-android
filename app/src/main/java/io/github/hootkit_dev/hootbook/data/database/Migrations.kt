package io.github.hootkit_dev.hootbook.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Schema migrations of HootbookDatabase, oldest first. The app opens the database with
// HootbookDatabase.MIGRATIONS; MigrationChainTest walks every exported schema through them.

/**
 * Manual migration from version 1 to 2. Example of how to write manual migrations when
 * auto-migration isn't sufficient.
 */
val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Example: Add a new column
                // db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
            }
        }

/**
 * Manual migration from version 13 to 14. Adds is_deleted column and unique constraint,
 * handling existing duplicates.
 */
val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Check if sms_sender column already exists in transactions table
            val cursor = db.query("PRAGMA table_info(transactions)")
            var hasSenderColumn = false
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex == -1) continue
                val columnName = cursor.getString(nameIndex)
                if (columnName == "sms_sender") {
                    hasSenderColumn = true
                    break
                }
            }
            cursor.close()

            // Add sms_sender column to transactions table only if it doesn't exist
            if (!hasSenderColumn) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sms_sender TEXT")
            }

            // Check if is_deleted column already exists in unrecognized_sms table
            val cursor2 = db.query("PRAGMA table_info(unrecognized_sms)")
            var hasIsDeletedColumn = false
            while (cursor2.moveToNext()) {
                val nameIndex2 = cursor2.getColumnIndex("name")
                if (nameIndex2 == -1) continue
                val columnName = cursor2.getString(nameIndex2)
                if (columnName == "is_deleted") {
                    hasIsDeletedColumn = true
                    break
                }
            }
            cursor2.close()

            // Only proceed with unrecognized_sms migration if needed
            if (!hasIsDeletedColumn) {
                // First, add the is_deleted column with default value
                db.execSQL(
                    "ALTER TABLE unrecognized_sms ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0"
                )

                // Create a temporary table with the new schema (including unique
                // constraint)
                db.execSQL(
                    """
                CREATE TABLE unrecognized_sms_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sender TEXT NOT NULL,
                    sms_body TEXT NOT NULL,
                    received_at TEXT NOT NULL,
                    reported INTEGER NOT NULL,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """
                )

                // Copy data from old table, keeping only the most recent of duplicates
                db.execSQL(
                    """
                INSERT INTO unrecognized_sms_new (id, sender, sms_body, received_at, reported, is_deleted, created_at)
                SELECT id, sender, sms_body, received_at, reported, is_deleted, created_at
                FROM unrecognized_sms
                WHERE id IN (
                    SELECT MAX(id)
                    FROM unrecognized_sms
                    GROUP BY sender, sms_body
                )
            """
                )

                // Drop the old table
                db.execSQL("DROP TABLE unrecognized_sms")

                // Rename the new table to the original name
                db.execSQL(
                    "ALTER TABLE unrecognized_sms_new RENAME TO unrecognized_sms"
                )

                // Create the unique index
                db.execSQL(
                    "CREATE UNIQUE INDEX index_unrecognized_sms_sender_sms_body ON unrecognized_sms (sender, sms_body)"
                )
            }
        }
    }

/**
 * Manual migration from version 12 to 14. Handles direct upgrade from 12 to 14, combining
 * migrations 12->13 and 13->14.
 */
val MIGRATION_12_14 =
    object : Migration(12, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Same as MIGRATION_13_14 since we need to handle both cases
            MIGRATION_13_14.migrate(db)
        }
    }

/** Manual migration from version 14 to 15. Adds sms_body column to subscriptions table. */
val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add sms_body column to subscriptions table
            db.execSQL("ALTER TABLE subscriptions ADD COLUMN sms_body TEXT")
        }
    }

/**
 * Manual migration from version 20 to 21. Makes next_payment_date nullable in subscriptions
 * table. This fixes the issue where v2.15.18 had non-nullable field but v2.15.19+ needs
 * nullable.
 */
val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQLite doesn't support ALTER COLUMN, so we need to recreate the table
            // Step 1: Create new subscriptions table with nullable next_payment_date
            db.execSQL(
                """
            CREATE TABLE subscriptions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                merchant_name TEXT NOT NULL,
                amount TEXT NOT NULL,
                next_payment_date TEXT,
                state TEXT NOT NULL,
                bank_name TEXT,
                umn TEXT,
                category TEXT,
                sms_body TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """
            )

            // Step 2: Copy data from old table to new table
            db.execSQL(
                """
            INSERT INTO subscriptions_new (id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at)
            SELECT id, merchant_name, amount, next_payment_date, state, bank_name, umn, category, sms_body, created_at, updated_at
            FROM subscriptions
        """
            )

            // Step 3: Drop old table
            db.execSQL("DROP TABLE subscriptions")

            // Step 4: Rename new table to original name
            db.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
        }
    }

/**
 * Manual migration from version 21 to 22. Adds transaction_rules and rule_applications
 * tables for the rule engine. Note: This migration is kept for users who might be on v21.
 */
val MIGRATION_21_22 =
    object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create transaction_rules table
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS transaction_rules (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                priority INTEGER NOT NULL,
                conditions TEXT NOT NULL,
                actions TEXT NOT NULL,
                is_active INTEGER NOT NULL,
                is_system_template INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """
            )

            // Create indices for transaction_rules
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)"
            )

            // Create rule_applications table
            db.execSQL(
                """
            CREATE TABLE rule_applications (
                id TEXT PRIMARY KEY NOT NULL,
                rule_id TEXT NOT NULL,
                rule_name TEXT NOT NULL,
                transaction_id TEXT NOT NULL,
                fields_modified TEXT NOT NULL,
                applied_at TEXT NOT NULL,
                FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
            )
        """
            )

            // Create indices for rule_applications
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)"
            )
        }
    }

/**
 * Manual migration from version 22 to 23. Adds transaction_rules and rule_applications
 * tables for the rule engine. This is for users who were already on v22 before the rules
 * feature was added.
 */
val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop table if exists to ensure clean state
            db.execSQL("DROP TABLE IF EXISTS transaction_rules")
            db.execSQL("DROP TABLE IF EXISTS rule_applications")

            // Create transaction_rules table with all required columns
            db.execSQL(
                """
            CREATE TABLE transaction_rules (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                priority INTEGER NOT NULL,
                conditions TEXT NOT NULL,
                actions TEXT NOT NULL,
                is_active INTEGER NOT NULL,
                is_system_template INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """
            )

            // Create indices for transaction_rules
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transaction_rules_priority_is_active ON transaction_rules (priority, is_active)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_transaction_rules_name ON transaction_rules (name)"
            )

            // Create rule_applications table
            db.execSQL(
                """
            CREATE TABLE rule_applications (
                id TEXT PRIMARY KEY NOT NULL,
                rule_id TEXT NOT NULL,
                rule_name TEXT NOT NULL,
                transaction_id TEXT NOT NULL,
                fields_modified TEXT NOT NULL,
                applied_at TEXT NOT NULL,
                FOREIGN KEY(rule_id) REFERENCES transaction_rules(id) ON DELETE CASCADE,
                FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
            )
        """
            )

            // Create indices for rule_applications
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_rule_id ON rule_applications (rule_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_transaction_id ON rule_applications (transaction_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rule_applications_applied_at ON rule_applications (applied_at)"
            )
        }
    }

/**
 * Manual migration from version 54 to 55. Adds chat_sessions table and session_id column to chat_messages.
 */
val MIGRATION_54_55 =
    object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create chat_sessions table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """
            )

            // Insert legacy session to preserve existing messages
            val currentTime = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO chat_sessions (id, title, createdAt) VALUES ('legacy_session', 'Legacy Chat', ?)",
                arrayOf<Any>(currentTime)
            )

            // Add session_id column to chat_messages with default value
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN session_id TEXT NOT NULL DEFAULT 'legacy_session'")

            // Drop the status index from bank_notifications — it was created in
            // MIGRATION_53_54 but is not declared in BankNotificationEntity,
            // causing Room schema validation to fail.
            db.execSQL("DROP INDEX IF EXISTS index_bank_notifications_status")
        }
    }

/**
 * Migration from version 55 to 56.
 * Drops the orphaned status index on bank_notifications for devices
 * that were already migrated to version 55 before the index was removed.
 */
val MIGRATION_55_56 =
    object : Migration(55, 56) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_bank_notifications_status")
        }
    }

/**
 * Migration from version 56 to 57.
 * Adds lend_borrow_persons and lend_borrow_transactions tables for Lendings/Borrowings (Khata) tracking.
 */
val MIGRATION_56_57 =
    object : Migration(56, 57) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS lend_borrow_persons (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone_number TEXT,
                    notes TEXT,
                    color TEXT NOT NULL DEFAULT '#4CAF50',
                    is_archived INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS lend_borrow_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    person_id INTEGER NOT NULL,
                    transaction_id INTEGER,
                    type TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    title TEXT NOT NULL,
                    due_date TEXT,
                    is_settled INTEGER NOT NULL DEFAULT 0,
                    date TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_sample INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(person_id) REFERENCES lend_borrow_persons(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX IF NOT EXISTS index_lend_borrow_transactions_person_id ON lend_borrow_transactions(person_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_lend_borrow_transactions_type ON lend_borrow_transactions(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_lend_borrow_transactions_date ON lend_borrow_transactions(date)")
        }
    }

/**
 * Migration from version 57 to 58.
 * Adds an optional avatar column to lend_borrow_persons so persons can have
 * a preset image or a photo picked from the gallery.
 */
val MIGRATION_57_58 =
    object : Migration(57, 58) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lend_borrow_persons ADD COLUMN avatar TEXT")
        }
    }

/**
 * Migration from version 58 to 59.
 * Adds an optional category column to lend_borrow_persons so persons can be
 * grouped (e.g. friend, family, colleague) and filtered in the ledger.
 */
val MIGRATION_58_59 =
    object : Migration(58, 59) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lend_borrow_persons ADD COLUMN category TEXT")
        }
    }

/**
 * Migration from version 59 to 60.
 * Adds account_id, category_name, merchant_name, and attachments columns to lend_borrow_transactions
 * for enhanced transaction tracking.
 */
val MIGRATION_59_60 =
    object : Migration(59, 60) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lend_borrow_transactions ADD COLUMN account_id INTEGER")
            db.execSQL("ALTER TABLE lend_borrow_transactions ADD COLUMN category_name TEXT")
            db.execSQL("ALTER TABLE lend_borrow_transactions ADD COLUMN merchant_name TEXT")
            db.execSQL("ALTER TABLE lend_borrow_transactions ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
        }
    }

/**
 * Migration from version 60 to 61.
 * Adds a currency column to lend_borrow_transactions. Existing rows are
 * backfilled from the linked wallet transaction's original currency so
 * amounts can be converted when the display currency changes.
 */
val MIGRATION_60_61 =
    object : Migration(60, 61) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lend_borrow_transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
            db.execSQL(
                """
                UPDATE lend_borrow_transactions
                SET currency = COALESCE(
                    (SELECT t.currency FROM transactions t WHERE t.id = lend_borrow_transactions.transaction_id),
                    'INR'
                )
                WHERE transaction_id IS NOT NULL
                """.trimIndent()
            )
        }
    }

/**
 * Migration from version 61 to 62.
 * Seeds the new "Borrowed" income category (mirror of the existing "Lent"
 * expense category) so existing installations expose it for lending/borrowing
 * entries created via the LENT/BORROWED transaction types.
 */
val MIGRATION_61_62 =
    object : Migration(61, 62) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO categories (
                    name, color, icon_res_id, icon_name, description, is_system, is_income, display_order,
                    default_name, default_color, default_icon_res_id, default_icon_name, default_description,
                    created_at, updated_at
                )
                VALUES (
                    'Borrowed', '#2196F3', 0, 'type_finance_deposit', 'Money borrowed from others', 1, 1, 29,
                    'Borrowed', '#2196F3', 0, 'type_finance_deposit', 'Money borrowed from others',
                    datetime('now'), datetime('now')
                )
                """.trimIndent()
            )
        }
    }

val MIGRATION_66_67 = AccountsMigration

/** Credit cards learn their statement closing and payment due days. */
/**
 * Drops what removed features left behind: smart rules, webhooks and merchant mappings;
 * the mandate number on subscriptions; the "manually added only" budget mode; and
 * sample data, which can no longer be removed from the app.
 */
val MIGRATION_68_69 =
    object : Migration(68, 69) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "rule_applications", "transaction_rules", "webhook_logs", "webhook_cursors",
                "webhook_profiles", "merchant_mappings"
            ).forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }

            db.execSQL("UPDATE `budgets` SET `track_type` = 'ALL_TRANSACTIONS' WHERE `track_type` = 'ADDED_ONLY'")

            db.execSQL("DELETE FROM `budget_category_limits` WHERE `budget_id` IN (SELECT `id` FROM `budgets` WHERE `is_sample` = 1)")
            listOf("budgets", "subscriptions", "cards", "account_balances", "transactions").forEach {
                db.execSQL("DELETE FROM `$it` WHERE `is_sample` = 1")
            }
            db.execSQL("DELETE FROM `account_currencies` WHERE `account_id` IN (SELECT `id` FROM `accounts` WHERE `is_sample` = 1)")
            db.execSQL("DELETE FROM `accounts` WHERE `is_sample` = 1")

            val columns = "`id`, `merchant_name`, `amount`, `next_payment_date`, `state`, `bank_name`, " +
                "`category`, `subcategory`, `sms_body`, `created_at`, `updated_at`, `currency`, " +
                "`billing_cycle`, `last_paid_date`, `is_sample`"
            db.execSQL(
                "CREATE TABLE `subscriptions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`merchant_name` TEXT NOT NULL, `amount` TEXT NOT NULL, `next_payment_date` TEXT, " +
                    "`state` TEXT NOT NULL, `bank_name` TEXT, `category` TEXT, `subcategory` TEXT, " +
                    "`sms_body` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL DEFAULT 'INR', `billing_cycle` TEXT, `last_paid_date` TEXT, " +
                    "`is_sample` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("INSERT INTO `subscriptions_new` ($columns) SELECT $columns FROM `subscriptions`")
            db.execSQL("DROP TABLE `subscriptions`")
            db.execSQL("ALTER TABLE `subscriptions_new` RENAME TO `subscriptions`")
        }
    }

val MIGRATION_67_68 =
    object : Migration(67, 68) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `statement_day` INTEGER")
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `due_day` INTEGER")
        }
    }

/** Transfers keep the amount that reached the target account, in its own currency. */
val MIGRATION_65_66 =
    object : Migration(65, 66) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `to_amount` TEXT")
        }
    }

/**
 * Drops the tables behind the removed on-device chat assistant and the removed
 * SMS / bank-notification ingestion. Nothing reads them any more.
 */
/** Quick-add templates for the Add Transaction screen. */
/** Index for the date-range queries behind every list, Home widget and chart. */
val MIGRATION_64_65 =
    object : Migration(64, 65) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_is_deleted_date_time` " +
                    "ON `transactions` (`is_deleted`, `date_time`)"
            )
        }
    }

val MIGRATION_63_64 =
    object : Migration(63, 64) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quick_templates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `merchant_name` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `subcategory` TEXT,
                    `transaction_type` TEXT NOT NULL,
                    `amount` TEXT,
                    `prefill_amount` INTEGER NOT NULL DEFAULT 0,
                    `bank_name` TEXT,
                    `account_last4` TEXT,
                    `currency` TEXT,
                    `notes` TEXT,
                    `sort_order` INTEGER NOT NULL DEFAULT 0,
                    `created_at` TEXT NOT NULL,
                    `updated_at` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

val MIGRATION_62_63 =
    object : Migration(62, 63) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS chat_messages")
            db.execSQL("DROP TABLE IF EXISTS chat_sessions")
            db.execSQL("DROP TABLE IF EXISTS unrecognized_sms")
            db.execSQL("DROP TABLE IF EXISTS bank_notifications")
        }
    }

/**
 * Migration from version 4 to 5.
 * - Removes sessionId column from chat_messages table
 * - Adds isSystemPrompt column to chat_messages table
 */
@DeleteColumn.Entries(DeleteColumn(tableName = "chat_messages", columnName = "sessionId"))
class Migration4To5 : AutoMigrationSpec

/**
 * Migration from version 7 to 8.
 * - Adds categories table with default categories
 */
class Migration7To8 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)

        // Insert default categories
        val categories =
            listOf(
                Triple("Food & Dining", "#FC8019", false),
                Triple("Groceries", "#5AC85A", false),
                Triple("Transportation", "#000000", false),
                Triple("Shopping", "#FF9900", false),
                Triple("Bills & Utilities", "#4CAF50", false),
                Triple("Entertainment", "#E50914", false),
                Triple("Healthcare", "#10847E", false),
                Triple("Investments", "#00D09C", false),
                Triple("Banking", "#004C8F", false),
                Triple("Personal Care", "#6A4C93", false),
                Triple("Education", "#673AB7", false),
                Triple("Mobile", "#2A3890", false),
                Triple("Fitness", "#FF3278", false),
                Triple("Insurance", "#0066CC", false),
                Triple("Travel", "#00BCD4", false),
                Triple("Salary", "#4CAF50", true),
                Triple("Income", "#4CAF50", true),
                Triple("Others", "#757575", false)
            )

        categories.forEachIndexed { index, (name, color, isIncome) ->
            db.execSQL(
                    """
                INSERT INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(),
                    arrayOf<Any>(name, color, if (isIncome) 1 else 0, index + 1)
            )
        }
    }
}

/**
 * Migration from version 10 to 11.
 * - Adds account_balances table for tracking account balance history
 * - Migrates existing balance data from transactions table
 */
class Migration10To11 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)

        // Migrate existing balance data from transactions table
        db.execSQL(
                """
            INSERT INTO account_balances (bank_name, account_last4, balance, timestamp, transaction_id, created_at)
            SELECT 
                bank_name,
                account_number,
                balance_after,
                date_time,
                id,
                created_at
            FROM transactions
            WHERE balance_after IS NOT NULL 
                AND bank_name IS NOT NULL 
                AND account_number IS NOT NULL
        """.trimIndent()
        )
    }
}

/**
 * Manual migration from version 29 to 30. Adds new fields to categories and subcategories tables
 * for enhanced functionality.
 */
/**
 * Manual migration 48 -> 49.
 *
 * Normalises the webhook_profiles table by extracting the previously-blob columns
 * `data_types` (CSV) and `headers_json` (JSON array) into proper child tables, and
 * drops the now-redundant per-profile `currency` column (use the app baseCurrency).
 * Also collapses any legacy webhook_logs.status values ("DELIVERED" -> "SUCCESS",
 * "ERROR"/"FAILED" -> "FAILURE") so the typed enum reads cleanly.
 */
val MIGRATION_48_49 =
    object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Create new child tables.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS webhook_profile_headers (
                    profile_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    PRIMARY KEY(profile_id, ordinal),
                    FOREIGN KEY(profile_id) REFERENCES webhook_profiles(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_webhook_profile_headers_profile_id ON webhook_profile_headers(profile_id)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS webhook_profile_data_types (
                    profile_id TEXT NOT NULL,
                    data_type TEXT NOT NULL,
                    PRIMARY KEY(profile_id, data_type),
                    FOREIGN KEY(profile_id) REFERENCES webhook_profiles(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_webhook_profile_data_types_profile_id ON webhook_profile_data_types(profile_id)")

            // 2) Copy data from the soon-to-be-dropped columns into the child tables.
            val profileCursor = db.query("SELECT id, data_types, headers_json FROM webhook_profiles")
            while (profileCursor.moveToNext()) {
                val profileId = profileCursor.getString(0)
                val dataTypesCsv = profileCursor.getString(1) ?: ""
                val headersJson = profileCursor.getString(2) ?: "[]"

                dataTypesCsv.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { dataType ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO webhook_profile_data_types (profile_id, data_type) VALUES (?, ?)",
                            arrayOf<Any>(profileId, dataType)
                        )
                    }

                runCatching {
                    val parsed = kotlinx.serialization.json.Json.parseToJsonElement(headersJson)
                    val array = (parsed as? kotlinx.serialization.json.JsonArray) ?: emptyList()
                    array.forEachIndexed { index, element ->
                        val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
                        val key = (obj["key"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val value = (obj["value"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        if (key.isNotEmpty()) {
                            db.execSQL(
                                "INSERT OR REPLACE INTO webhook_profile_headers (profile_id, ordinal, key, value) VALUES (?, ?, ?, ?)",
                                arrayOf<Any>(profileId, index, key, value)
                            )
                        }
                    }
                }
            }
            profileCursor.close()

            // 3) Recreate webhook_profiles without data_types / headers_json / currency.
            db.execSQL(
                """
                CREATE TABLE webhook_profiles_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    range_preset TEXT NOT NULL,
                    custom_start TEXT,
                    custom_end TEXT,
                    last_error TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    last_synced_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO webhook_profiles_new (
                    id, name, url, enabled, range_preset, custom_start, custom_end,
                    last_error, consecutive_failures, last_synced_at, created_at, updated_at
                ) SELECT
                    id, name, url, enabled, range_preset, custom_start, custom_end,
                    last_error, consecutive_failures, last_synced_at, created_at, updated_at
                FROM webhook_profiles
                """.trimIndent()
            )
            db.execSQL("DROP TABLE webhook_profiles")
            db.execSQL("ALTER TABLE webhook_profiles_new RENAME TO webhook_profiles")

            // 4) Normalise legacy log status values to the new SUCCESS/FAILURE pair.
            db.execSQL("UPDATE webhook_logs SET status = 'SUCCESS' WHERE status IN ('SUCCESS', 'DELIVERED', 'success', 'delivered')")
            db.execSQL("UPDATE webhook_logs SET status = 'FAILURE' WHERE status NOT IN ('SUCCESS', 'FAILURE')")
        }
    }

/**
 * Manual migration 49 -> 50.
 *
 * Splits the operational/status fields out of webhook_profiles into a sibling
 * webhook_profile_status table so editor saves no longer touch sync state and vice-versa.
 */
val MIGRATION_49_50 =
    object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Create the new status table.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS webhook_profile_status (
                    profile_id TEXT NOT NULL PRIMARY KEY,
                    last_error TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    last_synced_at TEXT,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY(profile_id) REFERENCES webhook_profiles(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // 2) Copy operational state from webhook_profiles into the new table.
            db.execSQL(
                """
                INSERT INTO webhook_profile_status (
                    profile_id, last_error, consecutive_failures, last_synced_at, updated_at
                ) SELECT
                    id, last_error, consecutive_failures, last_synced_at, updated_at
                FROM webhook_profiles
                """.trimIndent()
            )

            // 3) Recreate webhook_profiles without the operational columns.
            db.execSQL(
                """
                CREATE TABLE webhook_profiles_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    range_preset TEXT NOT NULL,
                    custom_start TEXT,
                    custom_end TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO webhook_profiles_new (
                    id, name, url, enabled, range_preset, custom_start, custom_end, created_at, updated_at
                ) SELECT
                    id, name, url, enabled, range_preset, custom_start, custom_end, created_at, updated_at
                FROM webhook_profiles
                """.trimIndent()
            )
            db.execSQL("DROP TABLE webhook_profiles")
            db.execSQL("ALTER TABLE webhook_profiles_new RENAME TO webhook_profiles")
        }
    }

/**
 * Manual migration 50 -> 51.
 *
 * Collapses the over-normalised webhook schema back to a single profile row that owns its
 * operational state and inlines the small data_types CSV / headers JSON blobs. Child tables
 * (webhook_profile_headers, webhook_profile_data_types, webhook_profile_status) are dropped.
 *
 * The pragmatic call: at this app's actual scale (single-digit profiles, single-digit child
 * rows per profile) the join-table cost outweighed the 1NF benefit.
 */
val MIGRATION_50_51 =
    object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            data class Aggregated(
                val dataTypesCsv: String,
                val headersJson: String,
                val lastError: String?,
                val consecutiveFailures: Int,
                val lastSyncedAt: String?
            )
            val perProfile = mutableMapOf<String, Aggregated>()
            val ids = mutableListOf<String>()
            db.query("SELECT id FROM webhook_profiles").use { c ->
                while (c.moveToNext()) ids += c.getString(0)
            }
            ids.forEach { id ->
                val dataTypes = mutableListOf<String>()
                db.query(
                    "SELECT data_type FROM webhook_profile_data_types WHERE profile_id = ?",
                    arrayOf<Any>(id)
                ).use { c -> while (c.moveToNext()) dataTypes += c.getString(0) }

                val headerPairs = mutableListOf<Pair<String, String>>()
                db.query(
                    "SELECT key, value FROM webhook_profile_headers WHERE profile_id = ? ORDER BY ordinal ASC",
                    arrayOf<Any>(id)
                ).use { c ->
                    while (c.moveToNext()) headerPairs += c.getString(0) to c.getString(1)
                }
                val headersJson = if (headerPairs.isEmpty()) "[]" else {
                    headerPairs.joinToString(prefix = "[", postfix = "]") { (k, v) ->
                        """{"key":${jsonString(k)},"value":${jsonString(v)}}"""
                    }
                }

                var lastError: String? = null
                var consecutiveFailures = 0
                var lastSyncedAt: String? = null
                db.query(
                    "SELECT last_error, consecutive_failures, last_synced_at FROM webhook_profile_status WHERE profile_id = ? LIMIT 1",
                    arrayOf<Any>(id)
                ).use { c ->
                    if (c.moveToNext()) {
                        lastError = if (c.isNull(0)) null else c.getString(0)
                        consecutiveFailures = c.getInt(1)
                        lastSyncedAt = if (c.isNull(2)) null else c.getString(2)
                    }
                }

                perProfile[id] = Aggregated(
                    dataTypesCsv = dataTypes.joinToString(","),
                    headersJson = headersJson,
                    lastError = lastError,
                    consecutiveFailures = consecutiveFailures,
                    lastSyncedAt = lastSyncedAt
                )
            }

            db.execSQL(
                """
                CREATE TABLE webhook_profiles_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    url TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    range_preset TEXT NOT NULL,
                    custom_start TEXT,
                    custom_end TEXT,
                    data_types TEXT NOT NULL DEFAULT '',
                    headers_json TEXT NOT NULL DEFAULT '[]',
                    last_error TEXT,
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    last_synced_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.query(
                "SELECT id, name, url, enabled, range_preset, custom_start, custom_end, created_at, updated_at FROM webhook_profiles"
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val agg = perProfile[id] ?: Aggregated("", "[]", null, 0, null)
                    db.execSQL(
                        """
                        INSERT INTO webhook_profiles_new (
                            id, name, url, enabled, range_preset, custom_start, custom_end,
                            data_types, headers_json, last_error, consecutive_failures, last_synced_at,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            id, c.getString(1), c.getString(2), c.getInt(3), c.getString(4),
                            if (c.isNull(5)) null else c.getString(5),
                            if (c.isNull(6)) null else c.getString(6),
                            agg.dataTypesCsv, agg.headersJson,
                            agg.lastError, agg.consecutiveFailures, agg.lastSyncedAt,
                            c.getString(7), c.getString(8)
                        )
                    )
                }
            }

            db.execSQL("DROP TABLE IF EXISTS webhook_profile_headers")
            db.execSQL("DROP TABLE IF EXISTS webhook_profile_data_types")
            db.execSQL("DROP TABLE IF EXISTS webhook_profile_status")
            db.execSQL("DROP TABLE webhook_profiles")
            db.execSQL("ALTER TABLE webhook_profiles_new RENAME TO webhook_profiles")
        }

        private fun jsonString(value: String): String {
            val sb = StringBuilder("\"")
            value.forEach { ch ->
                when {
                    ch == '\\' -> sb.append("\\\\")
                    ch == '"' -> sb.append("\\\"")
                    ch == '\n' -> sb.append("\\n")
                    ch == '\r' -> sb.append("\\r")
                    ch == '\t' -> sb.append("\\t")
                    ch.code < 0x20 -> sb.append("\\u%04x".format(ch.code))
                    else -> sb.append(ch)
                }
            }
            sb.append("\"")
            return sb.toString()
        }
    }

val MIGRATION_51_52 =
    object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exchange_rates ADD COLUMN is_custom INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_52_53 =
    object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN reference TEXT")
        }
    }

val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bank_notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                package_name TEXT NOT NULL,
                notification_id INTEGER NOT NULL,
                sender_alias TEXT NOT NULL,
                message_body TEXT NOT NULL,
                status TEXT NOT NULL,
                transaction_id INTEGER,
                error_message TEXT,
                received_at TEXT NOT NULL,
                processed_at TEXT
            )
        """)
        // Only create the composite unique index — the status index is intentionally
        // omitted as it is not declared on BankNotificationEntity.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_notifications_package_name_notification_id ON bank_notifications (package_name, notification_id)")
    }
}

val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns to categories table
            db.execSQL("ALTER TABLE categories ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN default_name TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN default_color TEXT")
            db.execSQL("ALTER TABLE categories ADD COLUMN default_icon_res_id INTEGER")
            db.execSQL("ALTER TABLE categories ADD COLUMN default_description TEXT")

            // Add new columns to subcategories table
            db.execSQL(
                "ALTER TABLE subcategories ADD COLUMN icon_res_id INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE subcategories ADD COLUMN color TEXT NOT NULL DEFAULT '#757575'"
            )
            db.execSQL(
                "ALTER TABLE subcategories ADD COLUMN is_system INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE subcategories ADD COLUMN default_name TEXT")
            db.execSQL("ALTER TABLE subcategories ADD COLUMN default_icon_res_id INTEGER")
            db.execSQL("ALTER TABLE subcategories ADD COLUMN default_color TEXT")
        }
    }

/** Migration from version 29 to 30. AutoMigrationSpec to handle any post-migration data updates. */
class Migration29To30 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        // Post-migration updates if needed
        // Default values are already set in the entity definitions
    }
}

/** Migration from version 31 to 32. Migrates 'Others' category to 'Miscellaneous'. */
class Migration31To32 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)

        // Update transactions categorized as 'Others' to 'Miscellaneous'
        db.execSQL("UPDATE transactions SET category = 'Miscellaneous' WHERE category = 'Others'")

        // Update subscriptions categorized as 'Others' to 'Miscellaneous'
        db.execSQL("UPDATE subscriptions SET category = 'Miscellaneous' WHERE category = 'Others'")

        // Update merchant mappings from 'Others' to 'Miscellaneous'
        db.execSQL("UPDATE merchant_mappings SET category = 'Miscellaneous' WHERE category = 'Others'")

        // Delete the 'Others' category from categories table
        db.execSQL("DELETE FROM categories WHERE name = 'Others'")
    }
}
/** Migration from version 34 to 35. Adds is_wallet column to account_balances table. */
class Migration34To35 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        // No post-migration needed as default value is handled
    }
}
/** Migration from version 40 to 41. Unifies old category names with new ones. */
class Migration40To41 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)

        val oldToNewMap = mapOf(
            "Food & Dining" to "Food & Drinks",
            "Transportation" to "Transport",
            "Bills & Utilities" to "Bill",
            "Healthcare" to "Medical",
            "Personal Care" to "Personal",
            "Investments" to "Investment",
            "Mobile" to "Bill",
            "Banking" to "Miscellaneous",
            "Education" to "Miscellaneous"
        )

        oldToNewMap.forEach { (old, new) ->
            // Update transactions
            db.execSQL("UPDATE transactions SET category = ? WHERE category = ?", arrayOf(new, old))

            // Update subscriptions
            db.execSQL("UPDATE subscriptions SET category = ? WHERE category = ?", arrayOf(new, old))

            // Update merchant mappings
            db.execSQL("UPDATE merchant_mappings SET category = ? WHERE category = ?", arrayOf(new, old))

            // Update rules (actions) - this is stored as JSON in the database,
            // but we can do a simple string replace for the value if it's stored as plain text in the JSON
            // TransactionRule actions are serialized. We might need a more careful approach here
            // if we want to be 100% sure, but simple string replacement in the 'actions' column
            // usually works for SQLite JSON if the structure is simple.
            // However, to be safe, let's just do it for categories.
            db.execSQL("UPDATE transaction_rules SET actions = REPLACE(actions, ?, ?) WHERE actions LIKE ?",
                arrayOf("\"value\":\"$old\"", "\"value\":\"$new\"", "%\"value\":\"$old\"%"))
        }

        // Delete old system categories from categories table
        oldToNewMap.keys.forEach { oldCategory ->
            db.execSQL("DELETE FROM categories WHERE name = ?", arrayOf(oldCategory))
        }
    }
}

/**
 * Migration from version 43 to 44. Adds 'Income' default category and its subcategories.
 * Icon names are not written here: that column only arrives in 46, whose migration fills it.
 */
class Migration43To44 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        // Insert Income category
        db.execSQL(
            """
            INSERT OR IGNORE INTO categories (
                name, color, icon_res_id, description, is_system, is_income, display_order,
                default_name, default_color, default_icon_res_id, default_description,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, 1, 1, 0, ?, ?, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(),
            arrayOf<Any>(
                "Income", "#4CAF50", io.github.hootkit_dev.hootbook.R.drawable.type_finance_money_bag, "Generic income",
                "Income", "#4CAF50", io.github.hootkit_dev.hootbook.R.drawable.type_finance_money_bag, "Generic income"
            )
        )
        
        // Find the inserted or existing Income category ID
        val cursor = db.query("SELECT id FROM categories WHERE name = 'Income'")
        var incomeCategoryId: Long = -1
        if (cursor.moveToFirst()) {
            incomeCategoryId = cursor.getLong(0)
        }
        cursor.close()
        
        if (incomeCategoryId != -1L) {
            val incomeSubcategories = listOf(
                Triple("Freelance", io.github.hootkit_dev.hootbook.R.drawable.type_stationary_clipboard, "#4CAF50"),
                Triple("Business", io.github.hootkit_dev.hootbook.R.drawable.type_finance_classical_building, "#8BC34A"),
                Triple("Bonus", io.github.hootkit_dev.hootbook.R.drawable.type_stationary_wrapped_gift, "#FFEB3B"),
                Triple("Gift", io.github.hootkit_dev.hootbook.R.drawable.type_stationary_wrapped_gift, "#FF9800"),
                Triple("Interest", io.github.hootkit_dev.hootbook.R.drawable.type_finance_chart_decreasing, "#8BC34A"),
                Triple("Refund", io.github.hootkit_dev.hootbook.R.drawable.type_finance_currency_exchange, "#03A9F4"),
                Triple("Other", io.github.hootkit_dev.hootbook.R.drawable.type_stationary_clipboard, "#9E9E9E"),
            )
            
            incomeSubcategories.forEach { (name, iconResId, color) ->
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO subcategories (
                        category_id, name, icon_res_id, color, is_system,
                        default_name, default_color, default_icon_res_id
                    )
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        incomeCategoryId, name, iconResId, color,
                        name, color, iconResId
                    )
                )
            }
        }
    }
}

/** 
 * Migration from version 44 to 45. 
 * Migrates 'Salary' category to 'Income' category with 'Salary' subcategory.
 * Its icon name is filled by the 45 -> 46 migration, which adds that column.
 */
class Migration44To45 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        // Find Income Category ID
        val cursorIncome = db.query("SELECT id FROM categories WHERE name = 'Income'")
        var incomeCategoryId: Long = -1
        if (cursorIncome.moveToFirst()) {
            incomeCategoryId = cursorIncome.getLong(0)
        }
        cursorIncome.close()

        if (incomeCategoryId != -1L) {
            // Ensure Salary subcategory exists under Income
            db.execSQL(
                """
                INSERT OR IGNORE INTO subcategories (
                    category_id, name, icon_res_id, color, is_system,
                    default_name, default_color, default_icon_res_id
                )
                VALUES (?, 'Salary', ?, '#8BC34A', 1, 'Salary', '#8BC34A', ?)
                """.trimIndent(),
                arrayOf<Any>(
                    incomeCategoryId, 
                    io.github.hootkit_dev.hootbook.R.drawable.type_finance_coin,
                    io.github.hootkit_dev.hootbook.R.drawable.type_finance_coin
                )
            )

            // Update transactions categorized as 'Salary' to 'Income' and subcategory 'Salary'
            val cursorHasSubcategory = db.query("PRAGMA table_info(transactions)")
            var hasSubcat = false
            while(cursorHasSubcategory.moveToNext()){
                if(cursorHasSubcategory.getString(1) == "subcategory") {
                    hasSubcat = true
                    break
                }
            }
            cursorHasSubcategory.close()
            
            if(hasSubcat) {
                db.execSQL("UPDATE transactions SET category = 'Income', subcategory = 'Salary' WHERE category = 'Salary'")
            } else {
                db.execSQL("UPDATE transactions SET category = 'Income' WHERE category = 'Salary'")
            }

            // Update subscriptions categorized as 'Salary' to 'Income'
            db.execSQL("UPDATE subscriptions SET category = 'Income' WHERE category = 'Salary'")

            // Update merchant mappings from 'Salary' to 'Income'
            db.execSQL("UPDATE merchant_mappings SET category = 'Income' WHERE category = 'Salary'")

            // Update rules
            db.execSQL("UPDATE transaction_rules SET actions = REPLACE(actions, '\"value\":\"Salary\"', '\"value\":\"Income\"') WHERE actions LIKE '%\"value\":\"Salary\"%'")
        }

        // Delete the old 'Salary' category from categories table
        db.execSQL("DELETE FROM categories WHERE name = 'Salary'")
    }
}

/** 
 * Migration from version 45 to 46. 
 * Populates icon_name for existing categories, subcategories and account balances.
 * This preserves stability across app updates.
 */
class Migration45To46 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        // Categories (System ones)
        val categoryMappings = mapOf(
            "Food & Drinks" to "type_food_stuffed_flatbread",
            "Transport" to "type_travel_transport_airplane",
            "Shopping" to "type_shopping_shopping_bags",
            "Groceries" to "type_groceries_bread",
            "Home" to "type_event_and_place_house",
            "Entertainment" to "type_snack_popcorn",
            "Events" to "type_event_and_place_party_popper",
            "Travel" to "type_travel_transport_luggage",
            "Medical" to "type_health_pill",
            "Personal" to "type_tool_electronic_scissors",
            "Fitness" to "type_sports_baseball",
            "Services" to "type_tool_electronic_high_voltage",
            "Bill" to "type_travel_transport_admission_tickets",
            "Subscription" to "type_tool_electronic_clapper_board",
            "EMI" to "type_travel_transport_automobile",
            "Credit Bill" to "type_stationary_card_file_box",
            "Investment" to "type_flower_and_tree_herb",
            "Support" to "type_health_stethoscope",
            "Insurance" to "type_health_mending_heart",
            "Tax" to "type_finance_chart_decreasing",
            "Top-up" to "type_finance_money_bag",
            "Children" to "type_event_and_place_houses",
            "Pet Care" to "type_animal_dog_face",
            "Business" to "type_finance_classical_building",
            "Miscellaneous" to "type_stationary_clipboard",
            "Self Transfer" to "type_finance_bank",
            "Savings" to "type_sports_bullseye",
            "Gift" to "type_stationary_wrapped_gift",
             "Lent" to "type_finance_money_with_wings",
             "Borrowed" to "type_finance_deposit",
             "Donation" to "type_health_drop_of_blood",
            "Hidden Charges" to "type_animal_goblin",
            "Cash Withdrawal" to "type_finance_dollar_banknote",
            "Income" to "type_finance_money_bag"
        )
        
        categoryMappings.forEach { (name, iconName) ->
            db.execSQL("UPDATE categories SET icon_name = ?, default_icon_name = ? WHERE name = ?", arrayOf(iconName, iconName, name))
        }

        // Subcategories (System ones)
        // partial list of the most common ones
        val subcategoryMappings = mapOf(
            "Eating out" to "type_food_dining",
            "Take Away" to "type_food_takeout",
            "Tea & Coffee" to "type_beverages_tea",
            "Fast Food" to "type_food_hamburger",
            "Snacks" to "type_snack_cookie",
            "Swiggy" to "ic_brand_swiggy",
            "Zomato" to "ic_brand_zomato",
            "Sweets" to "type_sweet_cupcake",
            "Uber" to "ic_brand_uber",
            "Rapido" to "ic_brand_rapido",
            "Auto" to "type_travel_transport_auto_rickshaw",
            "Cab" to "type_travel_transport_taxi",
            "Train" to "type_travel_transport_high_speed_train",
            "Metro" to "type_travel_transport_metro",
            "Bus" to "type_travel_transport_bus",
            "Bike" to "type_travel_transport_motorcycle",
            "Fuel" to "type_travel_transport_fuel_pump",
            "Clothes" to "type_shopping_necktie",
            "Footwear" to "type_shopping_mans_shoe",
            "Electronics" to "type_tool_electronic_desktop_computer",
            "Vegetables" to "type_vegetable_broccoli",
            "Fruits" to "type_fruit_mango",
            "Dairy" to "type_groceries_glass_of_milk",
            "Salary" to "type_finance_coin",
            "Freelance" to "type_stationary_clipboard",
            "Business" to "type_finance_classical_building",
            "Bonus" to "type_stationary_wrapped_gift",
            "Gift" to "type_stationary_wrapped_gift",
            "Interest" to "type_finance_chart_decreasing",
            "Refund" to "type_finance_currency_exchange"
        )
        
        subcategoryMappings.forEach { (name, iconName) ->
            db.execSQL("UPDATE subcategories SET icon_name = ?, default_icon_name = ? WHERE name = ? AND is_system = 1", arrayOf(iconName, iconName, name))
        }
    }
}

/** 
 * Migration from version 46 to 47. 
 * Populates icon_name for all the remaining default subcategories that were missed in Migration45To46.
 */
class Migration46To47 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        super.onPostMigrate(db)
        
        val subcategoryMappings = mapOf(
            "Eating out" to "type_food_dining",
            "Take Away" to "type_food_takeout",
            "Tea & Coffee" to "type_beverages_tea",
            "Fast Food" to "type_food_hamburger",
            "Snacks" to "type_snack_cookie",
            "Swiggy" to "ic_brand_swiggy",
            "Zomato" to "ic_brand_zomato",
            "Sweets" to "type_sweet_cupcake",
            "Liquor" to "type_beverages_beer",
            "Beverages" to "type_beverages_bubble_tea",
            "Date" to "type_food_sushi",
            "Pizza" to "type_food_pizza",
            "Tiffin" to "type_food_bento_box",
            "Uber" to "ic_brand_uber",
            "Rapido" to "ic_brand_rapido",
            "Auto" to "type_travel_transport_auto_rickshaw",
            "Cab" to "type_travel_transport_taxi",
            "Train" to "type_travel_transport_high_speed_train",
            "Metro" to "type_travel_transport_metro",
            "Bus" to "type_travel_transport_bus",
            "Bike" to "type_travel_transport_motorcycle",
            "Fuel" to "type_travel_transport_fuel_pump",
            "Ev Charge" to "type_tool_electronic_high_voltage",
            "Flights" to "type_travel_transport_airplane",
            "Parking" to "type_travel_transport_ticket",
            "FASTag" to "type_travel_transport_ticket",
            "Tolls" to "type_travel_transport_ticket",
            "Lounge" to "type_travel_transport_luggage",
            "Fine" to "type_travel_transport_ticket",
            "Clothes" to "type_shopping_necktie",
            "Footwear" to "type_shopping_mans_shoe",
            "Electronics" to "type_tool_electronic_mobile_phone",
            "Festival" to "type_event_and_place_firecracker",
            "Video games" to "type_tool_electronic_video_game",
            "Books" to "type_stationary_blue_book",
            "Plants" to "type_flower_and_tree_potted_plant",
            "Jewellery" to "type_shopping_gem_stone",
            "Furniture" to "type_event_and_place_couch_and_lamp",
            "Appliances" to "type_tool_electronic_television",
            "Utensils" to "type_tool_electronic_hammer_and_wrench",
            "Vehicle" to "type_travel_transport_automobile",
            "Cosmetics" to "type_shopping_nail_polish",
            "Toys" to "type_stationary_toys",
            "Stationery" to "type_stationary_artist_palette",
            "Glasses" to "type_shopping_glasses",
            "Devotional" to "type_event_and_place_diya_lamp",
            "Staples" to "type_vegetable_beans",
            "Vegetables" to "type_vegetable_broccoli",
            "Fruits" to "type_fruit_mango",
            "Meat" to "type_groceries_cut_of_meat",
            "Eggs" to "type_groceries_egg",
            "Bakery" to "type_groceries_baguette_bread",
            "Dairy" to "type_groceries_glass_of_milk",
            "Zepto" to "ic_brand_zepto",
            "Essentials" to "type_groceries_basket",
            "Toiletries" to "type_groceries_soap",
            "Decor" to "type_flower_and_tree_hibiscus",
            "Cleaning" to "type_flower_and_tree_leaf_fluttering_in_wind",
            "Upkeep" to "type_groceries_sponge",
            "Painting" to "type_stationary_artist_palette",
            "Renovation" to "type_tool_electronic_hammer_and_wrench",
            "Pest-control" to "type_animal_lady_beetle",
            "Construction" to "type_tool_electronic_hammer",
            "Movies" to "type_snack_french_fries",
            "Shows" to "type_tool_electronic_clapper_board",
            "Bowling" to "type_sports_bowling",
            "Tickets" to "type_travel_transport_admission_tickets",
            "Party" to "type_event_and_place_party_popper",
            "Birthday" to "type_sweet_birthday_cake",
            "Spiritual" to "type_event_and_place_diya_lamp",
            "Wedding" to "type_event_and_place_wedding",
            "Activities" to "type_sports_trophy",
            "Camping" to "type_event_and_place_camping",
            "Hotel" to "type_event_and_place_hotel",
            "Commute" to "type_event_and_place_couch_and_lamp",
            "Visa fees" to "type_travel_transport_ticket",
            "Hostel" to "type_finance_classical_building",
            "Airbnb" to "ic_brand_airbnb",
            "Oyo" to "ic_brand_oyo",
            "Medicines" to "type_health_pill",
            "Hospital" to "type_health_hospital",
            "Clinic" to "type_health_stethoscope",
            "Dentist" to "type_health_tooth",
            "Lab test" to "type_shopping_lab_coat",
            "Hygiene" to "type_health_adhesive_bandage",
            "Self-care" to "type_groceries_lotion_bottle",
            "Grooming" to "type_tool_electronic_scissors",
            "Hobbies" to "type_sports_basketball",
            "Vices" to "type_event_and_place_firecracker",
            "Therapy" to "type_health_mending_heart",
            "Gym" to "type_sports_flexed_biceps_light",
            "Badminton" to "type_sports_badminton",
            "Football" to "type_sports_soccer_ball",
            "Cricket" to "type_sports_cricket_game",
            "Classes" to "type_stationary_books",
            "Equipment" to "type_tool_electronic_screwdriver",
            "Nutrition" to "type_vegetable_pea_pod",
            "Laundry" to "type_shopping_necktie",
            "Tailor" to "type_shopping_scarf",
            "Courier" to "type_travel_transport_package",
            "Carpenter" to "type_tool_electronic_carpentry_saw",
            "Plumber" to "type_tool_electronic_toolbox",
            "Mechanic" to "type_tool_electronic_hammer_and_wrench",
            "Photographer" to "type_tool_electronic_camera_with_flash",
            "Driver" to "type_travel_transport_oncoming_taxi",
            "Vehicle Wash" to "type_travel_transport_automobile",
            "Electrician" to "type_tool_electronic_high_voltage",
            "Xerox" to "type_stationary_card_index",
            "Legal" to "type_stationary_reminder_ribbon",
            "Advisor" to "type_stationary_bookmark",
            "Repair" to "type_tool_electronic_hammer_and_pick",
            "Logistics" to "type_travel_transport_delivery_truck",
            "Phone" to "type_tool_electronic_mobile_phone",
            "Rent" to "type_event_and_place_house",
            "Water" to "type_beverages_sake",
            "Electricity" to "type_tool_electronic_high_voltage",
            "Gas" to "type_travel_transport_fuel_pump",
            "Internet" to "type_travel_transport_globe_showing_asia_australia",
            "House Help" to "type_flower_and_tree_potted_plant",
            "Education" to "type_stationary_writing_hand_light",
            "DTH" to "type_tool_electronic_video_camera",
            "Cook" to "type_food_curry_rice",
            "Maintenance" to "type_tool_electronic_hammer_and_wrench",
            "Software" to "type_tool_electronic_software",
            "News" to "type_stationary_newspaper",
            "Netflix" to "ic_brand_netflix",
            "Prime" to "ic_brand_amazon_prime",
            "Youtube" to "ic_brand_youtube",
            "Youtube Music" to "ic_brand_youtube_music",
            "Spotify" to "ic_brand_spotify",
            "Google" to "ic_brand_google",
            "Learning" to "type_stationary_writing_hand_light",
            "Apple Tv" to "ic_brand_apple_tv",
            "Apple Music" to "ic_brand_apple_music",
            "Bumble" to "ic_brand_bumble",
            "JioCinema" to "ic_brand_jiocinema",
            "Google Play" to "ic_brand_google_play",
            "Xbox" to "ic_brand_xbox",
            "PlayStation" to "ic_brand_playstation",
            "Disney Plus" to "ic_brand_disney_plus",
            "Zee5" to "ic_brand_zee5",
            "ChatGPT" to "ic_brand_chatgpt",
            "Claude" to "ic_brand_claude",
            "Grok" to "ic_brand_grok",
            "House" to "type_event_and_place_house",
            "Credit Card" to "type_finance_credit_card",
            "Simpl" to "ic_brand_simpl",
            "Slice" to "ic_brand_slice",
            "lazypay" to "ic_brand_lazypay",
            "Amazon Pay" to "ic_brand_amazon",
            "Mutual Funds" to "type_flower_and_tree_herb",
            "Stocks" to "type_finance_chart_increasing",
            "IPO" to "type_finance_bar_chart",
            "PPF" to "type_finance_dollar_banknote",
            "Fixed Deposit" to "type_finance_deposit",
            "Recurring Deposit" to "type_finance_tip",
            "Assets" to "type_finance_classical_building",
            "Crypto" to "type_finance_crypto",
            "Gold" to "type_finance_coin",
            "Parents" to "type_human_parents",
            "Spouse" to "type_human_woman",
            "Mom" to "type_human_old_woman",
            "Dad" to "type_human_older_person",
            "Pocket Money" to "type_finance_money_bag",
            "Health" to "type_health_drop_of_blood",
            "Life" to "type_health_mending_heart",
            "Income Tax" to "type_finance_chart_increasing",
            "GST" to "type_finance_tax_due",
            "Property Tax" to "type_finance_classical_building",
            "UPI Lite" to "type_finance_bank",
            "Paytm" to "ic_brand_paytm",
            "Amazon" to "ic_brand_amazon",
            "PhonePe" to "ic_brand_phonepe",
            "Google pay" to "ic_brand_google_pay",
            "Necessities" to "type_stationary_pencil",
            "Medical" to "type_health_pill",
            "Care" to "type_health_adhesive_bandage",
            "Tuition Fee" to "type_finance_money_bag",
            "Classes Fee" to "type_stationary_books",
            "School Fee" to "type_stationary_open_book",
            "College Fee" to "type_finance_classical_building",
            "Food" to "type_vegetable_beans",
            "Vet" to "type_health_vet",
            "Salary" to "type_finance_coin",
            "Inventory" to "type_travel_transport_inventory",
            "Marketing" to "type_finance_bar_chart",
            "Tax" to "type_finance_tax_due",
            "Insurance" to "type_finance_insurance",
            "Service" to "type_tool_electronic_light_bulb",
            "Tip" to "type_finance_tip",
            "Verification" to "type_tool_electronic_magnifying_glass_tilted_left",
            "Forex" to "type_finance_currency_exchange",
            "Deposit" to "type_finance_deposit",
            "Gift Cards" to "type_stationary_gift_card",
            "Freelance" to "type_stationary_clipboard",
            "Business" to "type_finance_classical_building",
            "Bonus" to "type_stationary_wrapped_gift",
            "Gift" to "type_stationary_wrapped_gift",
            "Interest" to "type_finance_chart_decreasing",
            "Refund" to "type_finance_currency_exchange",
            "Other" to "type_stationary_clipboard"
        )
        
        subcategoryMappings.forEach { (name, iconName) ->
            db.execSQL("UPDATE subcategories SET icon_name = ?, default_icon_name = ? WHERE name = ? AND is_system = 1", arrayOf(iconName, iconName, name))
        }
    }
}

/**
 * 69 -> 70: SMS import is gone, and so are the columns it filled. A subscription's notes were
 * kept in its sms_body column; that column is now called notes.
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "transactions", columnName = "sms_body"),
    DeleteColumn(tableName = "transactions", columnName = "sms_sender"),
    DeleteColumn(tableName = "cards", columnName = "last_balance_source"),
    DeleteColumn(tableName = "account_balances", columnName = "sms_source")
)
@RenameColumn(tableName = "subscriptions", fromColumnName = "sms_body", toColumnName = "notes")
class Migration69To70 : AutoMigrationSpec

/**
 * 70 -> 71: groundwork for multi-device sync (docs/sync.md). Every synced table gets a sync id
 * (filled in for every row here) and the time of its last local change; the outbox, its switch
 * and the triggers that fill the outbox are added. Existing rows are not queued: the first sync
 * uploads everything anyway.
 */
val MIGRATION_70_71 =
    object : Migration(70, 71) {
        // The synced tables of version 71, fixed here whatever later versions add
        private val tables = listOf(
            "accounts", "account_currencies", "account_balances", "transactions", "categories",
            "subcategories", "cards", "budgets", "budget_category_limits", "subscriptions",
            "lend_borrow_persons", "lend_borrow_transactions", "quick_templates",
        )

        override fun migrate(db: SupportSQLiteDatabase) {
            tables.forEach { table ->
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `$table` SET sync_id = ${SyncTriggers.NEW_ID_SQL}, sync_updated_at = ${SyncTriggers.NOW_SQL}"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_sync_id` ON `$table` (`sync_id`)")
            }
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_outbox` (`table_name` TEXT NOT NULL, `sync_id` TEXT NOT NULL, " +
                    "`op` TEXT NOT NULL, `queued_at` INTEGER NOT NULL, PRIMARY KEY(`table_name`, `sync_id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_control` (`id` INTEGER NOT NULL, `applying_remote` INTEGER " +
                    "NOT NULL DEFAULT 0, `capturing` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
            )
            SyncTriggers.install(db, tables)
        }
    }

/**
 * Phase 2 of sync (docs/sync.md): `sync_outbox.replaced_by` for the DELETE that resolves a
 * duplicate, `sync_inbox` for remote records that wait for their parent, and fixed sync ids for
 * the built-in categories and subcategories every device seeds.
 */
val MIGRATION_71_72 =
    object : Migration(71, 72) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `replaced_by` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_inbox` (`doc_id` TEXT NOT NULL, `table_name` TEXT NOT NULL, " +
                    "`sync_id` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `payload` TEXT, `device_id` TEXT NOT NULL, " +
                    "`version` INTEGER NOT NULL, `replaced_by` TEXT, `updated_seconds` INTEGER NOT NULL, " +
                    "`updated_nanos` INTEGER NOT NULL, `waiting_for` TEXT NOT NULL, PRIMARY KEY(`doc_id`))"
            )
            SeededSyncIds.rekey(db)
        }
    }
