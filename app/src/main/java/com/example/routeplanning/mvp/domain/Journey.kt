package com.example.routeplanning.mvp.domain

import java.time.OffsetDateTime

data class Coordinate(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}

data class JourneyQuery(
    val origin: Coordinate,
    val destination: Coordinate,
    val departureAt: OffsetDateTime,
    val mode: JourneyMode = JourneyMode.TRANSIT,
    val profile: JourneyProfile = JourneyProfile.FASTEST,
    val maxWalkMeters: Int = 1_500,
    val maxTransfers: Int = 3
) {
    init {
        require(maxWalkMeters in 0..10_000) { "Maximum walk must be between 0 and 10000 m" }
        require(maxTransfers in 0..5) { "Maximum transfers must be between 0 and 5" }
    }
}

data class JourneySearchResult(
    val dataVersion: String,
    val generatedAt: OffsetDateTime,
    val realtimeStatus: RealtimeStatus,
    val itineraries: List<JourneyOption>
)

data class JourneyOption(
    val id: String,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val durationSeconds: Int,
    val transfers: Int,
    val walkDistanceMeters: Int,
    val legs: List<JourneyLeg>,
    val fareNotices: List<JourneyFareNotice> = emptyList()
) {
    val requiredTicketCount: Int
        get() = legs.asSequence()
            .filter { it.mode == TransportMode.BUS || it.mode == TransportMode.RAIL }
            .map { leg ->
                leg.agencyId?.lowercase()
                    ?: leg.agencyName?.lowercase()
                    ?: leg.mode.name
            }
            .distinct()
            .count() + fareNotices.sumOf(JourneyFareNotice::additionalTicketCount)

    init {
        require(id.isNotBlank()) { "Journey id must not be blank" }
        require(durationSeconds >= 0)
        require(transfers >= 0)
        require(walkDistanceMeters >= 0)
        require(legs.isNotEmpty()) { "A journey must contain at least one leg" }
    }
}

data class JourneyLeg(
    val mode: TransportMode,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val fromName: String,
    val toName: String,
    val routeId: String? = null,
    val routeShortName: String? = null,
    val agencyId: String? = null,
    val agencyName: String? = null,
    val headsign: String? = null,
    val distanceMeters: Int? = null,
    val stopCount: Int? = null,
    val geometry: List<Coordinate> = emptyList(),
    val accessibilityConfirmed: Boolean? = null
) {
    val durationSeconds: Int
        get() = java.time.Duration.between(startsAt, endsAt).seconds
            .coerceAtLeast(0)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    init {
        require(distanceMeters == null || distanceMeters >= 0)
        require(stopCount == null || stopCount >= 1)
    }
}

data class JourneyFareNotice(
    val code: FareNoticeCode,
    val stopName: String,
    val routeShortName: String? = null,
    val additionalTicketCount: Int = 1
) {
    init {
        require(stopName.isNotBlank()) { "Fare notice stop must not be blank" }
        require(additionalTicketCount >= 1)
    }
}

enum class FareNoticeCode {
    SAME_LINE_NEW_TICKET
}

enum class RealtimeStatus {
    SCHEDULED_ONLY,
    REALTIME
}

enum class TransportMode {
    WALK,
    BUS,
    RAIL,
    BICYCLE
}

enum class JourneyMode {
    TRANSIT,
    BICYCLE,
    WALK
}

interface JourneyRepository {
    suspend fun search(query: JourneyQuery): JourneySearchResult
}
