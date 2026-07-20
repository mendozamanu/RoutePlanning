package com.example.routeplanning.mvp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.Weekday

@Entity(tableName = "saved_commutes")
data class SavedCommuteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "origin_label") val originLabel: String,
    @ColumnInfo(name = "destination_label") val destinationLabel: String,
    @ColumnInfo(name = "origin_latitude") val originLatitude: Double?,
    @ColumnInfo(name = "origin_longitude") val originLongitude: Double?,
    @ColumnInfo(name = "destination_latitude") val destinationLatitude: Double?,
    @ColumnInfo(name = "destination_longitude") val destinationLongitude: Double?,
    @ColumnInfo(name = "departure_hour") val departureHour: Int,
    @ColumnInfo(name = "departure_minute") val departureMinute: Int,
    @ColumnInfo(name = "active_days") val activeDays: String,
    val mode: String,
    val profile: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long
) {
    fun toDomain(): SavedCommute = SavedCommute(
        id = id,
        originLabel = originLabel,
        destinationLabel = destinationLabel,
        originCoordinate = coordinateOrNull(originLatitude, originLongitude),
        destinationCoordinate = coordinateOrNull(destinationLatitude, destinationLongitude),
        departureHour = departureHour,
        departureMinute = departureMinute,
        activeDays = activeDays.split(",")
            .mapNotNull { encoded -> Weekday.entries.find { it.name == encoded } }
            .toSet()
            .ifEmpty { Weekday.workingDays },
        mode = JourneyMode.entries.find { it.name == mode } ?: JourneyMode.TRANSIT,
        profile = JourneyProfile.entries.find { it.name == profile } ?: JourneyProfile.FASTEST,
        createdAtEpochMillis = createdAtEpochMillis
    )

    companion object {
        fun fromDomain(commute: SavedCommute): SavedCommuteEntity = SavedCommuteEntity(
            id = commute.id,
            originLabel = commute.originLabel,
            destinationLabel = commute.destinationLabel,
            originLatitude = commute.originCoordinate?.latitude,
            originLongitude = commute.originCoordinate?.longitude,
            destinationLatitude = commute.destinationCoordinate?.latitude,
            destinationLongitude = commute.destinationCoordinate?.longitude,
            departureHour = commute.departureHour,
            departureMinute = commute.departureMinute,
            activeDays = commute.activeDays.sortedBy { it.ordinal }.joinToString(",") { it.name },
            mode = commute.mode.name,
            profile = commute.profile.name,
            createdAtEpochMillis = commute.createdAtEpochMillis
        )

        private fun coordinateOrNull(latitude: Double?, longitude: Double?) =
            if (latitude != null && longitude != null) {
                com.example.routeplanning.mvp.domain.Coordinate(latitude, longitude)
            } else {
                null
            }
    }
}
