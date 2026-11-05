package com.example.budget.domain.repository

import com.example.budget.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    fun getAllAssets(): Flow<List<Asset>>
    suspend fun getAssetById(id: String): Asset?
    suspend fun insertAsset(asset: Asset)
    suspend fun updateAsset(asset: Asset)
    suspend fun deleteAsset(asset: Asset)
    fun getSumOfLiquidBalances(): Flow<Double>
    suspend fun togglePinAsset(id: String, isPinned: Boolean)
}
