package com.example.routeplanning.mvp.data.remote

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.StopRepository
import com.example.routeplanning.mvp.domain.StopSearchResult
import com.example.routeplanning.mvp.domain.TransitStop
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class StopApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean
) : RuntimeException(message)

class HttpStopRepository internal constructor(
    private val transport: StopApiTransport,
    private val gson: Gson = defaultGson()
) : StopRepository {
    override suspend fun search(query: String, limit: Int): StopSearchResult {
        require(query.trim().length >= 2) { "Stop search requires at least two characters" }
        val response = try {
            transport.get(query.trim(), limit)
        } catch (error: StopApiException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw StopApiException(
                code = "NETWORK_UNAVAILABLE",
                message = "No hay conexión con el catálogo de paradas.",
                retryable = true
            ).also { it.initCause(error) }
        }
        if (response.statusCode !in 200..299) {
            val error = runCatching { gson.fromJson(response.body, StopApiErrorDto::class.java) }
                .getOrNull()
            throw StopApiException(
                code = error?.code ?: "STOP_SEARCH_ERROR",
                message = error?.message ?: "No podemos consultar las paradas ahora mismo.",
                retryable = error?.retryable ?: true
            )
        }
        return runCatching {
            gson.fromJson(response.body, StopSearchResponseDto::class.java).toDomain()
        }.getOrElse { error ->
            throw StopApiException(
                code = "INVALID_RESPONSE",
                message = "El catálogo de paradas ha devuelto una respuesta no válida.",
                retryable = true
            ).also { it.initCause(error) }
        }
    }

    companion object {
        fun create(baseUrl: String, allowCleartext: Boolean): HttpStopRepository {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            val uri = URI(normalizedBaseUrl)
            val localCleartextHost = uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost")
            require(uri.scheme == "https" || (allowCleartext && localCleartextHost)) {
                "The stop API must use HTTPS except on a local debug host"
            }
            return HttpStopRepository(
                transport = UrlConnectionStopApiTransport(
                    endpoint = URL("$normalizedBaseUrl/v1/stops/search")
                )
            )
        }

        private fun defaultGson(): Gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }
}

internal fun interface StopApiTransport {
    suspend fun get(query: String, limit: Int): JourneyApiResponse
}

private class UrlConnectionStopApiTransport(
    private val endpoint: URL
) : StopApiTransport {
    override suspend fun get(query: String, limit: Int): JourneyApiResponse =
        withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val connection = URL("$endpoint?query=$encodedQuery&limit=$limit")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                val statusCode = connection.responseCode
                val responseStream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                JourneyApiResponse(
                    statusCode = statusCode,
                    body = responseStream?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        .orEmpty()
                )
            } finally {
                connection.disconnect()
            }
        }
}

private data class StopCoordinateDto(val latitude: Double, val longitude: Double)

private data class TransitStopDto(
    val id: String,
    val name: String,
    val coordinate: StopCoordinateDto
)

private data class StopSearchResponseDto(
    val dataVersion: String,
    val stops: List<TransitStopDto>
)

private data class StopApiErrorDto(val code: String, val message: String, val retryable: Boolean)

private fun StopSearchResponseDto.toDomain() = StopSearchResult(
    dataVersion = dataVersion,
    stops = stops.map { stop ->
        TransitStop(
            id = stop.id,
            name = stop.name,
            coordinate = Coordinate(
                latitude = stop.coordinate.latitude,
                longitude = stop.coordinate.longitude
            )
        )
    }
)
