package com.example.routeplanning.mvp.domain

import kotlinx.coroutines.flow.Flow

interface SavedCommuteRepository {
    fun observeAll(): Flow<List<SavedCommute>>
    suspend fun findById(id: String): SavedCommute?
    suspend fun upsert(commute: SavedCommute)
    suspend fun delete(id: String)
}
