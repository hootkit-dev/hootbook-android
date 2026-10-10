package io.github.hootkit_dev.hootbook.data.sync

/**
 * What a synced record carries, column by column: the payload of the wire protocol
 * (docs/sync.md, "Wire protocol"). The payload is a JSON object keyed by column name with every
 * column listed here except [Kind.LOCAL] ones; `id` and `sync_id` are never in it.
 */
object SyncSchema {
    /** Protocol version written to each document's `v`. A record with a higher one is held, not applied. */
    const val VERSION = 1

    enum class Kind {
        /** JSON string (or null) */
        TEXT,
        /** JSON integer (or null) */
        INT,
        /** JSON true / false; stored as 0 / 1 */
        BOOL,
        /** JSON string holding a plain decimal ("12.50", "-3"), never a number */
        DECIMAL,
        /** JSON string `yyyy-MM-ddTHH:mm[:ss[.fraction]]`, local time, no zone; older rows may have a space for the T */
        DATETIME,
        /** JSON string `yyyy-MM-dd` */
        DATE,
        /** JSON string: the sync id of the referenced record in [Column.ref] (or null) */
        REF,
        /** Device-specific (file paths, Android resource ids): never sent, and a received record keeps the local value */
        LOCAL,
    }

    data class Column(val name: String, val kind: Kind, val nullable: Boolean = false, val ref: String? = null)

    /**
     * A synced table. [level] orders application: a record is applied after the records it
     * refers to (lower levels). [uniqueKeys] are the table's natural keys (its unique indexes): two
     * records with one key are one record (see docs/sync.md, "Duplicates").
     */
    data class Table(val name: String, val level: Int, val columns: List<Column>, val uniqueKeys: List<List<String>> = emptyList()) {
        val refs: List<Column> get() = columns.filter { it.kind == Kind.REF }
        val synced: List<Column> get() = columns.filter { it.kind != Kind.LOCAL }
    }

    private fun text(name: String, nullable: Boolean = false) = Column(name, Kind.TEXT, nullable)
    private fun int(name: String, nullable: Boolean = false) = Column(name, Kind.INT, nullable)
    private fun bool(name: String) = Column(name, Kind.BOOL)
    private fun decimal(name: String, nullable: Boolean = false) = Column(name, Kind.DECIMAL, nullable)
    private fun dateTime(name: String, nullable: Boolean = false) = Column(name, Kind.DATETIME, nullable)
    private fun date(name: String, nullable: Boolean = true) = Column(name, Kind.DATE, nullable)
    private fun ref(name: String, table: String, nullable: Boolean = true) = Column(name, Kind.REF, nullable, table)
    private fun local(name: String) = Column(name, Kind.LOCAL, true)
    private val stamp = int("sync_updated_at")

