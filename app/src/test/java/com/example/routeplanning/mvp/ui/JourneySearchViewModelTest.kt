package com.example.routeplanning.mvp.ui

import com.example.routeplanning.mvp.data.InMemorySavedCommuteRepository
import com.example.routeplanning.mvp.data.remote.JourneyApiException
import com.example.routeplanning.mvp.domain.AddressPlace
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.CurrentLocationUnavailableException
import com.example.routeplanning.mvp.domain.JourneyQuery
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyLeg
import com.example.routeplanning.mvp.domain.JourneyOption
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.JourneyRepository
import com.example.routeplanning.mvp.domain.JourneySearchResult
import com.example.routeplanning.mvp.domain.RealtimeStatus
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.TransportMode
import com.example.routeplanning.mvp.domain.Weekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class JourneySearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun searchUsesSelectedAddressCoordinatesAndCordobaTimezone() = runTest {
        val journeyRepository = RecordingJourneyRepository()
        val viewModel = viewModel(journeyRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.updateDepartureDate("2026-07-20")
        viewModel.updateDepartureTime("08:15")
        viewModel.selectMode(JourneyMode.BICYCLE)

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val query = requireNotNull(journeyRepository.query)
        assertEquals(origin.coordinate, query.origin)
        assertEquals(destination.coordinate, query.destination)
        assertEquals("2026-07-20T08:15+02:00", query.departureAt.toString())
        assertEquals(JourneyMode.BICYCLE, query.mode)
        assertEquals(DepartureChoice.SCHEDULED, viewModel.state.value.departureChoice)
        assertNotNull(viewModel.state.value.result)
    }

    @Test
    fun searchNowUsesTheCurrentClockInstant() = runTest {
        val journeyRepository = RecordingJourneyRepository()
        val viewModel = viewModel(journeyRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.selectMode(JourneyMode.BICYCLE)

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val query = requireNotNull(journeyRepository.query)
        assertEquals("2026-07-19T12:00+02:00", query.departureAt.toString())
        assertEquals(DepartureChoice.NOW, viewModel.state.value.departureChoice)
    }

    @Test
    fun currentLocationBecomesTheSelectedOrigin() = runTest {
        val coordinate = Coordinate(37.8845, -4.7796)
        val viewModel = viewModel(
            journeyRepository = RecordingJourneyRepository(),
            currentLocationProvider = CurrentLocationProvider { coordinate }
        )

        viewModel.useCurrentLocation()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("My location", viewModel.state.value.origin?.label)
        assertEquals(coordinate, viewModel.state.value.origin?.coordinate)
        assertFalse(viewModel.state.value.isLocating)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun unavailableCurrentLocationShowsAnActionableError() = runTest {
        val message = "Activa la ubicación del dispositivo."
        val viewModel = viewModel(
            journeyRepository = RecordingJourneyRepository(),
            currentLocationProvider = CurrentLocationProvider {
                throw CurrentLocationUnavailableException(message)
            }
        )

        viewModel.useCurrentLocation()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            JourneySearchErrorMessage.CURRENT_LOCATION_UNAVAILABLE,
            viewModel.state.value.errorMessage
        )
        assertEquals(
            JourneySearchErrorKind.LOCATION_UNAVAILABLE,
            viewModel.state.value.errorKind
        )
        assertTrue(viewModel.state.value.errorRetryable)
        assertEquals(null, viewModel.state.value.origin)
        assertFalse(viewModel.state.value.isLocating)
    }

    @Test
    fun deniedLocationPermissionSuggestsManualAddressSelection() {
        val viewModel = viewModel(RecordingJourneyRepository())

        viewModel.showLocationPermissionDenied()

        assertEquals(
            JourneySearchErrorKind.LOCATION_PERMISSION,
            viewModel.state.value.errorKind
        )
        assertFalse(viewModel.state.value.errorRetryable)
        assertEquals(
            JourneySearchErrorMessage.LOCATION_PERMISSION_DENIED,
            viewModel.state.value.errorMessage
        )
    }

    @Test
    fun addressOutsideCordobaIsRejectedBeforeCallingTheBackend() {
        val journeyRepository = RecordingJourneyRepository()
        val viewModel = viewModel(journeyRepository)
        viewModel.selectOrigin(
            AddressPlace(
                id = "madrid",
                label = "Madrid",
                coordinate = Coordinate(40.4168, -3.7038)
            )
        )
        viewModel.selectDestination(destination)

        viewModel.search()

        assertEquals(JourneySearchErrorKind.OUTSIDE_SERVICE_AREA, viewModel.state.value.errorKind)
        assertEquals(null, journeyRepository.query)
    }

    @Test
    fun backendDisconnectionProducesARetryableServiceError() = runTest {
        val viewModel = viewModel(
            object : JourneyRepository {
                override suspend fun search(query: JourneyQuery): JourneySearchResult {
                    throw JourneyApiException(
                        code = "NETWORK_UNAVAILABLE",
                        message = "No hay conexión con el servicio de rutas.",
                        retryable = true
                    )
                }
            }
        )
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.selectMode(JourneyMode.BICYCLE)

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(JourneySearchErrorKind.SERVICE_UNAVAILABLE, viewModel.state.value.errorKind)
        assertTrue(viewModel.state.value.errorRetryable)
        assertEquals(null, viewModel.state.value.result)
    }

    @Test
    fun emptyBackendResultIsKeptAsANoRoutesState() = runTest {
        val viewModel = viewModel(RecordingJourneyRepository())
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.selectMode(JourneyMode.BICYCLE)

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(requireNotNull(viewModel.state.value.result).itineraries.isEmpty())
        assertEquals(null, viewModel.state.value.errorMessage)
        assertEquals(null, viewModel.state.value.errorKind)
    }

    @Test
    fun savePersistsBothCoordinates() = runTest {
        val savedRepository = InMemorySavedCommuteRepository()
        val viewModel = viewModel(RecordingJourneyRepository(), savedRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.selectMode(JourneyMode.BICYCLE)

        viewModel.save()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val saved = savedRepository.observeAll().first().single()
        assertEquals(origin.coordinate, saved.originCoordinate)
        assertEquals(destination.coordinate, saved.destinationCoordinate)
        assertEquals(JourneyMode.BICYCLE, saved.mode)
        assertEquals(Weekday.weekendDays, saved.activeDays)
        assertEquals(true, saved.canCalculateJourney)
        assertEquals(true, viewModel.state.value.saved)
    }

    @Test
    fun scheduledWeekdayIsSavedAsTheWorkdayCategory() = runTest {
        val savedRepository = InMemorySavedCommuteRepository()
        val viewModel = viewModel(RecordingJourneyRepository(), savedRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.updateDepartureDate("2026-07-20")
        viewModel.updateDepartureTime("08:15")

        viewModel.save()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val saved = savedRepository.observeAll().first().single()
        assertEquals(Weekday.workingDays, saved.activeDays)
    }

    @Test
    fun searchRequiresTwoSelectedAddresses() {
        val viewModel = viewModel(RecordingJourneyRepository())

        viewModel.search()

        assertEquals(
            JourneySearchErrorMessage.MISSING_ADDRESSES,
            viewModel.state.value.errorMessage
        )
    }

    @Test
    fun openingSavedCommuteLoadsItsDataAndSearchesNextActiveDate() = runTest {
        val savedRepository = InMemorySavedCommuteRepository()
        val commute = SavedCommute(
            id = "commute-1",
            originLabel = origin.label,
            destinationLabel = destination.label,
            originCoordinate = origin.coordinate,
            destinationCoordinate = destination.coordinate,
            departureHour = 8,
            departureMinute = 15,
            activeDays = Weekday.workingDays,
            mode = JourneyMode.BICYCLE,
            profile = JourneyProfile.LESS_WALKING
        )
        savedRepository.upsert(commute)
        val journeyRepository = RecordingJourneyRepository()

        val viewModel = viewModel(
            journeyRepository = journeyRepository,
            savedRepository = savedRepository,
            savedCommuteId = commute.id
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val query = requireNotNull(journeyRepository.query)
        assertEquals(origin.coordinate, query.origin)
        assertEquals(destination.coordinate, query.destination)
        assertEquals("2026-07-20T08:15+02:00", query.departureAt.toString())
        assertEquals(JourneyMode.BICYCLE, query.mode)
        assertEquals(JourneyProfile.LESS_WALKING, query.profile)
        assertEquals(origin.label, viewModel.state.value.origin?.label)
        assertNotNull(viewModel.state.value.result)
    }

    @Test
    fun transitSearchRecommendsFasterActiveModesAndOpensCachedResult() = runTest {
        val journeyRepository = ModeDurationJourneyRepository(
            durations = mapOf(
                JourneyMode.TRANSIT to 30 * 60,
                JourneyMode.BICYCLE to 18 * 60,
                JourneyMode.WALK to 24 * 60
            )
        )
        val viewModel = viewModel(journeyRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)
        viewModel.updateDepartureDate("2026-07-20")
        viewModel.updateDepartureTime("08:15")

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val recommendations = viewModel.state.value.modeRecommendations
        assertEquals(listOf(JourneyMode.BICYCLE, JourneyMode.WALK), recommendations.map { it.mode })
        assertEquals(listOf(12, 6), recommendations.map { it.minutesSaved })
        assertEquals(3, journeyRepository.queries.size)
        assertFalse(viewModel.state.value.isComparingModes)

        viewModel.showRecommendedMode(JourneyMode.BICYCLE)

        assertEquals(JourneyMode.BICYCLE, viewModel.state.value.mode)
        assertEquals(18 * 60, viewModel.state.value.result?.itineraries?.single()?.durationSeconds)
        assertEquals(3, journeyRepository.queries.size)
        assertTrue(viewModel.state.value.modeRecommendations.isEmpty())
    }

    @Test
    fun transitSearchDoesNotRecommendEqualOrSlowerModes() = runTest {
        val journeyRepository = ModeDurationJourneyRepository(
            durations = mapOf(
                JourneyMode.TRANSIT to 20 * 60,
                JourneyMode.BICYCLE to 20 * 60,
                JourneyMode.WALK to 28 * 60
            )
        )
        val viewModel = viewModel(journeyRepository)
        viewModel.selectOrigin(origin)
        viewModel.selectDestination(destination)

        viewModel.search()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.modeRecommendations.isEmpty())
        assertNotNull(viewModel.state.value.result)
    }

    private fun viewModel(
        journeyRepository: JourneyRepository,
        savedRepository: InMemorySavedCommuteRepository = InMemorySavedCommuteRepository(),
        currentLocationProvider: CurrentLocationProvider = CurrentLocationProvider {
            throw CurrentLocationUnavailableException("Ubicación no configurada en el test.")
        },
        savedCommuteId: String? = null
    ) = JourneySearchViewModel(
        journeyRepository = journeyRepository,
        savedCommuteRepository = savedRepository,
        currentLocationProvider = currentLocationProvider,
        clock = Clock.fixed(
            Instant.parse("2026-07-19T10:00:00Z"),
            JourneySearchViewModel.CORDOBA_ZONE
        ),
        savedCommuteId = savedCommuteId
    )

    private class RecordingJourneyRepository : JourneyRepository {
        var query: JourneyQuery? = null

        override suspend fun search(query: JourneyQuery): JourneySearchResult {
            this.query = query
            return JourneySearchResult(
                dataVersion = "aucorsa-test",
                generatedAt = query.departureAt,
                realtimeStatus = RealtimeStatus.SCHEDULED_ONLY,
                itineraries = emptyList()
            )
        }
    }

    private class ModeDurationJourneyRepository(
        private val durations: Map<JourneyMode, Int>
    ) : JourneyRepository {
        val queries = mutableListOf<JourneyQuery>()

        override suspend fun search(query: JourneyQuery): JourneySearchResult {
            queries += query
            val duration = requireNotNull(durations[query.mode])
            val transportMode = when (query.mode) {
                JourneyMode.TRANSIT -> TransportMode.BUS
                JourneyMode.BICYCLE -> TransportMode.BICYCLE
                JourneyMode.WALK -> TransportMode.WALK
            }
            return JourneySearchResult(
                dataVersion = "aucorsa-test",
                generatedAt = query.departureAt,
                realtimeStatus = RealtimeStatus.SCHEDULED_ONLY,
                itineraries = listOf(
                    JourneyOption(
                        id = query.mode.name,
                        startsAt = query.departureAt,
                        endsAt = query.departureAt.plusSeconds(duration.toLong()),
                        durationSeconds = duration,
                        transfers = 0,
                        walkDistanceMeters = if (query.mode == JourneyMode.WALK) 2_000 else 0,
                        legs = listOf(
                            JourneyLeg(
                                mode = transportMode,
                                startsAt = query.departureAt,
                                endsAt = query.departureAt.plusSeconds(duration.toLong()),
                                fromName = "Origen",
                                toName = "Destino"
                            )
                        )
                    )
                )
            )
        }
    }

    private companion object {
        val origin = AddressPlace(
            id = "origin",
            label = "Calle Andrés Barrera, Córdoba",
            coordinate = Coordinate(37.9022326, -4.7304484)
        )
        val destination = AddressPlace(
            id = "destination",
            label = "Ronda de los Tejares 12, Córdoba",
            coordinate = Coordinate(37.8884845, -4.7807885)
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
