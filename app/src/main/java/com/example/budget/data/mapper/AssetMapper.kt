package com.example.budget.data.mapper

import com.example.budget.data.local.entity.AssetEntity
import com.example.budget.domain.model.Asset

fun AssetEntity.toAsset(): Asset {
    return Asset(
        id = id,
        name = name,
        type = type,
        balance = balance,
        isPinned = isPinned
    )
}

fun Asset.toAssetEntity(): AssetEntity {
    return AssetEntity(
        id = id,
        name = name,
        type = type,
        balance = balance,
        isPinned = isPinned
    )
}
