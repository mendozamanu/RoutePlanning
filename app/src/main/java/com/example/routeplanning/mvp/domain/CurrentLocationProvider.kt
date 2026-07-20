package com.example.routeplanning.mvp.domain

fun interface CurrentLocationProvider {
    suspend fun currentCoordinate(): Coordinate
}

class CurrentLocationUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
