package io.github.hootkit_dev.hootbook.presentation.common

import io.github.hootkit_dev.hootbook.data.currency.Conversions
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.SubcategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.CategoryRepository
import io.github.hootkit_dev.hootbook.data.repository.LendBorrowRepository
import io.github.hootkit_dev.hootbook.data.repository.SubcategoryRepository
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.domain.model.PersonInfo
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Everything a transaction row shows besides the transaction itself. */
data class TransactionDecoration(
    val category: CategoryEntity?,
    val subcategory: SubcategoryEntity?,
    val account: AccountBalanceEntity?,
    // The lend/borrow person the transaction is linked to
    val person: PersonInfo?,
    val convertedAmount: BigDecimal?,
    val rateLoading: Boolean
)

/**
 * The tables every transaction list looks rows up in: categories by name, subcategories by
 * category and name, accounts by bank and number, and lend/borrow people by transaction.
 */
data class TransactionLookups(
    val categories: Map<String, CategoryEntity> = emptyMap(),
    // Subcategory names repeat across categories ("Other"), so the category is part of the key
    val subcategories: Map<Pair<Long, String>, SubcategoryEntity> = emptyMap(),
    val accounts: Map<String, AccountBalanceEntity> = emptyMap(),
    val accountsById: Map<Long, AccountBalanceEntity> = emptyMap(),
    val persons: Map<Long, PersonInfo> = emptyMap()
) {
    fun category(tx: TransactionEntity): CategoryEntity? = categories[tx.category]

    fun subcategory(tx: TransactionEntity): SubcategoryEntity? {
        val name = tx.subcategory ?: return null
        val category = category(tx) ?: return null
        return subcategories[category.id to name]
    }

    // By id first: a transaction keeps its account id even when its stored bank name is out of
    // date (an import that wrote the English name, an old rename)
    fun account(tx: TransactionEntity): AccountBalanceEntity? =
        tx.accountId?.let { accountsById[it] } ?: accounts[accountKey(tx.bankName, tx.accountNumber)]

    fun decorate(tx: TransactionEntity, conversions: Conversions = Conversions()) = TransactionDecoration(
        category = category(tx),
        subcategory = subcategory(tx),
        account = account(tx),
        person = persons[tx.id],
        convertedAmount = conversions.amountOf(tx),
        rateLoading = conversions.isLoading(tx)
    )

    companion object {
        fun accountKey(bankName: String?, last4: String?) = "${bankName}_$last4"
    }
}

/**
 * One shared, live [TransactionLookups] for every list (Home, Transactions, budget and account
 * detail), so each screen neither loads nor rebuilds these tables itself.
 */
@Singleton
class TransactionLookupsSource @Inject constructor(
    categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository,
    accountBalanceRepository: AccountBalanceRepository,
    lendBorrowRepository: LendBorrowRepository,
    @ApplicationScope scope: CoroutineScope
) {
    val lookups: StateFlow<TransactionLookups> = combine(
        categoryRepository.getAllCategories(),
        subcategoryRepository.getAllSubcategories(),
        accountBalanceRepository.getAllLatestBalances(),
        lendBorrowRepository.getAllTransactions(),
        lendBorrowRepository.getPersons()
    ) { categories, subcategories, accounts, lendBorrow, people ->
        val peopleById = people.associateBy { it.id }
        TransactionLookups(
            categories = categories.associateBy { it.name },
            subcategories = subcategories.associateBy { it.categoryId to it.name },
            accounts = accounts.associateBy { TransactionLookups.accountKey(it.bankName, it.accountLast4) },
            accountsById = accounts.mapNotNull { a -> a.accountId?.let { it to a } }.toMap(),
            persons = lendBorrow.mapNotNull { lb ->
                val id = lb.transactionId ?: return@mapNotNull null
                val person = peopleById[lb.personId]
                id to PersonInfo(
                    name = person?.name ?: lb.title,
                    color = person?.color ?: "#4CAF50",
                    avatar = person?.avatar
                )
            }.toMap()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), TransactionLookups())
}
