package com.example.routeplanning.mvp.domain

object CordobaServiceArea {
    const val MIN_LATITUDE = 37.75
    const val MAX_LATITUDE = 38.10
    const val MIN_LONGITUDE = -5.04
    const val MAX_LONGITUDE = -4.55

    fun contains(coordinate: Coordinate): Boolean =
        coordinate.latitude in MIN_LATITUDE..MAX_LATITUDE &&
            coordinate.longitude in MIN_LONGITUDE..MAX_LONGITUDE
}
