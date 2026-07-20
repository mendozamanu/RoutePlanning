package com.example.routeplanning.mvp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.routeplanning.mvp.data.remote.JourneyApiException
import com.example.routeplanning.mvp.domain.AddressPlace
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.CurrentLocationUnavailableException
import com.example.routeplanning.mvp.domain.CordobaServiceArea
import com.example.routeplanning.mvp.domain.JourneyQuery
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.JourneyRepository
import com.example.routeplanning.mvp.domain.JourneySearchResult
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import com.example.routeplanning.mvp.domain.Weekday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class DepartureChoice {
    NOW,
    SCHEDULED
}

enum class JourneySearchErrorKind {
    ADDRESS_SELECTION,
    MISSING_ADDRESSES,
    OUTSIDE_SERVICE_AREA,
    INVALID_DEPARTURE,
    LOCATION_PERMISSION,
    LOCATION_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    SAVED_COMMUTE,
    UNKNOWN
}

enum class JourneySearchErrorMessage {
    ADDRESS_NO_SELECTION,
    PLACES_NOT_CONFIGURED,
    ADDRESS_COORDINATES_UNAVAILABLE,
    ADDRESS_DETAILS_UNAVAILABLE,
    ADDRESS_SEARCH_FAILED,
    MAPS_API_MISSING,
    CURRENT_LOCATION_UNAVAILABLE,
    LOCATION_PERMISSION_DENIED,
    SERVICE_UNAVAILABLE,
    MISSING_ADDRESSES,
    CURRENT_LOCATION_OUTSIDE_AREA,
    ORIGIN_OUTSIDE_AREA,
    DESTINATION_OUTSIDE_AREA,
    ROUTE_OUTSIDE_AREA,
    INVALID_DEPARTURE,
    SAVED_COMMUTE_UNAVAILABLE,
    SAVED_COMMUTE_MISSING_COORDINATES
}

data class ModeRecommendation(
    val mode: JourneyMode,
    val durationMinutes: Int,
    val minutesSaved: Int,
    val result: JourneySearchResult
)

data class JourneySearchState(
    val origin: AddressPlace? = null,
    val destination: AddressPlace? = null,
    val departureDate: String,
    val departureTime: String,
    val departureChoice: DepartureChoice = DepartureChoice.NOW,
    val mode: JourneyMode = JourneyMode.TRANSIT,
    val profile: JourneyProfile = JourneyProfile.FASTEST,
    val isSearching: Boolean = false,
    val isComparingModes: Boolean = false,
    val modeRecommendations: List<ModeRecommendation> = emptyList(),
    val isLocating: Boolean = false,
    val result: JourneySearchResult? = null,
    val searchedDepartureAt: OffsetDateTime? = null,
    val errorMessage: JourneySearchErrorMessage? = null,
    val errorKind: JourneySearchErrorKind? = null,
    val errorRetryable: Boolean = false,
    val saved: Boolean = false
)

