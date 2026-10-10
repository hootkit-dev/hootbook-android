package io.github.hootkit_dev.hootbook.data.ai

import io.github.hootkit_dev.hootbook.data.currency.CurrencyConversionService
import androidx.room.withTransaction
import io.github.hootkit_dev.hootbook.data.database.dao.PocketBalance
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.AccountEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionType
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountRenamer
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.domain.usecase.AddTransactionUseCase
import io.github.hootkit_dev.hootbook.presentation.common.icons.InstitutionCatalog
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A transaction the model wants to add, resolved against the user's accounts and categories. */
data class TransactionDraft(
    val dateTime: LocalDateTime,
    val amount: BigDecimal,
    val currency: String,
    val type: TransactionType,
    val merchant: String,
    val category: String,
    val subcategory: String?,
    val account: AccountBalanceEntity?,
    val toAccount: AccountBalanceEntity?,
    // What reached [toAccount] when it is in another currency; null converts at today's rate
    val toAmount: BigDecimal? = null,
    // Which of [toAccount]'s currencies a transfer lands in
    val toCurrency: String? = null,
    val notes: String?,
    // An existing transaction with the same amount around the same day, on the same account
    val possibleDuplicate: TransactionEntity?
)

/** One change the model proposed. Nothing is written until the user approves it. */
sealed interface LedgerChange {
    // A new account; [ref] (N1, N2…) is how the model's transactions refer to it
    data class CreateAccount(val ref: String, val account: AccountBalanceEntity) : LedgerChange
    // A balance (and, for a card, credit limit) calibration on an existing account; [account] is
    // the currency calibrated, with that currency's balance
    data class SetBalance(
        val ref: String,
        val account: AccountBalanceEntity,
        val balance: BigDecimal,
        val creditLimit: BigDecimal?
    ) : LedgerChange
    // New name, kind or credit limit for an existing account; [after] is how it will look
    // and [addedCurrencies] the currencies it starts to hold
    data class UpdateAccount(
        val ref: String,
        val before: AccountBalanceEntity,
        val after: AccountBalanceEntity,
        val addedCurrencies: List<String> = emptyList()
    ) : LedgerChange
    data class Add(val draft: TransactionDraft) : LedgerChange
    data class Update(val before: TransactionEntity, val after: TransactionEntity) : LedgerChange
    data class Delete(val transaction: TransactionEntity, val reason: String?) : LedgerChange
}

/** What [LedgerTools.apply] did, enough to take it back. */
data class AppliedChanges(
    val createdAccounts: List<AccountBalanceEntity>,
    // Balance rows written for calibrations and account edits
    val balanceRowIds: List<Long>,
    // Accounts renamed: (name before, name after, last 4)
    val renames: List<Triple<String, String, String>>,
    val addedIds: List<Long>,
    val updated: List<TransactionEntity>,
    val deleted: List<TransactionEntity>,
    // Accounts' details before their kind, limit or look changed
    val accountsBefore: List<AccountEntity> = emptyList(),
    // Currencies added to accounts: (account id, currency)
    val addedCurrencies: List<Pair<Long, String>> = emptyList()
)

/**
 * The ledger as tools for a model: one read tool and three write tools. Writes only queue a
 * [LedgerChange] for review; [apply] commits the ones the user keeps through the same paths the
 * app's own screens use, so balances stay right.
 */
