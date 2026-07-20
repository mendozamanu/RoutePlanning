package com.example.routeplanning.mvp.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCommuteDao {
    @Query("SELECT * FROM saved_commutes ORDER BY created_at_epoch_millis DESC")
    fun observeAll(): Flow<List<SavedCommuteEntity>>

    @Query("SELECT * FROM saved_commutes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SavedCommuteEntity?

    @Upsert
    suspend fun upsert(commute: SavedCommuteEntity)

    @Query("DELETE FROM saved_commutes WHERE id = :id")
    suspend fun delete(id: String)
}
