package com.example.budget.di

import android.app.Application
import androidx.room.Room
import com.example.budget.data.local.AppDatabase
import com.example.budget.data.repository.AssetRepositoryImpl
import com.example.budget.data.repository.FundCategoryRepositoryImpl
import com.example.budget.data.repository.TransactionRepositoryImpl
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.data.repository.ExpenseCategoryRepositoryImpl
import com.example.budget.data.repository.SettingsRepositoryImpl
import com.example.budget.data.repository.AllocationRuleRepositoryImpl
import com.example.budget.domain.repository.AllocationRuleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
        // NOTE: fallbackToDestructiveMigration() was removed on purpose — it would
        // silently WIPE all transactions if a migration were ever missing.
        .build()
    }

    @Provides
    @Singleton
    fun provideAssetRepository(db: AppDatabase): AssetRepository {
        return AssetRepositoryImpl(db.assetDao)
    }

    @Provides
    @Singleton
    fun provideFundCategoryRepository(db: AppDatabase): FundCategoryRepository {
        return FundCategoryRepositoryImpl(db.fundCategoryDao)
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(db: AppDatabase): TransactionRepository {
        return TransactionRepositoryImpl(
            db = db,
            transactionDao = db.transactionDao,
            assetDao = db.assetDao,
            fundCategoryDao = db.fundCategoryDao
        )
    }

    @Provides
    @Singleton
    fun provideExpenseCategoryRepository(db: AppDatabase): ExpenseCategoryRepository {
        return ExpenseCategoryRepositoryImpl(
            db = db,
            expenseCategoryDao = db.expenseCategoryDao
        )
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(app: Application): SettingsRepository {
        return SettingsRepositoryImpl(app)
    }

    @Provides
    @Singleton
    fun provideAllocationRuleRepository(db: AppDatabase): AllocationRuleRepository {
        return AllocationRuleRepositoryImpl(db.allocationRuleDao)
    }
}