class JourneySearchViewModel(
    private val journeyRepository: JourneyRepository,
    private val savedCommuteRepository: SavedCommuteRepository,
    private val currentLocationProvider: CurrentLocationProvider,
    private val clock: Clock = Clock.system(CORDOBA_ZONE),
    savedCommuteId: String? = null
) : ViewModel() {
    private val initialDeparture = currentCordobaDateTime()
    private val mutableState = MutableStateFlow(
        JourneySearchState(
            departureDate = initialDeparture.toLocalDate().toString(),
            departureTime = initialDeparture.toLocalTime().format(TIME_FORMATTER)
        )
    )
    val state: StateFlow<JourneySearchState> = mutableState.asStateFlow()
    private var locationJob: Job? = null

    init {
        savedCommuteId?.let(::openSavedCommute)
    }

    fun selectOrigin(place: AddressPlace) = mutableState.update {
        locationJob?.cancel()
        val serviceAreaError = serviceAreaErrorMessage(place, it.destination)
        it.copy(
            origin = place,
            isLocating = false,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = serviceAreaError,
            errorKind = serviceAreaError?.let { JourneySearchErrorKind.OUTSIDE_SERVICE_AREA },
            errorRetryable = false,
            saved = false
        )
    }

    fun selectDestination(place: AddressPlace) = mutableState.update {
        val serviceAreaError = serviceAreaErrorMessage(it.origin, place)
        it.copy(
            destination = place,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = serviceAreaError,
            errorKind = serviceAreaError?.let { JourneySearchErrorKind.OUTSIDE_SERVICE_AREA },
            errorRetryable = false,
            saved = false
        )
    }

    fun updateDepartureDate(value: String) = mutableState.update {
        it.copy(
            departureDate = value,
            departureChoice = DepartureChoice.SCHEDULED,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = null,
            errorKind = null,
            errorRetryable = false,
            saved = false
        )
    }

    fun updateDepartureTime(value: String) = mutableState.update {
        it.copy(
            departureTime = value,
            departureChoice = DepartureChoice.SCHEDULED,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = null,
            errorKind = null,
            errorRetryable = false,
            saved = false
        )
    }

    fun selectDepartureNow() {
        val now = currentCordobaDateTime()
        mutableState.update {
            it.copy(
                departureDate = now.toLocalDate().toString(),
                departureTime = now.toLocalTime().format(TIME_FORMATTER),
                departureChoice = DepartureChoice.NOW,
                result = null,
                isComparingModes = false,
                modeRecommendations = emptyList(),
                errorMessage = null,
                errorKind = null,
                errorRetryable = false,
                saved = false
            )
        }
    }

    fun scheduleDeparture() = mutableState.update {
        it.copy(
            departureChoice = DepartureChoice.SCHEDULED,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = null,
            errorKind = null,
            errorRetryable = false,
            saved = false
        )
    }

    fun useCurrentLocation(currentLocationLabel: String = "My location") {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isLocating = true,
                    result = null,
                    isComparingModes = false,
                    modeRecommendations = emptyList(),
                    errorMessage = null,
                    errorKind = null,
                    errorRetryable = false,
                    saved = false
                )
            }
            try {
                val coordinate = currentLocationProvider.currentCoordinate()
                val resolvedLabel = try {
                    currentLocationProvider.addressLabel(coordinate)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                mutableState.update {
                    val currentLocation = AddressPlace(
                        id = CURRENT_LOCATION_PLACE_ID,
                        label = resolvedLabel ?: currentLocationLabel,
                        coordinate = coordinate
                    )
                    val serviceAreaError = serviceAreaErrorMessage(
                        currentLocation,
                        it.destination
                    )
                    it.copy(
                        origin = currentLocation,
                        isLocating = false,
                        errorMessage = serviceAreaError,
                        errorKind = if (serviceAreaError != null) {
                            JourneySearchErrorKind.OUTSIDE_SERVICE_AREA
                        } else {
                            null
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: CurrentLocationUnavailableException) {
                mutableState.update {
                    it.copy(
                        isLocating = false,
                        errorMessage = JourneySearchErrorMessage.CURRENT_LOCATION_UNAVAILABLE,
                        errorKind = JourneySearchErrorKind.LOCATION_UNAVAILABLE,
                        errorRetryable = true
                    )
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isLocating = false,
                        errorMessage = JourneySearchErrorMessage.CURRENT_LOCATION_UNAVAILABLE,
                        errorKind = JourneySearchErrorKind.LOCATION_UNAVAILABLE,
                        errorRetryable = true
                    )
                }
            }
        }
    }

    fun selectMode(mode: JourneyMode) = mutableState.update {
        it.copy(
            mode = mode,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = null,
            errorKind = null,
            errorRetryable = false,
            saved = false
        )
    }

    fun showRecommendedMode(mode: JourneyMode) = mutableState.update { state ->
        val recommendation = state.modeRecommendations.firstOrNull { it.mode == mode }
            ?: return@update state
        state.copy(
            mode = mode,
            result = recommendation.result,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = null,
            errorKind = null,
            errorRetryable = false,
            saved = false
        )
    }

    fun swapLocations() = mutableState.update {
        locationJob?.cancel()
        val serviceAreaError = serviceAreaErrorMessage(it.destination, it.origin)
        it.copy(
            origin = it.destination,
            destination = it.origin,
            isLocating = false,
            result = null,
            isComparingModes = false,
            modeRecommendations = emptyList(),
            errorMessage = serviceAreaError,
            errorKind = serviceAreaError?.let { JourneySearchErrorKind.OUTSIDE_SERVICE_AREA },
            errorRetryable = false,
            saved = false
        )
    }

    fun showAddressError(message: JourneySearchErrorMessage) = mutableState.update {
        it.copy(
            errorMessage = message,
            errorKind = JourneySearchErrorKind.ADDRESS_SELECTION,
            errorRetryable = false
        )
    }

    fun showLocationPermissionDenied() = mutableState.update {
        it.copy(
            errorMessage = JourneySearchErrorMessage.LOCATION_PERMISSION_DENIED,
            errorKind = JourneySearchErrorKind.LOCATION_PERMISSION,
            errorRetryable = false
        )
    }

    fun search() {
        val current = mutableState.value
        val query = buildQuery(current) ?: return
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isSearching = true,
                    isComparingModes = query.mode == JourneyMode.TRANSIT,
                    modeRecommendations = emptyList(),
                    result = null,
                    searchedDepartureAt = query.departureAt,
                    errorMessage = null,
                    errorKind = null,
                    errorRetryable = false,
                    saved = false
                )
            }
            try {
                if (query.mode == JourneyMode.TRANSIT) {
                    searchTransitWithAlternatives(query)
                } else {
                    val result = journeyRepository.search(query)
                    mutableState.update {
                        it.copy(
                            isSearching = false,
                            isComparingModes = false,
                            result = result
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: JourneyApiException) {
                runCatching { Log.w(TAG, "Route search failed (${error.code})", error) }
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        isComparingModes = false,
                        errorMessage = if (error.code == "OUTSIDE_SERVICE_AREA") {
                            JourneySearchErrorMessage.ROUTE_OUTSIDE_AREA
                        } else {
                            JourneySearchErrorMessage.SERVICE_UNAVAILABLE
                        },
                        errorKind = if (error.code == "OUTSIDE_SERVICE_AREA") {
                            JourneySearchErrorKind.OUTSIDE_SERVICE_AREA
                        } else {
                            JourneySearchErrorKind.SERVICE_UNAVAILABLE
                        },
                        errorRetryable = error.retryable
                    )
                }
            } catch (error: Exception) {
                runCatching { Log.w(TAG, "Unexpected route search failure", error) }
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        isComparingModes = false,
                        errorMessage = JourneySearchErrorMessage.SERVICE_UNAVAILABLE,
                        errorKind = JourneySearchErrorKind.UNKNOWN,
                        errorRetryable = true
                    )
                }
            }
        }
    }

    private suspend fun searchTransitWithAlternatives(query: JourneyQuery) = coroutineScope {
        val bicycle = async {
            searchAlternativeOrNull(query.copy(mode = JourneyMode.BICYCLE))
        }
        val walk = async {
            searchAlternativeOrNull(query.copy(mode = JourneyMode.WALK))
        }
        val transitResult = journeyRepository.search(query)
        mutableState.update {
            it.copy(isSearching = false, result = transitResult)
        }
        val recommendations = buildRecommendations(
            transitResult = transitResult,
            alternatives = listOf(
                JourneyMode.BICYCLE to bicycle.await(),
                JourneyMode.WALK to walk.await()
            )
        )
        mutableState.update { state ->
            if (state.mode == JourneyMode.TRANSIT && state.result == transitResult) {
                state.copy(
                    isComparingModes = false,
                    modeRecommendations = recommendations
                )
            } else {
                state
            }
        }
    }

    private suspend fun searchAlternativeOrNull(query: JourneyQuery): JourneySearchResult? = try {
        journeyRepository.search(query)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun buildRecommendations(
        transitResult: JourneySearchResult,
        alternatives: List<Pair<JourneyMode, JourneySearchResult?>>
    ): List<ModeRecommendation> {
        val transitMinutes = transitResult.fastestDurationMinutes() ?: return emptyList()
        return alternatives.mapNotNull { (mode, result) ->
            val alternativeResult = result ?: return@mapNotNull null
            val alternativeMinutes = alternativeResult.fastestDurationMinutes()
                ?: return@mapNotNull null
            if (alternativeMinutes >= transitMinutes) return@mapNotNull null
            ModeRecommendation(
                mode = mode,
                durationMinutes = alternativeMinutes,
                minutesSaved = transitMinutes - alternativeMinutes,
                result = alternativeResult
            )
        }.sortedByDescending(ModeRecommendation::minutesSaved)
    }

    private fun JourneySearchResult.fastestDurationMinutes(): Int? = itineraries
        .minOfOrNull { (it.durationSeconds + 59) / 60 }

    fun save() {
        val current = mutableState.value
        val origin = current.origin ?: return
        val destination = current.destination ?: return
        val departureDate: LocalDate
        val departureTime: LocalTime
        if (current.departureChoice == DepartureChoice.NOW) {
            val departure = currentCordobaDateTime()
            departureDate = departure.toLocalDate()
            departureTime = departure.toLocalTime()
        } else {
            departureDate = parseDepartureDate(current.departureDate) ?: return
            departureTime = parseDepartureTime(current.departureTime) ?: return
        }
        val activeDays = if (
            Weekday.valueOf(departureDate.dayOfWeek.name) in Weekday.workingDays
        ) {
            Weekday.workingDays
        } else {
            Weekday.weekendDays
        }
        viewModelScope.launch {
            savedCommuteRepository.upsert(
                SavedCommute(
                    originLabel = origin.label,
                    destinationLabel = destination.label,
                    originCoordinate = origin.coordinate,
                    destinationCoordinate = destination.coordinate,
                    departureHour = departureTime.hour,
                    departureMinute = departureTime.minute,
                    activeDays = activeDays,
                    mode = current.mode,
                    profile = current.profile
                )
            )
            mutableState.update { it.copy(saved = true) }
        }
    }

    private fun buildQuery(state: JourneySearchState): JourneyQuery? {
        val origin = state.origin
        val destination = state.destination
        if (origin == null || destination == null) {
            mutableState.update {
                it.copy(
                    errorMessage = JourneySearchErrorMessage.MISSING_ADDRESSES,
                    errorKind = JourneySearchErrorKind.MISSING_ADDRESSES,
                    errorRetryable = false
                )
            }
            return null
        }
        if (!CordobaServiceArea.contains(origin.coordinate)) {
            mutableState.update {
                it.copy(
                    errorMessage = if (origin.id == CURRENT_LOCATION_PLACE_ID) {
                        JourneySearchErrorMessage.CURRENT_LOCATION_OUTSIDE_AREA
                    } else {
                        JourneySearchErrorMessage.ORIGIN_OUTSIDE_AREA
                    },
                    errorKind = JourneySearchErrorKind.OUTSIDE_SERVICE_AREA,
                    errorRetryable = false
                )
            }
            return null
        }
        if (!CordobaServiceArea.contains(destination.coordinate)) {
            mutableState.update {
                it.copy(
                    errorMessage = JourneySearchErrorMessage.DESTINATION_OUTSIDE_AREA,
                    errorKind = JourneySearchErrorKind.OUTSIDE_SERVICE_AREA,
                    errorRetryable = false
                )
            }
            return null
        }
        val departure = if (state.departureChoice == DepartureChoice.NOW) {
            currentCordobaDateTime()
        } else {
            try {
                val date = LocalDate.parse(state.departureDate)
                val time = LocalTime.parse(state.departureTime)
                date.atTime(time).atZone(CORDOBA_ZONE).toOffsetDateTime()
            } catch (_: DateTimeParseException) {
                mutableState.update {
                    it.copy(
                        errorMessage = JourneySearchErrorMessage.INVALID_DEPARTURE,
                        errorKind = JourneySearchErrorKind.INVALID_DEPARTURE,
                        errorRetryable = false
                    )
                }
                return null
            }
        }
        return JourneyQuery(
            origin = origin.coordinate,
            destination = destination.coordinate,
            departureAt = departure,
            mode = state.mode,
            profile = state.profile
        )
    }

    private fun openSavedCommute(id: String) {
        viewModelScope.launch {
            val commute = savedCommuteRepository.findById(id)
            if (commute == null) {
                mutableState.update {
                    it.copy(
                        errorMessage = JourneySearchErrorMessage.SAVED_COMMUTE_UNAVAILABLE,
                        errorKind = JourneySearchErrorKind.SAVED_COMMUTE,
                        errorRetryable = false
                    )
                }
                return@launch
            }
            val originCoordinate = commute.originCoordinate
            val destinationCoordinate = commute.destinationCoordinate
            if (originCoordinate == null || destinationCoordinate == null) {
                mutableState.update {
                    it.copy(
                        errorMessage = JourneySearchErrorMessage.SAVED_COMMUTE_MISSING_COORDINATES,
                        errorKind = JourneySearchErrorKind.SAVED_COMMUTE,
                        errorRetryable = false
                    )
                }
                return@launch
            }
            mutableState.update {
                it.copy(
                    origin = AddressPlace(
                        id = "saved:${commute.id}:origin",
                        label = commute.originLabel,
                        coordinate = originCoordinate
                    ),
                    destination = AddressPlace(
                        id = "saved:${commute.id}:destination",
                        label = commute.destinationLabel,
                        coordinate = destinationCoordinate
                    ),
                    departureDate = nextActiveDate(commute).toString(),
                    departureTime = commute.formattedDeparture,
                    departureChoice = DepartureChoice.SCHEDULED,
                    mode = commute.mode,
                    profile = commute.profile,
                    result = null,
                    isComparingModes = false,
                    modeRecommendations = emptyList(),
                    errorMessage = null,
                    errorKind = null,
                    errorRetryable = false,
                    saved = false
                )
            }
            search()
        }
    }

    private fun nextActiveDate(commute: SavedCommute): LocalDate {
        var date = LocalDate.now(clock)
        val departureTime = LocalTime.of(commute.departureHour, commute.departureMinute)
        val todayIsActive = commute.activeDays.any { it.name == date.dayOfWeek.name }
        if (todayIsActive && departureTime.isBefore(LocalTime.now(clock))) {
            date = date.plusDays(1)
        }
        while (commute.activeDays.none { it.name == date.dayOfWeek.name }) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun parseDepartureTime(value: String): LocalTime? = try {
        LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseDepartureDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun currentCordobaDateTime(): OffsetDateTime = clock.instant()
        .atZone(CORDOBA_ZONE)
        .toOffsetDateTime()

    private fun serviceAreaErrorMessage(
        origin: AddressPlace?,
        destination: AddressPlace?
    ): JourneySearchErrorMessage? = when {
        origin != null && !CordobaServiceArea.contains(origin.coordinate) -> {
            if (origin.id == CURRENT_LOCATION_PLACE_ID) {
                JourneySearchErrorMessage.CURRENT_LOCATION_OUTSIDE_AREA
            } else {
                JourneySearchErrorMessage.ORIGIN_OUTSIDE_AREA
            }
        }
        destination != null && !CordobaServiceArea.contains(destination.coordinate) ->
            JourneySearchErrorMessage.DESTINATION_OUTSIDE_AREA
        else -> null
    }

    companion object {
        val CORDOBA_ZONE: ZoneId = ZoneId.of("Europe/Madrid")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val CURRENT_LOCATION_PLACE_ID = "device:current-location"
        private const val TAG = "JourneySearch"

        fun factory(
            journeyRepository: JourneyRepository,
            savedCommuteRepository: SavedCommuteRepository,
            currentLocationProvider: CurrentLocationProvider,
            savedCommuteId: String? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            JourneySearchViewModel(
                journeyRepository = journeyRepository,
                savedCommuteRepository = savedCommuteRepository,
                currentLocationProvider = currentLocationProvider,
                savedCommuteId = savedCommuteId
            )
        }
    }
}
