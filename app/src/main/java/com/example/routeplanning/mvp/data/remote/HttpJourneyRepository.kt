package com.example.routeplanning.mvp.data.remote

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.FareNoticeCode
import com.example.routeplanning.mvp.domain.JourneyFareNotice
import com.example.routeplanning.mvp.domain.JourneyLeg
import com.example.routeplanning.mvp.domain.JourneyOption
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.JourneyQuery
import com.example.routeplanning.mvp.domain.JourneyRepository
import com.example.routeplanning.mvp.domain.JourneySearchResult
import com.example.routeplanning.mvp.domain.RealtimeStatus
import com.example.routeplanning.mvp.domain.TransportMode
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime

class JourneyApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean
) : RuntimeException(message)

class HttpJourneyRepository internal constructor(
    private val transport: JourneyApiTransport,
    private val gson: Gson = defaultGson()
) : JourneyRepository {

    override suspend fun search(query: JourneyQuery): JourneySearchResult {
        val response = try {
            transport.post(gson.toJson(query.toDto()))
        } catch (error: JourneyApiException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw JourneyApiException(
                code = "NETWORK_UNAVAILABLE",
                message = "No hay conexión con el servicio de rutas.",
                retryable = true
            ).also { it.initCause(error) }
        }
        if (response.statusCode !in 200..299) {
            val error = runCatching { gson.fromJson(response.body, ApiErrorDto::class.java) }
                .getOrNull()
            throw JourneyApiException(
                code = error?.code ?: "NETWORK_ERROR",
                message = error?.message ?: "No podemos calcular el trayecto en este momento.",
                retryable = error?.retryable ?: true
            )
        }
        return runCatching {
            gson.fromJson(response.body, JourneySearchResponseDto::class.java).toDomain()
        }.getOrElse { error ->
            throw JourneyApiException(
                code = "INVALID_RESPONSE",
                message = "El servicio de rutas ha devuelto una respuesta no válida.",
                retryable = true
            ).also { it.initCause(error) }
        }
    }

    companion object {
        fun create(baseUrl: String, allowCleartext: Boolean): HttpJourneyRepository {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            val uri = URI(normalizedBaseUrl)
            val localCleartextHost = uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost")
            require(uri.scheme == "https" || (allowCleartext && localCleartextHost)) {
                "The journey API must use HTTPS except on a local debug host"
            }
            return HttpJourneyRepository(
                transport = UrlConnectionJourneyApiTransport(
                    endpoint = URL("$normalizedBaseUrl/v1/journeys/search")
                )
            )
        }

        private fun defaultGson(): Gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }
}

internal data class JourneyApiResponse(val statusCode: Int, val body: String)

internal fun interface JourneyApiTransport {
    suspend fun post(body: String): JourneyApiResponse
}

private class UrlConnectionJourneyApiTransport(
    private val endpoint: URL
) : JourneyApiTransport {
    override suspend fun post(body: String): JourneyApiResponse = withContext(Dispatchers.IO) {
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            JourneyApiResponse(
                statusCode = statusCode,
                body = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }
}

private data class CoordinateDto(val latitude: Double, val longitude: Double)

private data class JourneySearchRequestDto(
    val origin: CoordinateDto,
    val destination: CoordinateDto,
    val departureAt: String,
    val mode: String,
    val profile: String,
    val maxWalkMeters: Int,
    val maxTransfers: Int
)

private data class JourneyLegDto(
    val mode: String,
    val startsAt: String,
    val endsAt: String,
    val fromName: String,
    val toName: String,
    val routeId: String?,
    val routeShortName: String?,
    val agencyId: String?,
    val agencyName: String?,
    val headsign: String?,
    val distanceMeters: Int?,
    val stopCount: Int?,
    val geometry: List<CoordinateDto>,
    val accessibilityConfirmed: Boolean?
)

private data class JourneyOptionDto(
    val id: String,
    val startsAt: String,
    val endsAt: String,
    val durationSeconds: Int,
    val transfers: Int,
    val walkDistanceMeters: Int,
    val legs: List<JourneyLegDto>,
    val fareNotices: List<JourneyFareNoticeDto>? = null
)

private data class JourneyFareNoticeDto(
    val code: String,
    val stopName: String,
    val routeShortName: String?,
    val additionalTicketCount: Int
)

private data class JourneySearchResponseDto(
    val dataVersion: String,
    val generatedAt: String,
    val realtimeStatus: String,
    val itineraries: List<JourneyOptionDto>
)

private data class ApiErrorDto(val code: String, val message: String, val retryable: Boolean)

private fun JourneyQuery.toDto() = JourneySearchRequestDto(
    origin = CoordinateDto(origin.latitude, origin.longitude),
    destination = CoordinateDto(destination.latitude, destination.longitude),
    departureAt = departureAt.toString(),
    mode = mode.name,
    profile = profile.name,
    maxWalkMeters = maxWalkMeters,
    maxTransfers = maxTransfers
)

private fun JourneySearchResponseDto.toDomain() = JourneySearchResult(
    dataVersion = dataVersion,
    generatedAt = OffsetDateTime.parse(generatedAt),
    realtimeStatus = RealtimeStatus.valueOf(realtimeStatus),
    itineraries = itineraries.map { option ->
        JourneyOption(
            id = option.id,
            startsAt = OffsetDateTime.parse(option.startsAt),
            endsAt = OffsetDateTime.parse(option.endsAt),
            durationSeconds = option.durationSeconds,
            transfers = option.transfers,
            walkDistanceMeters = option.walkDistanceMeters,
            legs = option.legs.map(JourneyLegDto::toDomain),
            fareNotices = option.fareNotices.orEmpty().map(JourneyFareNoticeDto::toDomain)
        )
    }
)

private fun JourneyLegDto.toDomain() = JourneyLeg(
    mode = TransportMode.valueOf(mode),
    startsAt = OffsetDateTime.parse(startsAt),
    endsAt = OffsetDateTime.parse(endsAt),
    fromName = fromName,
    toName = toName,
    routeId = routeId,
    routeShortName = routeShortName,
    agencyId = agencyId,
    agencyName = agencyName,
    headsign = headsign,
    distanceMeters = distanceMeters,
    stopCount = stopCount,
    geometry = geometry.map { Coordinate(it.latitude, it.longitude) },
    accessibilityConfirmed = accessibilityConfirmed
)

private fun JourneyFareNoticeDto.toDomain() = JourneyFareNotice(
    code = FareNoticeCode.valueOf(code),
    stopName = stopName,
    routeShortName = routeShortName,
    additionalTicketCount = additionalTicketCount
)
