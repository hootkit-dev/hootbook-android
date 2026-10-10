package io.github.hootkit_dev.hootbook.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.hootkit_dev.hootbook.data.database.HootbookDatabase
import io.github.hootkit_dev.hootbook.data.database.DatabaseCallback
import io.github.hootkit_dev.hootbook.data.database.SyncTriggers
import io.github.hootkit_dev.hootbook.data.database.dao.SyncDao

import io.github.hootkit_dev.hootbook.data.database.dao.AccountBalanceDao
import io.github.hootkit_dev.hootbook.data.database.dao.BudgetDao
import io.github.hootkit_dev.hootbook.data.database.dao.CardDao
import io.github.hootkit_dev.hootbook.data.database.dao.CategoryDao
import io.github.hootkit_dev.hootbook.data.database.dao.ExchangeRateDao
import io.github.hootkit_dev.hootbook.data.database.dao.SubcategoryDao
import io.github.hootkit_dev.hootbook.data.database.dao.SubscriptionDao
import io.github.hootkit_dev.hootbook.data.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils

/** Hilt module that provides database-related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton instance of HootbookDatabase.
     *
     * @param context Application context
     * @return Configured Room database instance
     */
    @Provides
    @Singleton
    fun provideHootbookDatabase(@ApplicationContext context: Context): HootbookDatabase {
        val database =
            Room.databaseBuilder(
                context,
                HootbookDatabase::class.java,
                HootbookDatabase.DATABASE_NAME
            )
                // Add manual migrations here when needed
                .addMigrations(*HootbookDatabase.MIGRATIONS)

                // Enable auto-migrations
                // Room will automatically detect schema changes between versions

                // Sync change capture first, so the seeded rows are captured too (docs/sync.md)
                .addCallback(SyncTriggers.Callback)
                // Add callback to seed default data on first creation
                .addCallback(DatabaseCallback(context))
                .build()

        return database
    }

    /**
     * Provides the TransactionDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return TransactionDao for accessing transaction data
     */
    @Provides
    @Singleton
    fun provideTransactionDao(database: HootbookDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: HootbookDatabase): io.github.hootkit_dev.hootbook.data.database.dao.AccountDao =
        database.accountDao()

    /**
     * Provides the SubscriptionDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return SubscriptionDao for accessing subscription data
     */
    @Provides
    @Singleton
    fun provideSubscriptionDao(database: HootbookDatabase): SubscriptionDao {
        return database.subscriptionDao()
    }

    /**
     * Provides the CategoryDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return CategoryDao for accessing category data
     */
    @Provides
    @Singleton
    fun provideCategoryDao(database: HootbookDatabase): CategoryDao {
        return database.categoryDao()
    }

    /**
     * Provides the AccountBalanceDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return AccountBalanceDao for accessing account balance data
     */
    @Provides
    @Singleton
    fun provideAccountBalanceDao(database: HootbookDatabase): AccountBalanceDao {
        return database.accountBalanceDao()
    }

    /**
     * Provides the CardDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return CardDao for accessing card data
     */
    @Provides
    @Singleton
    fun provideCardDao(database: HootbookDatabase): CardDao {
        return database.cardDao()
    }

    /**
     * Provides the ExchangeRateDao from the database.
     *
     * @param database The HootbookDatabase instance
     * @return ExchangeRateDao for accessing exchange rate data
     */
    @Provides
    @Singleton
    fun provideExchangeRateDao(database: HootbookDatabase): ExchangeRateDao {
        return database.exchangeRateDao()
    }

    @Provides
    @Singleton
    fun provideSubcategoryDao(database: HootbookDatabase): SubcategoryDao {
        return database.subcategoryDao()
    }

    @Provides
    @Singleton
    fun provideBudgetDao(database: HootbookDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    @Singleton
    fun provideLendBorrowDao(database: HootbookDatabase): io.github.hootkit_dev.hootbook.data.database.dao.LendBorrowDao {
        return database.lendBorrowDao()
    }

    @Provides
    @Singleton
    fun provideQuickTemplateDao(database: HootbookDatabase): io.github.hootkit_dev.hootbook.data.database.dao.QuickTemplateDao {
        return database.quickTemplateDao()
    }

    @Provides
    @Singleton
    fun provideSyncDao(database: HootbookDatabase): SyncDao = database.syncDao()
}