class LedgerTools @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val accountRenamer: AccountRenamer,
    private val currencyConversionService: CurrencyConversionService,
    private val database: io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
) {
    /** The user's accounts and categories, loaded once per session. */
    class Context internal constructor(
        val accounts: List<AccountBalanceEntity>,
        val categories: List<CategoryEntity>,
        val subcategories: Map<Long, List<SubcategoryEntity>>,
        // Each account's currencies and their balances, by account id
        val pockets: Map<Long, List<PocketBalance>> = emptyMap()
    ) {
        // Short stable references the model uses instead of bank names: A1, A2…
        val accountRefs: Map<String, AccountBalanceEntity> =
            accounts.mapIndexed { i, account -> "A${i + 1}" to account }.toMap()

        // Accounts proposed in this session: N1, N2…
        internal val newAccounts = linkedMapOf<String, AccountBalanceEntity>()

        fun account(ref: String): AccountBalanceEntity? = accountRefs[ref] ?: newAccounts[ref]

        // Currencies proposed for accounts in this session, by ref
        internal val addedCurrencies = mutableMapOf<String, MutableSet<String>>()

        /** The currencies the account [ref] holds, its main one first, with those proposed for it. */
        fun currenciesOf(ref: String): List<String> {
            val account = account(ref) ?: return emptyList()
            val held = account.accountId?.let { pockets[it] }.orEmpty().map { it.currency }
            return (listOf(account.currency) + held + addedCurrencies[ref].orEmpty()).distinct()
        }

        fun pocket(account: AccountBalanceEntity, currency: String): PocketBalance? =
            account.accountId?.let { pockets[it] }?.firstOrNull { it.currency == currency }

        fun refOf(bankName: String?, last4: String?): String? =
            accountRefs.entries.firstOrNull { it.value.bankName == bankName && it.value.accountLast4 == last4 }?.key
    }

    suspend fun context(): Context = Context(
        accounts = accountBalanceRepository.getAllLatestBalances().first(),
        categories = categoryRepository.getAllCategories().first(),
        subcategories = subcategoryRepository.getAllSubcategories().first().groupBy { it.categoryId },
        pockets = accountBalanceRepository.pocketBalances().groupBy { it.accountId }
    )

    /** The accounts and categories as the model sees them, for the system prompt. */
    fun describe(context: Context): String = buildString {
        appendLine("Accounts (ref: name, main currency):")
        context.accountRefs.forEach { (ref, a) ->
            val kind = when {
                a.isCreditCard -> ", credit card"
                a.isWallet -> ", wallet"
                else -> ""
            }
            val limit = a.creditLimit?.let { ", limit ${it.toPlainString()}" } ?: ""
            val others = a.accountId?.let { context.pockets[it] }.orEmpty().filter { it.currency != a.currency }
            val also = if (others.isEmpty()) "" else
                "; also holds " + others.joinToString(", ") { "${it.currency} balance ${it.balance.toPlainString()}" }
            appendLine("- $ref: ${a.bankName} ${a.accountLast4}, ${a.currency}$kind, balance ${a.balance.toPlainString()}$limit$also")
        }
        appendLine()
        appendLine("Categories (name: subcategories):")
        context.categories.sortedWith(compareBy({ it.isIncome }, { it.displayOrder })).forEach { c ->
            val subs = context.subcategories[c.id].orEmpty().joinToString(", ") { it.name }
            appendLine("- ${if (c.isIncome) "[income] " else ""}${c.name}${if (subs.isNotEmpty()) ": $subs" else ""}")
        }
    }

    val tools: List<AiTool> = listOf(
        AiTool(
            name = FIND,
            description = "Find the user's existing transactions between two dates (inclusive), optionally " +
                "matching text in merchant or notes, or an exact amount. Use it to find the ids of " +
                "transactions to change or delete, or to answer questions about them. Not needed to avoid " +
                "duplicates: the app checks every proposed transaction against the ledger itself.",
            schema = schema(
                required = listOf("from_date", "to_date"),
                "from_date" to str("yyyy-MM-dd"),
                "to_date" to str("yyyy-MM-dd"),
                "text" to str("Optional text to match"),
                "amount" to num("Optional exact amount")
            )
        ),
        AiTool(
            name = ADD,
            description = "Propose new transactions for the user to review (at most 50 per call). Amounts " +
                "are positive; the type gives the direction. A transfer needs to_account; it may move money " +
                "between two currencies of one account.",
            schema = schema(
                required = listOf("transactions"),
                "transactions" to array(
                    schema(
                        required = listOf("date", "amount", "type", "merchant", "category"),
                        "date" to str("yyyy-MM-dd or yyyy-MM-dd HH:mm"),
                        "amount" to num("Positive amount in the transaction's currency (what was billed); note a foreign original amount in notes"),
                        "currency" to str("ISO code. With an account: one of the currencies it holds, its main one if left out"),
                        "type" to enumOf("EXPENSE", "INCOME", "TRANSFER"),
                        "merchant" to str("Payee or payer as the user would name it"),
                        "category" to str("Exactly one of the user's category names"),
                        "subcategory" to str("One of that category's subcategories, if any fits"),
                        "account" to str("Account ref such as A1"),
                        "to_account" to str("Transfers only: the receiving account ref"),
                        "to_currency" to str("Transfers only: which of the receiving account's currencies it lands in; the sent currency if it holds that, else its main one"),
                        "to_amount" to num("Transfers between currencies: the amount received, in to_currency; converted at today's rate if left out"),
                        "notes" to str("Optional note")
                    )
                )
            )
        ),
        AiTool(
            name = CREATE_ACCOUNT,
            description = "Propose a new account for the user to review, when a document belongs to a card or " +
                "account that is not listed. Its ref is N1 for the first one you propose, N2 for the next and " +
                "so on; use it as account or to_account in add_transactions, in the same reply if you like. " +
                "Never propose an account that is already listed.",
            schema = schema(
                required = listOf("name", "type", "currency"),
                "name" to str("Bank, card issuer or wallet, as the user would name it, e.g. 招商银行"),
                "last4" to str("Last 4 digits of the card or account number; leave out for a wallet"),
                "type" to enumOf("BANK", "CREDIT_CARD", "WALLET"),
                "currency" to str("ISO code"),
                "balance" to num("Current balance if the document states it; credit cards: amount owed, else 0"),
                "credit_limit" to num("Credit cards only, if stated")
            )
        ),
        AiTool(
            name = SET_BALANCE,
            description = "Propose correcting a listed account's balance to what the document shows as its " +
                "current balance (for a credit card, the amount owed). Only when they differ, and only for a " +
                "balance that is current: a statement's closing balance is not, if later transactions exist.",
            schema = schema(
                required = listOf("account", "balance"),
                "account" to str("Account ref such as A1"),
                "balance" to num("The correct current balance; for a credit card, the amount owed"),
                "currency" to str("Which of the account's currencies; its main one if left out"),
                "credit_limit" to num("Credit cards only, main currency only: the credit limit, if the document states it")
            )
        ),
        AiTool(
            name = UPDATE_ACCOUNT,
            description = "Propose renaming a listed account, changing its kind or credit limit, or adding " +
                "currencies it holds (an account can hold several, e.g. a Hong Kong account with HKD and USD). " +
                "Its main currency and last 4 digits cannot be changed, and currencies cannot be removed.",
            schema = schema(
                required = listOf("account"),
                "account" to str("Account ref such as A1"),
                "name" to str("New name"),
                "type" to enumOf("BANK", "CREDIT_CARD", "WALLET"),
                "credit_limit" to num("Credit cards only"),
                "add_currencies" to arrayOf(str("ISO code of a currency the account should also hold"))
            )
        ),
        AiTool(
            name = UPDATE,
            description = "Propose changes to existing transactions: merchant, category, subcategory or " +
                "notes. To change an amount, date, type or account, delete the transaction and add a " +
                "corrected one instead.",
            schema = schema(
                required = listOf("changes"),
                "changes" to array(
                    schema(
                        required = listOf("id"),
                        "id" to int("Transaction id from find_transactions"),
                        "merchant" to str(""),
                        "category" to str(""),
                        "subcategory" to str(""),
                        "notes" to str("")
                    )
                )
            )
        ),
        AiTool(
            name = DELETE,
            description = "Propose deleting transactions. Deleted transactions go to the trash.",
            schema = schema(
                required = listOf("ids"),
                "ids" to arrayOf(int("Transaction id")),
                "reason" to str("Why, shown to the user")
            )
        ),
        AiTool(
            name = FINISH,
            description = "End the session. Call it in the same reply as your last proposals, with a summary " +
                "for the user. If any call in that reply is rejected, you get another turn to fix it.",
            schema = schema(
                required = listOf("summary"),
                "summary" to str("One or two plain sentences in the user's language: what you proposed, and " +
                    "anything you could not read or were unsure about")
            )
        )
    )

    /** Runs one tool call: reads answer at once, writes add to [queue]. Returns the tool result. */
    suspend fun run(call: AiToolCall, context: Context, queue: MutableList<LedgerChange>): AiToolResult {
        call.input[AiChat.INVALID_ARGUMENTS]?.let {
            return AiToolResult(call.id, "The arguments were not valid JSON.", isError = true)
        }
        return try {
            val text = when (call.name) {
                FIND -> find(call.input, context)
                ADD -> add(call.input, context, queue)
                CREATE_ACCOUNT -> createAccount(call.input, context, queue)
                SET_BALANCE -> setBalance(call.input, context, queue)
                UPDATE_ACCOUNT -> updateAccount(call.input, context, queue)
                UPDATE -> update(call.input, context, queue)
                DELETE -> delete(call.input, queue)
                FINISH -> "Finished."
                else -> return AiToolResult(call.id, "Unknown tool ${call.name}", isError = true)
            }
            AiToolResult(call.id, text)
        } catch (e: IllegalArgumentException) {
            AiToolResult(call.id, e.message ?: "Invalid input", isError = true)
        }
    }

    private suspend fun find(input: JsonObject, context: Context): String {
        val from = date(input.string("from_date")).atStartOfDay()
        val to = date(input.string("to_date")).atTime(LocalTime.MAX)
        val text = input.string("text")?.lowercase()
        val amount = input.decimal("amount")
        val found = transactionRepository.getTransactionsBetweenDates(from, to).first()
            .asSequence()
            .filter { !it.isDeleted }
            .filter {
                text == null || it.merchantName.lowercase().contains(text) ||
                    it.description.orEmpty().lowercase().contains(text)
            }
            .filter { amount == null || it.amount.compareTo(amount) == 0 }
            .sortedBy { it.dateTime }
            .toList()
        val shown = found.take(MAX_FOUND)
        return buildJsonObject {
            put("count", found.size)
            if (found.size > shown.size) put("note", "Only the first $MAX_FOUND are listed; narrow the range.")
            putJsonArray("transactions") {
                shown.forEach { t ->
                    addJsonObject {
                        put("id", t.id)
                        put("date", t.dateTime.format(DATE_TIME))
                        put("amount", t.amount.toPlainString())
                        put("currency", t.currency)
                        put("type", t.transactionType.name)
                        put("merchant", t.merchantName)
                        put("category", t.category)
                        t.subcategory?.let { put("subcategory", it) }
                        context.refOf(t.bankName, t.accountNumber)?.let { put("account", it) }
                        t.description?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
                    }
                }
            }
        }.toString()
    }

    private suspend fun add(input: JsonObject, context: Context, queue: MutableList<LedgerChange>): String {
        val items = input["transactions"] as? JsonArray ?: throw IllegalArgumentException("transactions is required")
        val errors = mutableListOf<String>()
        var queued = 0
        items.forEachIndexed { index, element ->
            try {
                queue += LedgerChange.Add(draft(element.jsonObject, context))
                queued++
            } catch (e: IllegalArgumentException) {
                errors += "#$index: ${e.message}"
            }
        }
        return result(queued, errors)
    }

    private suspend fun draft(item: JsonObject, context: Context): TransactionDraft {
        val dateText = item.string("date") ?: throw IllegalArgumentException("date is required")
        val dateTime = dateTime(dateText)
        val amount = item.decimal("amount")?.abs()?.takeIf { it.signum() > 0 }
            ?: throw IllegalArgumentException("amount must be a positive number")
        val type = item.string("type")?.let { name -> ALLOWED_TYPES.firstOrNull { it.name == name } }
            ?: throw IllegalArgumentException("type must be EXPENSE, INCOME or TRANSFER")
        val accountRef = item.string("account")
        val account = accountRef?.let {
            context.account(it) ?: throw IllegalArgumentException("unknown account $it")
        }
        val toRef = item.string("to_account")
        val toAccount = toRef?.let {
            context.account(it) ?: throw IllegalArgumentException("unknown account $it")
        }
        if (type == TransactionType.TRANSFER && (account == null || toAccount == null)) {
            throw IllegalArgumentException("a transfer needs account and to_account")
        }
        // An account's transactions are in a currency it holds, or its balance would change by a foreign amount
        val asked = item.string("currency")?.uppercase()
        val currency = if (accountRef == null) asked ?: DEFAULT_CURRENCY else heldCurrency(context, accountRef, asked)
        val toCurrency = toRef?.let { ref ->
            item.string("to_currency")?.uppercase()?.let { heldCurrency(context, ref, it) }
                ?: currency.takeIf { it in context.currenciesOf(ref) }
                ?: toAccount!!.currency
        }
        if (toRef != null && toRef == accountRef && toCurrency == currency) {
            throw IllegalArgumentException("a transfer within $toRef goes between two of its currencies; give to_currency")
        }
        val (category, subcategory) = category(item.string("category"), item.string("subcategory"), context)
        val possibleDuplicate = transactionRepository
            .findPotentialDuplicates(amount, dateTime.minusDays(1), dateTime.plusDays(1))
            .firstOrNull { existing ->
                !existing.isDeleted && (account == null || existing.bankName == account.bankName &&
                    existing.accountNumber == account.accountLast4)
            }
        return TransactionDraft(
            dateTime = dateTime,
            amount = amount,
            currency = currency,
            type = type,
            merchant = item.string("merchant").orEmpty().trim(),
            category = category,
            subcategory = subcategory,
            account = account,
            toAccount = toAccount,
            toAmount = item.decimal("to_amount")?.abs()?.takeIf { toCurrency != null && toCurrency != currency && it.signum() > 0 },
            toCurrency = toCurrency,
            notes = item.string("notes")?.trim()?.takeIf { it.isNotEmpty() },
            possibleDuplicate = possibleDuplicate
        )
    }

    /** [wanted] (else the main currency) when the account [ref] holds it. */
    private fun heldCurrency(context: Context, ref: String, wanted: String?): String {
        val held = context.currenciesOf(ref)
        return when {
            wanted == null -> held.first()
            wanted in held -> wanted
            else -> throw IllegalArgumentException(
                "$ref holds ${held.joinToString()}, not $wanted: use one of those (a foreign original amount " +
                    "goes in notes), or first propose adding $wanted with update_account add_currencies"
            )
        }
    }

    private fun createAccount(input: JsonObject, context: Context, queue: MutableList<LedgerChange>): String {
        val name = input.string("name")?.trim() ?: throw IllegalArgumentException("name is required")
        val type = input.string("type") ?: throw IllegalArgumentException("type is required")
        val isWallet = type == "WALLET"
        val last4 = if (isWallet) input.string("last4")?.filter(Char::isDigit)?.takeLast(4) ?: WALLET_LAST4
        else input.string("last4")?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }
            ?: throw IllegalArgumentException("last4 must be 4 digits")
        val institution = InstitutionCatalog.find(name)
        val bankName = institution?.chineseName?.takeIf { name.any { c -> c.code > 0x2E80 } } ?: name
        (context.accountRefs + context.newAccounts).entries
            .firstOrNull { it.value.accountLast4 == last4 && (it.value.bankName == bankName || it.value.bankName == name) }
            ?.let { return "Already exists as ${it.key}; use that ref." }
        val ref = "N${context.newAccounts.size + 1}"
        val account = AccountBalanceEntity(
            bankName = bankName,
            accountLast4 = last4,
            balance = input.decimal("balance")?.abs() ?: BigDecimal.ZERO,
            creditLimit = input.decimal("credit_limit")?.takeIf { type == "CREDIT_CARD" },
            timestamp = LocalDateTime.now(),
            isCreditCard = type == "CREDIT_CARD",
            isWallet = isWallet,
            iconResId = institution?.iconResId ?: 0,
            iconName = institution?.iconName ?: "",
            sourceType = "MANUAL",
            currency = input.string("currency")?.uppercase() ?: institution?.currency ?: DEFAULT_CURRENCY,
            color = institution?.color ?: DEFAULT_ACCOUNT_COLOR
        )
        context.newAccounts[ref] = account
        queue += LedgerChange.CreateAccount(ref, account)
        return "Queued account $ref for the user's review. Use $ref as account for its transactions."
    }

    private fun existingAccount(input: JsonObject, context: Context): Pair<String, AccountBalanceEntity> {
        val ref = input.string("account") ?: throw IllegalArgumentException("account is required")
        val account = context.accountRefs[ref]
            ?: throw IllegalArgumentException(
                if (ref in context.newAccounts) "$ref is a new account; give its details in create_account instead"
                else "unknown account $ref"
            )
        return ref to account
    }

    private fun setBalance(input: JsonObject, context: Context, queue: MutableList<LedgerChange>): String {
        val (ref, main) = existingAccount(input, context)
        val currency = heldCurrency(context, ref, input.string("currency")?.uppercase())
        // The currency calibrated, with its own balance
        val account = if (currency == main.currency) main else context.pocket(main, currency).let { pocket ->
            main.copy(currency = currency, balance = pocket?.balance ?: BigDecimal.ZERO, creditLimit = pocket?.creditLimit)
        }
        val balance = input.decimal("balance") ?: throw IllegalArgumentException("balance is required")
        val limit = input.decimal("credit_limit")?.abs()?.takeIf { account.isCreditCard && currency == main.currency }
        if (balance.compareTo(account.balance) == 0 && (limit == null || account.creditLimit?.compareTo(limit) == 0)) {
            return "$ref already has that balance; nothing to change."
        }
        queue.removeAll { it is LedgerChange.SetBalance && it.ref == ref && it.account.currency == currency }
        queue += LedgerChange.SetBalance(ref, account, balance, limit)
        return "Queued balance correction for $ref for the user's review."
    }

    private fun updateAccount(input: JsonObject, context: Context, queue: MutableList<LedgerChange>): String {
        val (ref, account) = existingAccount(input, context)
        val name = input.string("name")?.trim()
        val type = input.string("type")
        val institution = name?.let { InstitutionCatalog.find(it) }
        var after = account
        if (name != null && name != account.bankName) {
            if (context.accountRefs.values.any { it.bankName == name && it.accountLast4 == account.accountLast4 }) {
                throw IllegalArgumentException("another account is already named $name ${account.accountLast4}")
            }
            after = after.copy(
                bankName = name,
                // Take the institution's look when the new name is a known one
                iconResId = institution?.iconResId ?: after.iconResId,
                iconName = institution?.iconName ?: after.iconName,
                color = institution?.color ?: after.color
            )
        }
        if (type != null) after = after.copy(isCreditCard = type == "CREDIT_CARD", isWallet = type == "WALLET")
        input.decimal("credit_limit")?.abs()?.let { if (after.isCreditCard) after = after.copy(creditLimit = it) }
        val held = account.accountId?.let { context.pockets[it] }.orEmpty().map { it.currency } + account.currency
        val added = (input["add_currencies"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase() }
            .onEach { if (!it.matches(Regex("[A-Z]{3}"))) throw IllegalArgumentException("$it is not an ISO currency code") }
            .filter { it !in held }
            .distinct()
        if (after == account && added.isEmpty()) return "$ref already looks like that; nothing to change."
        queue.removeAll { it is LedgerChange.UpdateAccount && it.ref == ref }
        context.addedCurrencies[ref] = added.toMutableSet()
        queue += LedgerChange.UpdateAccount(ref, account, after, added)
        return "Queued changes to $ref for the user's review."
    }

    private suspend fun update(input: JsonObject, context: Context, queue: MutableList<LedgerChange>): String {
        val items = input["changes"] as? JsonArray ?: throw IllegalArgumentException("changes is required")
        val errors = mutableListOf<String>()
        var queued = 0
        items.forEachIndexed { index, element ->
            val item = element.jsonObject
            try {
                val id = item.long("id") ?: throw IllegalArgumentException("id is required")
                val before = transactionRepository.getTransactionById(id)?.takeIf { !it.isDeleted }
                    ?: throw IllegalArgumentException("no transaction $id")
                val (category, subcategory) = if (item.string("category") != null || item.string("subcategory") != null) {
                    category(item.string("category") ?: before.category, item.string("subcategory"), context)
                } else before.category to before.subcategory
                val after = before.copy(
                    merchantName = item.string("merchant")?.trim() ?: before.merchantName,
                    category = category,
                    subcategory = subcategory,
                    description = item.string("notes") ?: before.description,
                    updatedAt = LocalDateTime.now()
                )
                if (after.copy(updatedAt = before.updatedAt) != before) {
                    queue.removeAll { it is LedgerChange.Update && it.before.id == id }
                    queue += LedgerChange.Update(before, after)
                    queued++
                }
            } catch (e: IllegalArgumentException) {
                errors += "#$index: ${e.message}"
            }
        }
        return result(queued, errors)
    }

    private suspend fun delete(input: JsonObject, queue: MutableList<LedgerChange>): String {
        val ids = (input["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
            ?: throw IllegalArgumentException("ids is required")
        val reason = input.string("reason")
        val errors = mutableListOf<String>()
        var queued = 0
        ids.forEach { id ->
            val transaction = transactionRepository.getTransactionById(id)?.takeIf { !it.isDeleted }
            if (transaction == null) {
                errors += "no transaction $id"
            } else if (queue.none { it is LedgerChange.Delete && it.transaction.id == id }) {
                queue += LedgerChange.Delete(transaction, reason)
                queued++
            }
        }
        return result(queued, errors)
    }

    /** Commits approved changes. */
    suspend fun apply(changes: List<LedgerChange>): AppliedChanges =
        // One database transaction: a failure part-way writes nothing
        database.withTransaction { applyAll(changes) }

    private suspend fun applyAll(changes: List<LedgerChange>): AppliedChanges {
        // Accounts first, so the transactions on them find them
        val created = changes.filterIsInstance<LedgerChange.CreateAccount>().map { it.account }
        created.forEach { accountBalanceRepository.insertBalance(it.copy(timestamp = LocalDateTime.now())) }
        // Then the currencies accounts start to hold, which their transactions may be in
        val addedCurrencies = mutableListOf<Pair<Long, String>>()
        changes.filterIsInstance<LedgerChange.UpdateAccount>().forEach { change ->
            val accountId = accountBalanceRepository.account(change.before.bankName, change.before.accountLast4)?.id
                ?: return@forEach
            change.addedCurrencies.forEach { currency ->
                val now = LocalDateTime.now()
                accountBalanceRepository.insertBalance(
                    change.before.copy(
                        id = 0, balance = BigDecimal.ZERO, currency = currency, timestamp = now, transactionId = null,
                        sourceType = "MANUAL", createdAt = now
                    )
                )
                addedCurrencies += accountId to currency
            }
        }
        // A new account left out of the save: its transactions are saved without an account
        fun AccountBalanceEntity?.kept() = this?.takeIf { it.id != 0L || it in created }
        val added = mutableListOf<Long>()
        val updated = mutableListOf<TransactionEntity>()
        val deleted = mutableListOf<TransactionEntity>()
        changes.forEach { change ->
            when (change) {
                is LedgerChange.CreateAccount, is LedgerChange.SetBalance, is LedgerChange.UpdateAccount -> Unit
                is LedgerChange.Add -> with(change.draft.let { it.copy(account = it.account.kept(), toAccount = it.toAccount.kept()) }) {
                    // A currency whose addition was left out: the amount goes into the account's main one
                    val pocket = account?.let { accountBalanceRepository.pocketCurrency(it.bankName, it.accountLast4, currency) }
                        ?: currency
                    val amount = if (pocket == currency) amount else currencyConversionService.convertAmount(amount, currency, pocket)
                    val notes = if (pocket == currency) notes else
                        listOfNotNull(notes, "${this.amount.toPlainString()} $currency").joinToString(" · ")
                    val landsIn = toAccount?.let {
                        accountBalanceRepository.pocketCurrency(it.bankName, it.accountLast4, toCurrency ?: pocket)
                    }
                    added += addTransactionUseCase.execute(
                        amount = amount,
                        merchant = merchant,
                        category = category,
                        subcategory = subcategory,
                        type = type,
                        date = dateTime,
                        notes = notes,
                        bankName = account?.bankName,
                        accountLast4 = account?.accountLast4,
                        currency = pocket,
                        sourceAccountId = account?.id,
                        targetAccountBankName = toAccount?.bankName,
                        targetAccountLast4 = toAccount?.accountLast4,
                        targetCurrency = landsIn,
                        targetAmount = landsIn?.takeIf { it != pocket }?.let { target ->
                            toAmount?.takeIf { target == toCurrency } ?: currencyConversionService.convertAmount(amount, pocket, target)
                        },
                        createSubscription = false
                    )
                }
                is LedgerChange.Update -> {
                    // Only descriptive fields change, so balances are untouched
                    transactionRepository.updateTransaction(change.after)
                    updated += change.before
                }
                is LedgerChange.Delete -> {
                    transactionRepository.deleteTransaction(change.transaction)
                    deleted += change.transaction
                }
            }
        }
        // Account changes last, so a rename also moves the transactions just added to the account
        val balanceRows = mutableListOf<Long>()
        val renames = mutableListOf<Triple<String, String, String>>()
        val accountsBefore = mutableListOf<AccountEntity>()
        changes.filterIsInstance<LedgerChange.SetBalance>().forEach { change ->
            // Latest values for everything else, as the app's own balance calibration does
            val latest = accountBalanceRepository.getLatestBalance(change.account.bankName, change.account.accountLast4)
                ?: change.account
            balanceRows += accountBalanceRepository.insertBalance(
                latest.copy(
                    id = 0,
                    balance = change.balance,
                    currency = change.account.currency,
                    creditLimit = change.creditLimit ?: latest.creditLimit,
                    timestamp = LocalDateTime.now(),
                    transactionId = null,
                    
                    sourceType = "BALANCE_CALIBRATION",
                    createdAt = LocalDateTime.now()
                )
            )
            change.creditLimit?.let { limit ->
                accountBalanceRepository.updateAccount(change.account.bankName, change.account.accountLast4) {
                    it.copy(creditLimit = limit)
                }?.let { accountsBefore += it }
            }
        }
        changes.filterIsInstance<LedgerChange.UpdateAccount>().forEach { change ->
            val before = change.before
            // Only currencies added (done above)
            if (change.after == before) return@forEach
            if (change.after.bankName != before.bankName) {
                accountRenamer.rename(before.bankName, before.accountLast4, change.after.bankName)
                renames += Triple(before.bankName, change.after.bankName, before.accountLast4)
            }
            // The account takes the new kind, limit and look; its balance stays as it is
            accountBalanceRepository.updateAccount(change.after.bankName, before.accountLast4) {
                it.copy(
                    isCreditCard = change.after.isCreditCard,
                    isWallet = change.after.isWallet,
                    creditLimit = change.after.creditLimit,
                    iconResId = change.after.iconResId,
                    iconName = change.after.iconName,
                    color = change.after.color
                )
            }?.let { accountsBefore += it }
        }
        return AppliedChanges(created, balanceRows, renames, added, updated, deleted, accountsBefore, addedCurrencies)
    }

    /** Takes back what [apply] did. */
    suspend fun undo(applied: AppliedChanges) =
        database.withTransaction { undoAll(applied) }

    private suspend fun undoAll(applied: AppliedChanges) {
        // Reverse order of apply: account changes were made last
        applied.balanceRowIds.forEach { accountBalanceRepository.deleteBalanceById(it) }
        applied.accountsBefore.asReversed().forEach { accountBalanceRepository.restoreAccount(it) }
        applied.renames.asReversed().forEach { (before, after, last4) -> accountRenamer.rename(after, last4, before) }
        applied.addedIds.forEach { transactionRepository.deleteTransactionById(it, hardDelete = true) }
        applied.updated.forEach { transactionRepository.updateTransaction(it) }
        if (applied.deleted.isNotEmpty()) transactionRepository.undoDeleteTransactions(applied.deleted)
        applied.addedCurrencies.forEach { (accountId, currency) -> accountBalanceRepository.removeCurrency(accountId, currency) }
        applied.createdAccounts.forEach { accountBalanceRepository.deleteAccount(it.bankName, it.accountLast4) }
    }

    private fun category(name: String?, subcategory: String?, context: Context): Pair<String, String?> {
        val category = context.categories.firstOrNull { it.name == name }
            ?: context.categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException("unknown category $name; use one of the listed names")
        val sub = subcategory?.takeIf { it.isNotBlank() }?.let { wanted ->
            context.subcategories[category.id].orEmpty().firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.name
                ?: throw IllegalArgumentException("unknown subcategory $wanted in ${category.name}")
        }
        return category.name to sub
    }

    private fun result(queued: Int, errors: List<String>) = buildString {
        append("Queued $queued for the user's review.")
        if (errors.isNotEmpty()) append(" Rejected: ").append(errors.joinToString("; ")).append(". Fix and retry those.")
    }

    companion object {
        const val FIND = "find_transactions"
        const val ADD = "add_transactions"
        const val UPDATE = "update_transactions"
        const val DELETE = "delete_transactions"
        const val CREATE_ACCOUNT = "create_account"
        const val SET_BALANCE = "set_balance"
        const val UPDATE_ACCOUNT = "update_account"
        const val FINISH = "finish"
        private const val WALLET_LAST4 = "wallet"
        private const val DEFAULT_ACCOUNT_COLOR = "#33B5E5"
        private const val MAX_FOUND = 200
        private const val DEFAULT_CURRENCY = "CNY"
        private val ALLOWED_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private fun date(text: String?): LocalDate = try {
            LocalDate.parse(text?.take(10))
        } catch (e: Exception) {
            throw IllegalArgumentException("dates are yyyy-MM-dd, got $text")
        }

        internal fun dateTime(text: String): LocalDateTime {
            val day = date(text)
            val time = text.drop(10).trim().removePrefix("T").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalTime.parse(it.take(5)) }.getOrNull() }
            // Statements often give only a day: keep it at noon so time zones never move it to another day
            return day.atTime(time ?: LocalTime.NOON)
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.long(key: String): Long? =
            (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

        private fun JsonObject.decimal(key: String): BigDecimal? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.toBigDecimalOrNull()

        private fun str(description: String) = buildJsonObject {
            put("type", "string")
            if (description.isNotEmpty()) put("description", description)
        }

        private fun num(description: String) = buildJsonObject {
            put("type", "number")
            put("description", description)
        }

        private fun int(description: String) = buildJsonObject {
            put("type", "integer")
            put("description", description)
        }

        private fun enumOf(vararg values: String) = buildJsonObject {
            put("type", "string")
            put("enum", JsonArray(values.map { JsonPrimitive(it) }))
        }

        private fun array(items: JsonObject) = arrayOf(items)
        private fun arrayOf(items: JsonObject) = buildJsonObject {
            put("type", "array")
            put("items", items)
        }

        private fun schema(required: List<String>, vararg properties: Pair<String, JsonElement>) = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties.toMap()))
            put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
        }
    }
}
