package com.example.budget.data.mapper

import com.example.budget.data.local.entity.FundCategoryEntity
import com.example.budget.domain.model.FundCategory

fun FundCategoryEntity.toFundCategory(): FundCategory {
    return FundCategory(
        id = id,
        name = name,
        type = type,
        balance = balance,
        isPinned = isPinned
    )
}

fun FundCategory.toFundCategoryEntity(): FundCategoryEntity {
    return FundCategoryEntity(
        id = id,
        name = name,
        type = type,
        balance = balance,
        isPinned = isPinned
    )
}
