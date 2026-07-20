package com.example.routeplanning.mvp.domain

import java.util.UUID

data class SavedCommute(
    val id: String = UUID.randomUUID().toString(),
    val originLabel: String,
    val destinationLabel: String,
    val originCoordinate: Coordinate? = null,
    val destinationCoordinate: Coordinate? = null,
    val departureHour: Int,
    val departureMinute: Int,
    val activeDays: Set<Weekday> = Weekday.workingDays,
    val mode: JourneyMode = JourneyMode.TRANSIT,
    val profile: JourneyProfile = JourneyProfile.FASTEST,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    init {
        require(originLabel.isNotBlank()) { "Origin must not be blank" }
        require(destinationLabel.isNotBlank()) { "Destination must not be blank" }
        require(departureHour in 0..23) { "Departure hour must be between 0 and 23" }
        require(departureMinute in 0..59) { "Departure minute must be between 0 and 59" }
        require(activeDays.isNotEmpty()) { "At least one active day is required" }
        require((originCoordinate == null) == (destinationCoordinate == null)) {
            "Origin and destination coordinates must either both be present or both be absent"
        }
    }

    val formattedDeparture: String
        get() = "%02d:%02d".format(departureHour, departureMinute)

    val canCalculateJourney: Boolean
        get() = originCoordinate != null && destinationCoordinate != null
}

enum class JourneyProfile {
    FASTEST,
    FEWER_TRANSFERS,
    LESS_WALKING,
    ACCESSIBLE
}

enum class Weekday(val shortLabel: String) {
    MONDAY("L"),
    TUESDAY("M"),
    WEDNESDAY("X"),
    THURSDAY("J"),
    FRIDAY("V"),
    SATURDAY("S"),
    SUNDAY("D");

    companion object {
        val workingDays: Set<Weekday> = entries.take(5).toSet()
    }
}
