package com.example.routeplanning.mvp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedCommuteTest {
    @Test
    fun formatsDepartureUsingTwentyFourHourClock() {
        val commute = SavedCommute(
            originLabel = "Casa",
            destinationLabel = "Trabajo",
            departureHour = 7,
            departureMinute = 5
        )

        assertEquals("07:05", commute.formattedDeparture)
    }

    @Test
    fun rejectsBlankOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedCommute(
                originLabel = " ",
                destinationLabel = "Trabajo",
                departureHour = 8,
                departureMinute = 0
            )
        }
    }

    @Test
    fun usesWorkingDaysByDefault() {
        val commute = SavedCommute(
            originLabel = "Casa",
            destinationLabel = "Campus",
            departureHour = 8,
            departureMinute = 0
        )

        assertEquals(Weekday.workingDays, commute.activeDays)
    }

    @Test
    fun requiresBothCoordinatesWhenOneIsProvided() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedCommute(
                originLabel = "Casa",
                destinationLabel = "Campus",
                originCoordinate = Coordinate(37.8882, -4.7794),
                departureHour = 8,
                departureMinute = 0
            )
        }
    }
}
