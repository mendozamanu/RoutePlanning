package com.example.routeplanning.mvp.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpStopRepositoryTest {
    @Test
    fun mapsStopSearchResponseToDomain() = runBlocking {
        val repository = HttpStopRepository(
            transport = StopApiTransport { query, limit ->
                assertEquals("Rabanales", query)
                assertEquals(8, limit)
                JourneyApiResponse(
                    200,
                    """
                    {
                      "data_version": "aucorsa-test-1",
                      "stops": [{
                        "id": "1:808",
                        "name": "Campus Rabanales",
                        "coordinate": {"latitude": 37.9137919, "longitude": -4.7176528}
                      }]
                    }
                    """.trimIndent()
                )
            }
        )

        val result = repository.search("Rabanales")

        assertEquals("aucorsa-test-1", result.dataVersion)
        assertEquals("Campus Rabanales", result.stops.single().name)
        assertEquals(37.9137919, result.stops.single().coordinate.latitude, 0.0)
    }

    @Test
    fun mapsStructuredApiError() {
        val repository = HttpStopRepository(
            transport = StopApiTransport { _, _ ->
                JourneyApiResponse(
                    503,
                    """{"code":"STOP_CATALOG_UNAVAILABLE","message":"No disponible","retryable":true}"""
                )
            }
        )

        val error = assertThrows(StopApiException::class.java) {
            runBlocking { repository.search("Centro") }
        }

        assertEquals("STOP_CATALOG_UNAVAILABLE", error.code)
        assertEquals(true, error.retryable)
    }

    @Test
    fun rejectsSingleCharacterSearch() {
        val repository = HttpStopRepository(
            transport = StopApiTransport { _, _ -> error("must not be called") }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.search("R") }
        }
    }
}
