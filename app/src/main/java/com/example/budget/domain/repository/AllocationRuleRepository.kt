package com.example.budget.domain.repository

import com.example.budget.domain.model.AllocationRule
import com.example.budget.domain.model.RuleKind
import kotlinx.coroutines.flow.Flow

interface AllocationRuleRepository {
    fun getAllRules(): Flow<List<AllocationRule>>
    suspend fun getAllRulesOnce(): List<AllocationRule>

    /** Create or replace the rule of a pocket. New pockets go to the end of the priority list. */
    suspend fun setRule(fundId: String, kind: RuleKind, amount: Double)
    suspend fun removeRule(fundId: String)
}
