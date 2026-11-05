package com.example.budget.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.budget.data.local.dao.AssetDao
import com.example.budget.data.local.dao.FundCategoryDao
import com.example.budget.data.local.dao.TransactionDao
import com.example.budget.data.local.entity.AssetEntity
import com.example.budget.data.local.entity.FundCategoryEntity
import com.example.budget.data.local.entity.TransactionEntity
import com.example.budget.data.local.entity.ExpenseCategoryEntity
import com.example.budget.data.local.dao.ExpenseCategoryDao
import com.example.budget.data.local.dao.AllocationRuleDao
import com.example.budget.data.local.entity.AllocationRuleEntity
import com.example.budget.data.local.entity.PendingEntity
import com.example.budget.data.local.dao.PendingDao

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        AssetEntity::class,
        FundCategoryEntity::class,
        TransactionEntity::class,
        ExpenseCategoryEntity::class,
        AllocationRuleEntity::class,
        PendingEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val assetDao: AssetDao
    abstract val fundCategoryDao: FundCategoryDao
    abstract val transactionDao: TransactionDao
    abstract val expenseCategoryDao: ExpenseCategoryDao
    abstract val allocationRuleDao: AllocationRuleDao
    abstract val pendingDao: PendingDao
    
    companion object {
        const val DATABASE_NAME = "budget_db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE transactions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        total_amount REAL NOT NULL,
                        primary_asset_id TEXT,
                        secondary_asset_id TEXT,
                        payee TEXT NOT NULL,
                        memo TEXT NOT NULL
                    )
                """)

                db.execSQL("""
                    INSERT INTO transactions_new (id, type, timestamp, total_amount, primary_asset_id, secondary_asset_id, payee, memo)
                    SELECT 
                        id, 
                        type, 
                        timestamp, 
                        amount, 
                        CASE 
                            WHEN source_type = 'ASSET' THEN source_id 
                            WHEN destination_type = 'ASSET' THEN destination_id 
                            ELSE NULL 
                        END,
                        CASE 
                            WHEN type = 'ASSET_TRANSFER' THEN destination_id 
                            ELSE NULL 
                        END,
                        '',
                        memo
                    FROM transactions
                """)

                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                db.execSQL("""
                    CREATE TABLE transaction_splits (
                        id TEXT NOT NULL PRIMARY KEY,
                        transaction_id TEXT NOT NULL,
                        fund_category_id TEXT NOT NULL,
                        amount REAL NOT NULL,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(fund_category_id) REFERENCES fund_categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_transaction_id ON transaction_splits(transaction_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_fund_category_id ON transaction_splits(fund_category_id)")

                db.execSQL("""
                    INSERT INTO transaction_splits (id, transaction_id, fund_category_id, amount)
                    SELECT 
                        lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))),
                        id, 
                        fund_category_id, 
                        amount
                    FROM transactions 
                    WHERE fund_category_id IS NOT NULL
                """)

                db.execSQL("""
                    CREATE TABLE tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE transaction_tag_cross_ref (
                        transaction_id TEXT NOT NULL,
                        tag_id TEXT NOT NULL,
                        PRIMARY KEY(transaction_id, tag_id),
                        FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(tag_id) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_tag_cross_ref_tag_id ON transaction_tag_cross_ref(tag_id)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new flat columns
                db.execSQL("ALTER TABLE transactions ADD COLUMN primary_fund_id TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN secondary_fund_id TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN expense_category TEXT NOT NULL DEFAULT 'Uncategorized'")

                // Recover primary_fund_id from splits for INCOME, EXPENSE, ASSET_TRANSFER
                db.execSQL("""
                    UPDATE transactions 
                    SET primary_fund_id = (
                        SELECT fund_category_id FROM transaction_splits 
                        WHERE transaction_id = transactions.id LIMIT 1
                    )
                    WHERE type != 'BUDGET_ASSIGNMENT'
                """)

                // Recover primary (source) and secondary (destination) funds for BUDGET_ASSIGNMENT
                db.execSQL("""
                    UPDATE transactions 
                    SET primary_fund_id = (SELECT fund_category_id FROM transaction_splits WHERE transaction_id = transactions.id AND amount < 0 LIMIT 1),
                        secondary_fund_id = (SELECT fund_category_id FROM transaction_splits WHERE transaction_id = transactions.id AND amount > 0 LIMIT 1)
                    WHERE type = 'BUDGET_ASSIGNMENT'
                """)

                // Drop abandoned tables
                db.execSQL("DROP TABLE transaction_splits")
                db.execSQL("DROP TABLE tags")
                db.execSQL("DROP TABLE transaction_tag_cross_ref")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create expense_categories table
                db.execSQL("""
                    CREATE TABLE expense_categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                """)

                // 2. Insert predefined categories
                val predefined = listOf(
                    "Uncategorized", "Groceries", "Utilities", "Dining Out", 
                    "Transportation", "Entertainment", "Shopping", "Health"
                )
                predefined.forEach { name ->
                    val id = java.util.UUID.randomUUID().toString()
                    db.execSQL("INSERT INTO expense_categories (id, name) VALUES ('$id', '$name')")
                }

                // 3. Drop payee column from transactions by recreating the table
                db.execSQL("""
                    CREATE TABLE transactions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        total_amount REAL NOT NULL,
                        primary_asset_id TEXT,
                        secondary_asset_id TEXT,
                        primary_fund_id TEXT,
                        secondary_fund_id TEXT,
                        expense_category TEXT NOT NULL,
                        memo TEXT NOT NULL
                    )
                """)

                db.execSQL("""
                    INSERT INTO transactions_new (
                        id, type, timestamp, total_amount, 
                        primary_asset_id, secondary_asset_id, 
                        primary_fund_id, secondary_fund_id, 
                        expense_category, memo
                    )
                    SELECT 
                        id, type, timestamp, total_amount, 
                        primary_asset_id, secondary_asset_id, 
                        primary_fund_id, secondary_fund_id, 
                        expense_category, memo
                    FROM transactions
                """)

                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fund_categories ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Map old FundCategoryType values to new EnvelopeType values
                db.execSQL("UPDATE fund_categories SET type = 'MONTHLY_SPENDING' WHERE type = 'EXPENSE'")
                db.execSQL("UPDATE fund_categories SET type = 'SINKING_FUND' WHERE type = 'SAVING'")
                db.execSQL("UPDATE fund_categories SET type = 'MONTHLY_SPENDING' WHERE type = 'INCOME_HOLDING'")
            }
        }

        /** Adds pocket allocation rules. Existing data is untouched. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS allocation_rules (
                        fund_id TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        amount REAL NOT NULL,
                        sort_order INTEGER NOT NULL
                    )
                """)
            }
        }

        /** Adds the review inbox for scanned transactions. Existing data is untouched. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_transactions (
                        id TEXT NOT NULL PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        amount REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        merchant TEXT NOT NULL,
                        category TEXT NOT NULL,
                        asset_id TEXT,
                        asset2_id TEXT,
                        fund_id TEXT,
                        reference TEXT,
                        items_json TEXT NOT NULL,
                        split_items INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        origin TEXT NOT NULL
                    )
                """)
            }
        }
    }
}
