package com.example.routeplanning.mvp.domain

data class TransitStop(
    val id: String,
    val name: String,
    val coordinate: Coordinate
) {
    init {
        require(id.isNotBlank()) { "Stop id must not be blank" }
        require(name.isNotBlank()) { "Stop name must not be blank" }
    }

    val displayLabel: String
        get() = "$name · parada ${id.substringAfterLast(':')}"
}

data class StopSearchResult(
    val dataVersion: String,
    val stops: List<TransitStop>
)

interface StopRepository {
    suspend fun search(query: String, limit: Int = 8): StopSearchResult
}
