package com.example.routeplanning.mvp.data

import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemorySavedCommuteRepository : SavedCommuteRepository {
    private val commutes = MutableStateFlow<List<SavedCommute>>(emptyList())

    override fun observeAll(): Flow<List<SavedCommute>> = commutes.asStateFlow()

    override suspend fun findById(id: String): SavedCommute? =
        commutes.value.firstOrNull { it.id == id }

    override suspend fun upsert(commute: SavedCommute) {
        commutes.update { current ->
            current.filterNot { it.id == commute.id } + commute
        }
    }

    override suspend fun delete(id: String) {
        commutes.update { current -> current.filterNot { it.id == id } }
    }
}
