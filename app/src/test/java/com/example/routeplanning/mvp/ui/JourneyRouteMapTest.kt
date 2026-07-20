package com.example.routeplanning.mvp.ui

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.JourneyLeg
import com.example.routeplanning.mvp.domain.JourneyOption
import com.example.routeplanning.mvp.domain.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class JourneyRouteMapTest {
    @Test
    fun mapSegmentsPreserveDrawableLegModesAndGeometry() {
        val walkStart = Coordinate(37.90, -4.73)
        val transfer = Coordinate(37.89, -4.75)
        val destination = Coordinate(37.88, -4.78)
        val option = option(
            legs = listOf(
                leg(TransportMode.WALK, listOf(walkStart, transfer)),
                leg(TransportMode.BUS, emptyList()),
                leg(TransportMode.RAIL, listOf(walkStart, transfer)),
                leg(TransportMode.BICYCLE, listOf(transfer, destination))
            )
        )

        val segments = option.mapSegments()

        assertEquals(
            listOf(TransportMode.WALK, TransportMode.RAIL, TransportMode.BICYCLE),
            segments.map { it.mode }
        )
        assertEquals(listOf(transfer, destination), segments.last().points)
    }

    @Test
    fun mapPointsAlwaysIncludeSelectedAddresses() {
        val origin = Coordinate(37.9022, -4.7304)
        val routePoint = Coordinate(37.8950, -4.7500)
        val destination = Coordinate(37.8885, -4.7808)
        val option = option(legs = listOf(leg(TransportMode.BICYCLE, listOf(routePoint))))

        val points = option.mapPoints(origin, destination)

        assertEquals(origin, points.first())
        assertEquals(destination, points.last())
        assertEquals(true, routePoint in points)
    }

    private fun option(legs: List<JourneyLeg>) = JourneyOption(
        id = "option",
        startsAt = time,
        endsAt = time.plusMinutes(20),
        durationSeconds = 1_200,
        transfers = 0,
        walkDistanceMeters = 0,
        legs = legs
    )

    private fun leg(mode: TransportMode, geometry: List<Coordinate>) = JourneyLeg(
        mode = mode,
        startsAt = time,
        endsAt = time.plusMinutes(5),
        fromName = "Origen",
        toName = "Destino",
        geometry = geometry
    )

    private companion object {
        val time: OffsetDateTime = OffsetDateTime.parse("2026-07-20T08:00:00+02:00")
    }
}
