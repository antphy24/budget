package com.example.budget.data.repository

import com.example.budget.data.local.dao.AssetDao
import com.example.budget.data.mapper.toAsset
import com.example.budget.data.mapper.toAssetEntity
import com.example.budget.domain.model.Asset
import com.example.budget.domain.repository.AssetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AssetRepositoryImpl @Inject constructor(
    private val dao: AssetDao
) : AssetRepository {

    override fun getAllAssets(): Flow<List<Asset>> {
        return dao.getAllAssets().map { entities ->
            entities.map { it.toAsset() }
        }
    }

    override suspend fun getAssetById(id: String): Asset? {
        return dao.getAssetById(id)?.toAsset()
    }

    override suspend fun insertAsset(asset: Asset) {
        dao.insertAsset(asset.toAssetEntity())
    }

    override suspend fun updateAsset(asset: Asset) {
        dao.updateAsset(asset.toAssetEntity())
    }

    override suspend fun deleteAsset(asset: Asset) {
        dao.deleteAsset(asset.toAssetEntity())
    }

    override fun getSumOfLiquidBalances(): Flow<Double> {
        return dao.getSumOfLiquidAssetBalances()
    }

    override suspend fun togglePinAsset(id: String, isPinned: Boolean) {
        dao.updateIsPinned(id, isPinned)
    }
}
