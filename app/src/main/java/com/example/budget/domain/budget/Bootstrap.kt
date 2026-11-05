package com.example.budget.domain.budget

import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure a fresh install is usable without setup:
 *  - an income pocket ("To distribute") where income lands,
 *  - a savings pocket that receives leftovers,
 *  - a starter set of spending categories.
 * Safe to call many times; it only fills what is missing.
 */
@Singleton
class Bootstrap @Inject constructor(
    private val funds: FundCategoryRepository,
    private val settings: SettingsRepository,
    private val categories: ExpenseCategoryRepository
) {
    private val mutex = Mutex()
    @Volatile private var done = false

    suspend fun ensure() {
        if (done) return
        mutex.withLock {
            if (done) return
            val existing = funds.getAllFundCategories().first()
            val ids = existing.map { it.id }.toSet()

            if (settings.holdingFundId.value?.let { it in ids } != true) {
                val holding = existing.firstOrNull { it.name.equals(HOLDING_NAME, ignoreCase = true) }
                    ?: FundCategory(UUID.randomUUID().toString(), HOLDING_NAME, EnvelopeType.SINKING_FUND, 0.0)
                        .also { funds.insertFundCategory(it) }
                settings.setHoldingFundId(holding.id)
            }
            if (settings.savingsFundId.value?.let { it in ids } != true) {
                val savings = existing.firstOrNull { it.name.equals(SAVINGS_NAME, ignoreCase = true) }
                    ?: FundCategory(UUID.randomUUID().toString(), SAVINGS_NAME, EnvelopeType.SINKING_FUND, 0.0)
                        .also { funds.insertFundCategory(it) }
                settings.setSavingsFundId(savings.id)
            }

            if (categories.getAllExpenseCategories().first().isEmpty()) {
                DEFAULT_CATEGORIES.forEach {
                    categories.insertExpenseCategory(ExpenseCategory(UUID.randomUUID().toString(), it))
                }
            }
            done = true
        }
    }

    companion object {
        const val HOLDING_NAME = "To distribute"
        const val SAVINGS_NAME = "Savings"
        const val UNCATEGORIZED = "Uncategorized"
        val DEFAULT_CATEGORIES = listOf(
            "Food & drinks", "Groceries", "Transport", "Bills", "Phone & internet",
            "Shopping", "Health", "Education", "Family", "Giving", "Entertainment", UNCATEGORIZED
        )
    }
}
