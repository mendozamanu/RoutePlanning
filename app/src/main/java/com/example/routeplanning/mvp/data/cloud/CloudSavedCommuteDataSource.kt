package com.example.routeplanning.mvp.data.cloud

import com.example.routeplanning.mvp.domain.SavedCommute
import kotlinx.coroutines.flow.Flow

sealed interface CloudSavedCommuteChange {
    data class Upsert(val commute: SavedCommute) : CloudSavedCommuteChange
    data class Delete(val id: String) : CloudSavedCommuteChange
}

interface CloudSavedCommuteDataSource {
    suspend fun authenticate(): String
    suspend fun fetchAll(userId: String): List<SavedCommute>
    fun observeChanges(userId: String): Flow<CloudSavedCommuteChange>
    suspend fun upsert(userId: String, commute: SavedCommute)
    suspend fun delete(userId: String, id: String)
}