    val TABLES: List<Table> = listOf(
        Table(
            "accounts", 0,
            listOf(
                text("name"), text("last4"), text("main_currency"), bool("is_credit_card"), bool("is_wallet"),
                decimal("credit_limit", true), local("icon_res_id"), text("icon_name"), text("color"), bool("is_sample"),
                dateTime("created_at"), int("statement_day", true), int("due_day", true), stamp,
            ),
            uniqueKeys = listOf(listOf("name", "last4")),
        ),
        Table(
            "categories", 0,
            listOf(
                text("name"), text("color"), local("icon_res_id"), text("icon_name"), text("description"),
                bool("is_system"), bool("is_income"), int("display_order"), text("default_name", true),
                text("default_color", true), local("default_icon_res_id"), text("default_icon_name", true),
                text("default_description", true), dateTime("created_at"), dateTime("updated_at"), stamp,
            ),
            uniqueKeys = listOf(listOf("name")),
        ),
        Table(
            "budgets", 0,
            listOf(
                text("name"), decimal("amount"), int("year"), int("month"), text("currency"), bool("is_active"),
                dateTime("created_at"), dateTime("updated_at"), dateTime("start_date"), dateTime("end_date"),
                text("period_type"), text("track_type"), text("budget_type"), text("account_ids"), text("color"),
                bool("is_sample"), stamp,
            ),
        ),
        Table(
            "lend_borrow_persons", 0,
            listOf(
                text("name"), text("phone_number", true), text("notes", true), text("color"), local("avatar"),
                text("category", true), bool("is_archived"), dateTime("created_at"), dateTime("updated_at"), stamp,
            ),
        ),
        Table(
            "cards", 0,
            listOf(
                text("card_last4"), text("card_type"), text("bank_name"), text("account_last4", true),
                text("nickname", true), bool("is_active"), decimal("last_balance", true),
                dateTime("last_balance_date", true), dateTime("created_at"), dateTime("updated_at"), text("currency"),
                bool("is_sample"), stamp,
            ),
            uniqueKeys = listOf(listOf("bank_name", "card_last4")),
        ),
        Table(
            "subscriptions", 0,
            listOf(
                text("merchant_name"), decimal("amount"), date("next_payment_date"), text("state"),
                text("bank_name", true), text("category", true), text("subcategory", true), text("notes", true),
                dateTime("created_at"), dateTime("updated_at"), text("currency"), text("billing_cycle", true),
                date("last_paid_date"), bool("is_sample"), stamp,
            ),
        ),
        Table(
            "quick_templates", 0,
            listOf(
                text("name"), text("merchant_name"), text("category"), text("subcategory", true),
                text("transaction_type"), decimal("amount", true), bool("prefill_amount"), text("bank_name", true),
                text("account_last4", true), text("currency", true), text("notes", true), int("sort_order"),
                dateTime("created_at"), dateTime("updated_at"), stamp,
            ),
        ),
        Table(
            "account_currencies", 1,
            listOf(
                ref("account_id", "accounts", nullable = false), text("currency"), decimal("credit_limit", true),
                dateTime("created_at"), stamp,
            ),
            uniqueKeys = listOf(listOf("account_id", "currency")),
        ),
        Table(
            "subcategories", 1,
            listOf(
                ref("category_id", "categories", nullable = false), text("name"), local("icon_res_id"),
                text("icon_name"), text("color"), bool("is_system"), text("default_name", true),
                local("default_icon_res_id"), text("default_icon_name", true), text("default_color", true),
                dateTime("created_at"), dateTime("updated_at"), stamp,
            ),
        ),
        Table(
            "budget_category_limits", 1,
            listOf(
                ref("budget_id", "budgets", nullable = false), text("category_name"), decimal("limit_amount"),
                dateTime("created_at"), dateTime("updated_at"), stamp,
            ),
        ),
        Table(
            "transactions", 1,
            listOf(
                decimal("amount"), text("merchant_name"), text("category"), text("subcategory", true),
                text("transaction_type"), dateTime("date_time"), text("description", true), text("bank_name", true),
                text("account_number", true), decimal("balance_after", true), text("transaction_hash"),
                bool("is_recurring"), bool("is_deleted"), dateTime("created_at"), dateTime("updated_at"),
                text("currency"), text("from_account", true), text("to_account", true), decimal("to_amount", true),
                ref("account_id", "accounts"), ref("to_account_id", "accounts"), text("to_currency", true),
                text("reference", true), text("billing_cycle", true), local("attachments"), bool("is_sample"), stamp,
            ),
            uniqueKeys = listOf(listOf("transaction_hash")),
        ),
        Table(
            "account_balances", 2,
            listOf(
                local("icon_res_id"), text("icon_name"), text("bank_name"), text("account_last4"), decimal("balance"),
                dateTime("timestamp"), ref("transaction_id", "transactions"), decimal("credit_limit", true),
                bool("is_credit_card"), text("source_type", true), dateTime("created_at"), text("currency"),
                bool("is_wallet"), text("color"), bool("is_sample"), ref("account_id", "accounts"), stamp,
            ),
            uniqueKeys = listOf(listOf("bank_name", "account_last4", "currency", "timestamp")),
        ),
        Table(
            "lend_borrow_transactions", 2,
            listOf(
                ref("person_id", "lend_borrow_persons", nullable = false), ref("transaction_id", "transactions"),
                text("type"), decimal("amount"), text("currency"), text("title"), dateTime("due_date", true),
                bool("is_settled"), dateTime("date"), dateTime("created_at"), dateTime("updated_at"),
                bool("is_sample"), ref("account_id", "accounts"), text("category_name", true),
                text("merchant_name", true), local("attachments"), stamp,
            ),
        ),
    )

    private val byName = TABLES.associateBy { it.name }

    fun table(name: String): Table? = byName[name]

    /** The document id of a record: `{table}_{syncId}`. */
    fun docId(table: String, syncId: String) = "${table}_$syncId"
}
