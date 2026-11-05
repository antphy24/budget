package com.example.budget.data.repository

import com.example.budget.data.local.dao.AllocationRuleDao
import com.example.budget.data.local.entity.AllocationRuleEntity
import com.example.budget.domain.model.AllocationRule
import com.example.budget.domain.model.RuleKind
import com.example.budget.domain.repository.AllocationRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AllocationRuleRepositoryImpl @Inject constructor(
    private val dao: AllocationRuleDao
) : AllocationRuleRepository {

    override fun getAllRules(): Flow<List<AllocationRule>> =
        dao.getAllRules().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getAllRulesOnce(): List<AllocationRule> =
        dao.getAllRulesOnce().mapNotNull { it.toDomain() }

    override suspend fun setRule(fundId: String, kind: RuleKind, amount: Double) {
        val existing = dao.getAllRulesOnce().firstOrNull { it.fundId == fundId }
        val order = existing?.sortOrder ?: (dao.getMaxSortOrder() + 1)
        dao.upsertRule(
            AllocationRuleEntity(
                fundId = fundId,
                kind = kind.name,
                amount = amount,
                sortOrder = order
            )
        )
    }

    override suspend fun removeRule(fundId: String) {
        dao.deleteRule(fundId)
    }

    private fun AllocationRuleEntity.toDomain(): AllocationRule? {
        val k = runCatching { RuleKind.valueOf(kind) }.getOrNull() ?: return null
        return AllocationRule(fundId = fundId, kind = k, amount = amount, sortOrder = sortOrder)
    }
}
