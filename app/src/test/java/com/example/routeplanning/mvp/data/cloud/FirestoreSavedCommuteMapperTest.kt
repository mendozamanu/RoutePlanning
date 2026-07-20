package com.example.routeplanning.mvp.data.cloud

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreSavedCommuteMapperTest {
    @Test
    fun `round trips a saved commute without losing route data`() {
        val commute = SavedCommute(
            id = "commute-1",
            originLabel = "Centro",
            destinationLabel = "Campus de Rabanales",
            originCoordinate = Coordinate(37.8847, -4.7792),
            destinationCoordinate = Coordinate(37.9125, -4.7208),
            departureHour = 8,
            departureMinute = 15,
            activeDays = Weekday.workingDays,
            mode = JourneyMode.TRANSIT,
            profile = JourneyProfile.FEWER_TRANSFERS,
            createdAtEpochMillis = 1_720_000_000_000
        )

        val restored = FirestoreSavedCommuteMapper.fromMap(
            commute.id,
            FirestoreSavedCommuteMapper.toMap(commute)
        )

        assertEquals(commute, restored)
    }

    @Test
    fun `rejects a document without required fields`() {
        val restored = FirestoreSavedCommuteMapper.fromMap(
            id = "invalid",
            data = mapOf("originLabel" to "Centro")
        )

        assertNull(restored)
    }
}
