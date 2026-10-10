package io.github.hootkit_dev.hootbook.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 66 → 67: accounts become rows of their own, holding one or more currencies.
 *
 * Every (bank_name, account_last4) pair becomes an account with the look of its latest balance
 * row. Its one currency is the one it was last set up or edited with (its latest MANUAL row),
 * falling back to its latest row: a bug used to stamp a transfer's currency on the receiving
 * account, and those stray rows must not become currencies that can't be removed. All of an
 * account's balance rows join that currency, and transactions learn their account ids; a
 * transfer's target comes from its own balance rows, not from the last 4 it stored.
 */
object AccountsMigration : Migration(66, 67) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `last4` TEXT NOT NULL, `main_currency` TEXT NOT NULL, " +
                "`is_credit_card` INTEGER NOT NULL DEFAULT 0, `is_wallet` INTEGER NOT NULL DEFAULT 0, " +
                "`credit_limit` TEXT, `icon_res_id` INTEGER NOT NULL DEFAULT 0, `icon_name` TEXT NOT NULL DEFAULT '', " +
                "`color` TEXT NOT NULL DEFAULT '#33B5E5', `is_sample` INTEGER NOT NULL DEFAULT 0, `created_at` TEXT NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name_last4` ON `accounts` (`name`, `last4`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_currencies` (`account_id` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`credit_limit` TEXT, `created_at` TEXT NOT NULL, PRIMARY KEY(`account_id`, `currency`), " +
                "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        // One account per pair, looking like its latest row
        db.execSQL(
            """
            INSERT INTO accounts (name, last4, main_currency, is_credit_card, is_wallet, credit_limit,
                icon_res_id, icon_name, color, is_sample, created_at)
            SELECT ab.bank_name, ab.account_last4,
                COALESCE(
                    (SELECT m.currency FROM account_balances m
                        WHERE m.bank_name = ab.bank_name AND m.account_last4 = ab.account_last4 AND m.source_type = 'MANUAL'
                        ORDER BY m.timestamp DESC, m.id DESC LIMIT 1),
                    ab.currency),
                ab.is_credit_card, ab.is_wallet, ab.credit_limit, ab.icon_res_id, ab.icon_name, ab.color, ab.is_sample,
                (SELECT MIN(f.created_at) FROM account_balances f
                    WHERE f.bank_name = ab.bank_name AND f.account_last4 = ab.account_last4)
            FROM account_balances ab
            WHERE ab.id = (SELECT l.id FROM account_balances l
                WHERE l.bank_name = ab.bank_name AND l.account_last4 = ab.account_last4
                ORDER BY l.timestamp DESC, l.id DESC LIMIT 1)
            """.trimIndent()
        )
        db.execSQL(
            "INSERT OR IGNORE INTO account_currencies (account_id, currency, credit_limit, created_at) " +
                "SELECT id, main_currency, NULL, created_at FROM accounts"
        )

        // Balance rows belong to their account, in its currency
        db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `account_id` INTEGER")
        db.execSQL("DROP INDEX IF EXISTS `index_account_balances_bank_name_account_last4_timestamp`")
        db.execSQL(
            """
            UPDATE account_balances SET
                account_id = (SELECT a.id FROM accounts a WHERE a.name = account_balances.bank_name AND a.last4 = account_balances.account_last4),
                currency = (SELECT a.main_currency FROM accounts a WHERE a.name = account_balances.bank_name AND a.last4 = account_balances.account_last4)
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_account_balances_bank_name_account_last4_currency_timestamp` " +
                "ON `account_balances` (`bank_name`, `account_last4`, `currency`, `timestamp`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_account_balances_account_id_currency_timestamp` " +
                "ON `account_balances` (`account_id`, `currency`, `timestamp`)"
        )

        // Transactions learn their accounts
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `account_id` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `to_account_id` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `to_currency` TEXT")
        db.execSQL(
            "UPDATE transactions SET account_id = (SELECT a.id FROM accounts a " +
                "WHERE a.name = transactions.bank_name AND a.last4 = transactions.account_number)"
        )
        db.execSQL(
            """
            UPDATE transactions SET to_account_id = (
                SELECT ab.account_id FROM account_balances ab
                WHERE ab.transaction_id = transactions.id
                AND NOT (ab.bank_name = transactions.bank_name AND ab.account_last4 = transactions.account_number)
                ORDER BY ab.id LIMIT 1)
            WHERE transaction_type = 'TRANSFER'
            """.trimIndent()
        )
        db.execSQL(
            "UPDATE transactions SET to_currency = (SELECT a.main_currency FROM accounts a WHERE a.id = transactions.to_account_id) " +
                "WHERE to_account_id IS NOT NULL"
        )
    }
}
