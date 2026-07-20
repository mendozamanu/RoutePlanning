package com.example.routeplanning.mvp.data.local

import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedCommuteEntityTest {
    @Test
    fun roundTripPreservesDomainValues() {
        val original = SavedCommute(
            id = "commute-1",
            originLabel = "Casa",
            destinationLabel = "Rabanales",
            originCoordinate = Coordinate(37.8882, -4.7794),
            destinationCoordinate = Coordinate(37.9138, -4.7211),
            departureHour = 7,
            departureMinute = 35,
            activeDays = setOf(Weekday.MONDAY, Weekday.WEDNESDAY),
            mode = JourneyMode.BICYCLE,
            profile = JourneyProfile.LESS_WALKING,
            createdAtEpochMillis = 1234L
        )

        val restored = SavedCommuteEntity.fromDomain(original).toDomain()

        assertEquals(original, restored)
    }
}
