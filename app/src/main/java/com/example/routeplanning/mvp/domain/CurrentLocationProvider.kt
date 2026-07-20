package com.example.routeplanning.mvp.domain

fun interface CurrentLocationProvider {
    suspend fun currentCoordinate(): Coordinate

    suspend fun addressLabel(coordinate: Coordinate): String? = null
}

class CurrentLocationUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
