package com.example.routeplanning.mvp.data.remote

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.FareNoticeCode
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyQuery
import com.example.routeplanning.mvp.domain.RealtimeStatus
import com.example.routeplanning.mvp.domain.TransportMode
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class HttpJourneyRepositoryTest {
    @Test
    fun `maps request and response without exposing OTP to Android`() = runBlocking {
        var capturedBody = ""
        val transport = JourneyApiTransport { body ->
            capturedBody = body
            JourneyApiResponse(
                statusCode = 200,
                body = SUCCESS_RESPONSE
            )
        }
        val repository = HttpJourneyRepository(transport)

        val result = repository.search(
            JourneyQuery(
                origin = Coordinate(37.8882, -4.7794),
                destination = Coordinate(37.9138, -4.7211),
                departureAt = OffsetDateTime.parse("2026-03-20T08:00:00+01:00")
            )
        )

        val request = JsonParser.parseString(capturedBody).asJsonObject
        assertEquals("2026-03-20T08:00+01:00", request["departure_at"].asString)
        assertEquals("TRANSIT", request["mode"].asString)
        assertEquals(37.8882, request["origin"].asJsonObject["latitude"].asDouble, 0.0)
        assertFalse(capturedBody.contains("otp", ignoreCase = true))
        assertEquals("aucorsa-test", result.dataVersion)
        assertEquals(RealtimeStatus.SCHEDULED_ONLY, result.realtimeStatus)
        assertEquals("E", result.itineraries.single().legs.single().routeShortName)
        assertEquals("AUCORSA:aucorsa", result.itineraries.single().legs.single().agencyId)
        assertEquals("AUCORSA", result.itineraries.single().legs.single().agencyName)
        assertEquals("Campus de Rabanales", result.itineraries.single().legs.single().headsign)
        assertEquals(7_100, result.itineraries.single().legs.single().distanceMeters)
        assertEquals(8, result.itineraries.single().legs.single().stopCount)
        assertNull(result.itineraries.single().legs.single().accessibilityConfirmed)
        assertTrue(result.itineraries.single().fareNotices.isEmpty())
    }

    @Test
    fun `maps an additional same-line ticket notice`() = runBlocking {
        val response = JsonParser.parseString(SUCCESS_RESPONSE).asJsonObject
        val option = response["itineraries"].asJsonArray.single().asJsonObject
        option.add(
            "fare_notices",
            JsonParser.parseString(
                """
                [{
                  "code": "SAME_LINE_NEW_TICKET",
                  "stop_name": "Colón Norte",
                  "route_short_name": "E",
                  "additional_ticket_count": 1
                }]
                """.trimIndent()
            )
        )
        val repository = HttpJourneyRepository(
            JourneyApiTransport { JourneyApiResponse(200, response.toString()) }
        )

        val result = repository.search(
            JourneyQuery(
                origin = Coordinate(37.8882, -4.7794),
                destination = Coordinate(37.9138, -4.7211),
                departureAt = OffsetDateTime.parse("2026-03-20T08:00:00+01:00")
            )
        )

        val notice = result.itineraries.single().fareNotices.single()
        assertEquals(FareNoticeCode.SAME_LINE_NEW_TICKET, notice.code)
        assertEquals("Colón Norte", notice.stopName)
        assertEquals("E", notice.routeShortName)
        assertEquals(2, result.itineraries.single().requiredTicketCount)
    }

    @Test
    fun `sends bicycle mode and maps bicycle legs`() = runBlocking {
        var capturedBody = ""
        val repository = HttpJourneyRepository(
            JourneyApiTransport { body ->
                capturedBody = body
                JourneyApiResponse(
                    statusCode = 200,
                    body = SUCCESS_RESPONSE.replace("\"BUS\"", "\"BICYCLE\"")
                )
            }
        )

        val result = repository.search(
            JourneyQuery(
                origin = Coordinate(37.9022, -4.7304),
                destination = Coordinate(37.8885, -4.7808),
                departureAt = OffsetDateTime.parse("2026-07-20T08:00:00+02:00"),
                mode = JourneyMode.BICYCLE
            )
        )

        val request = JsonParser.parseString(capturedBody).asJsonObject
        assertEquals("BICYCLE", request["mode"].asString)
        assertEquals(TransportMode.BICYCLE, result.itineraries.single().legs.single().mode)
    }

    @Test
    fun `sends walking mode and maps walking legs`() = runBlocking {
        var capturedBody = ""
        val repository = HttpJourneyRepository(
            JourneyApiTransport { body ->
                capturedBody = body
                JourneyApiResponse(
                    statusCode = 200,
                    body = SUCCESS_RESPONSE.replace("\"BUS\"", "\"WALK\"")
                )
            }
        )

        val result = repository.search(
            JourneyQuery(
                origin = Coordinate(37.9022, -4.7304),
                destination = Coordinate(37.8885, -4.7808),
                departureAt = OffsetDateTime.parse("2026-07-20T08:00:00+02:00"),
                mode = JourneyMode.WALK
            )
        )

        val request = JsonParser.parseString(capturedBody).asJsonObject
        assertEquals("WALK", request["mode"].asString)
        assertEquals(TransportMode.WALK, result.itineraries.single().legs.single().mode)
    }

    @Test
    fun `maps rail as a leg within public transport`() = runBlocking {
        val response = JsonParser.parseString(SUCCESS_RESPONSE).asJsonObject
        val leg = response["itineraries"].asJsonArray.single().asJsonObject
            .getAsJsonArray("legs").single().asJsonObject
        leg.addProperty("mode", "RAIL")
        leg.addProperty("agency_id", "RENFE:1071")
        leg.addProperty("agency_name", "RENFE OPERADORA")
        leg.addProperty("route_short_name", "PROXIMDAD")
        val repository = HttpJourneyRepository(
            JourneyApiTransport { JourneyApiResponse(200, response.toString()) }
        )

        val result = repository.search(
            JourneyQuery(
                origin = Coordinate(37.888291, -4.789453),
                destination = Coordinate(37.91256, -4.72086),
                departureAt = OffsetDateTime.parse("2026-07-20T08:00:00+02:00")
            )
        )

        val railLeg = result.itineraries.single().legs.single()
        assertEquals(TransportMode.RAIL, railLeg.mode)
        assertEquals("RENFE:1071", railLeg.agencyId)
        assertEquals("RENFE OPERADORA", railLeg.agencyName)
        assertEquals(1, result.itineraries.single().requiredTicketCount)
    }

    @Test
    fun `preserves structured retryable API errors`() = runBlocking {
        val repository = HttpJourneyRepository(
            JourneyApiTransport {
                JourneyApiResponse(
                    statusCode = 503,
                    body = """{"code":"DATA_NOT_READY","message":"No disponible","retryable":true}"""
                )
            }
        )

        val error = runCatching {
            repository.search(
                JourneyQuery(
                    origin = Coordinate(37.88, -4.78),
                    destination = Coordinate(37.91, -4.72),
                    departureAt = OffsetDateTime.parse("2026-03-20T08:00:00+01:00")
                )
            )
        }.exceptionOrNull() as JourneyApiException

        assertEquals("DATA_NOT_READY", error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun `rejects remote cleartext API even in debug`() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpJourneyRepository.create(
                baseUrl = "http://api.example.com",
                allowCleartext = true
            )
        }
    }

    @Test
    fun `converts transport failures to a retryable network error`() = runBlocking {
        val repository = HttpJourneyRepository(
            JourneyApiTransport { throw IllegalStateException("offline") }
        )

        val error = runCatching {
            repository.search(
                JourneyQuery(
                    origin = Coordinate(37.88, -4.78),
                    destination = Coordinate(37.91, -4.72),
                    departureAt = OffsetDateTime.parse("2026-03-20T08:00:00+01:00")
                )
            )
        }.exceptionOrNull() as JourneyApiException

        assertEquals("NETWORK_UNAVAILABLE", error.code)
        assertTrue(error.retryable)
    }

    private companion object {
        val SUCCESS_RESPONSE = """
            {
              "data_version": "aucorsa-test",
              "generated_at": "2026-03-20T07:59:00Z",
              "realtime_status": "SCHEDULED_ONLY",
              "itineraries": [{
                "id": "option-1",
                "starts_at": "2026-03-20T08:00:00+01:00",
                "ends_at": "2026-03-20T08:30:00+01:00",
                "duration_seconds": 1800,
                "transfers": 0,
                "walk_distance_meters": 420,
                "legs": [{
                  "mode": "BUS",
                  "starts_at": "2026-03-20T08:05:00+01:00",
                  "ends_at": "2026-03-20T08:30:00+01:00",
                  "from_name": "Colón Norte",
                  "to_name": "Campus de Rabanales",
                  "route_id": "AUCORSA:E",
                  "route_short_name": "E",
                  "agency_id": "AUCORSA:aucorsa",
                  "agency_name": "AUCORSA",
                  "headsign": "Campus de Rabanales",
                  "distance_meters": 7100,
                  "stop_count": 8,
                  "geometry": [],
                  "accessibility_confirmed": null
                }]
              }]
            }
        """.trimIndent()
    }
}
