package com.example.routeplanning.mvp.domain

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JourneyTest {
    @Test
    fun coordinateRejectsInvalidLatitude() {
        assertThrows(IllegalArgumentException::class.java) {
            Coordinate(latitude = 91.0, longitude = -4.7794)
        }
    }

    @Test
    fun queryRejectsUnsupportedTransferLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            JourneyQuery(
                origin = Coordinate(37.8882, -4.7794),
                destination = Coordinate(37.9138, -4.7211),
                departureAt = OffsetDateTime.parse("2026-07-20T08:00:00+02:00"),
                maxTransfers = 6
            )
        }
    }

    @Test
    fun ticketCountIsIndependentFromTransfers() {
        val startsAt = OffsetDateTime.parse("2026-07-20T18:09:00+02:00")
        val firstBus = JourneyLeg(
            mode = TransportMode.BUS,
            startsAt = startsAt,
            endsAt = startsAt.plusMinutes(6),
            fromName = "Jesús Rescatado",
            toName = "Colón Norte",
            routeId = "AUCORSA:E",
            routeShortName = "E"
        )
        val secondBus = JourneyLeg(
            mode = TransportMode.BUS,
            startsAt = startsAt.plusMinutes(6),
            endsAt = startsAt.plusMinutes(8),
            fromName = "Colón Norte",
            toName = "Gran Capitán",
            routeId = "AUCORSA:E",
            routeShortName = "E"
        )
        val sameLineContinuation = JourneyOption(
            id = "same-line",
            startsAt = startsAt,
            endsAt = startsAt.plusMinutes(8),
            durationSeconds = 480,
            transfers = 0,
            walkDistanceMeters = 0,
            legs = listOf(firstBus, secondBus),
            fareNotices = listOf(
                JourneyFareNotice(
                    code = FareNoticeCode.SAME_LINE_NEW_TICKET,
                    stopName = "Colón Norte",
                    routeShortName = "E"
                )
            )
        )
        val ordinaryTransfer = sameLineContinuation.copy(
            id = "ordinary-transfer",
            transfers = 1,
            fareNotices = emptyList()
        )

        assertEquals(2, sameLineContinuation.requiredTicketCount)
        assertEquals(0, sameLineContinuation.transfers)
        assertEquals(1, ordinaryTransfer.requiredTicketCount)
        assertEquals(1, ordinaryTransfer.transfers)
    }

    @Test
    fun busAndRailRequireSeparateOperatorTicketsInsidePublicTransport() {
        val startsAt = OffsetDateTime.parse("2026-07-20T08:00:00+02:00")
        val option = JourneyOption(
            id = "bus-rail",
            startsAt = startsAt,
            endsAt = startsAt.plusMinutes(30),
            durationSeconds = 1_800,
            transfers = 1,
            walkDistanceMeters = 350,
            legs = listOf(
                JourneyLeg(
                    mode = TransportMode.BUS,
                    startsAt = startsAt,
                    endsAt = startsAt.plusMinutes(10),
                    fromName = "Origen",
                    toName = "Córdoba-Julio Anguita",
                    agencyId = "AUCORSA:aucorsa",
                    agencyName = "AUCORSA"
                ),
                JourneyLeg(
                    mode = TransportMode.RAIL,
                    startsAt = startsAt.plusMinutes(17),
                    endsAt = startsAt.plusMinutes(24),
                    fromName = "Córdoba-Julio Anguita",
                    toName = "Campus Universitario de Rabanales",
                    agencyId = "RENFE:1071",
                    agencyName = "RENFE OPERADORA"
                )
            )
        )

        assertEquals(2, option.requiredTicketCount)
        assertEquals(1, option.transfers)
    }

    @Test
    fun legExposesItsDurationAndValidatedStopCount() {
        val startsAt = OffsetDateTime.parse("2026-07-20T18:09:00+02:00")
        val leg = JourneyLeg(
            mode = TransportMode.BUS,
            startsAt = startsAt,
            endsAt = startsAt.plusMinutes(13),
            fromName = "Andrés Barrera DC",
            toName = "Colón Norte",
            routeShortName = "E",
            headsign = "Colón Norte",
            distanceMeters = 4_200,
            stopCount = 13
        )

        assertEquals(13 * 60, leg.durationSeconds)
        assertEquals(13, leg.stopCount)
        assertThrows(IllegalArgumentException::class.java) {
            leg.copy(stopCount = 0)
        }
    }
}
