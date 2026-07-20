package com.example.routeplanning.mvp.data.local

import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSavedCommuteRepository(
    private val dao: SavedCommuteDao
) : SavedCommuteRepository {
    override fun observeAll(): Flow<List<SavedCommute>> =
        dao.observeAll().map { entities -> entities.map(SavedCommuteEntity::toDomain) }

    override suspend fun findById(id: String): SavedCommute? = dao.findById(id)?.toDomain()

    override suspend fun upsert(commute: SavedCommute) {
        dao.upsert(SavedCommuteEntity.fromDomain(commute))
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}
