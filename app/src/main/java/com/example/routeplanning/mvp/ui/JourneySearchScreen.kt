package com.example.routeplanning.mvp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.routeplanning.R
import com.example.routeplanning.mvp.domain.JourneyFareNotice
import com.example.routeplanning.mvp.domain.JourneyLeg
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyOption
import com.example.routeplanning.mvp.domain.TransportMode
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneySearchScreen(
    state: JourneySearchState,
    onOriginSelected: (com.example.routeplanning.mvp.domain.AddressPlace) -> Unit,
    onDestinationSelected: (com.example.routeplanning.mvp.domain.AddressPlace) -> Unit,
    onAddressError: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onSwapLocations: () -> Unit,
    onDepartureNowSelected: () -> Unit,
    onDepartureScheduledSelected: () -> Unit,
    onDepartureDateChanged: (String) -> Unit,
    onDepartureTimeChanged: (String) -> Unit,
    onModeSelected: (JourneyMode) -> Unit,
    onRecommendedModeSelected: (JourneyMode) -> Unit,
    onSearch: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val firstItineraryId = state.result?.itineraries?.firstOrNull()?.id
    val resultPresentationKey = state.result?.let { result ->
        firstItineraryId ?: "empty:${result.generatedAt}"
    }
    val resultsRequester = remember { BringIntoViewRequester() }
    var selectedItineraryId by rememberSaveable(firstItineraryId) {
        mutableStateOf(firstItineraryId)
    }
    LaunchedEffect(resultPresentationKey) {
        if (resultPresentationKey != null) {
            resultsRequester.bringIntoView()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mvp_search_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.mvp_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AddressPickerField(
                    label = stringResource(R.string.mvp_address_origin),
                    selectedPlace = state.origin,
                    onPlaceSelected = onOriginSelected,
                    onError = onAddressError
                )
                CurrentLocationButton(
                    isLocating = state.isLocating,
                    onUseCurrentLocation = onUseCurrentLocation,
                    onPermissionDenied = onLocationPermissionDenied,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                AddressPickerField(
                    label = stringResource(R.string.mvp_address_destination),
                    selectedPlace = state.destination,
                    onPlaceSelected = onDestinationSelected,
                    onError = onAddressError
                )
            }
            item {
                OutlinedButton(onClick = onSwapLocations, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mvp_swap_locations))
                }
            }
            item {
                Text(
                    stringResource(R.string.mvp_journey_mode),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.mode == JourneyMode.TRANSIT,
                        onClick = { onModeSelected(JourneyMode.TRANSIT) },
                        label = { Text(stringResource(R.string.mvp_mode_transit)) }
                    )
                    FilterChip(
                        selected = state.mode == JourneyMode.BICYCLE,
                        onClick = { onModeSelected(JourneyMode.BICYCLE) },
                        label = { Text(stringResource(R.string.mvp_mode_bicycle)) }
                    )
                    FilterChip(
                        selected = state.mode == JourneyMode.WALK,
                        onClick = { onModeSelected(JourneyMode.WALK) },
                        label = { Text(stringResource(R.string.mvp_mode_walk)) }
                    )
                }
            }
            item {
                DeparturePicker(
                    choice = state.departureChoice,
                    departureDate = state.departureDate,
                    departureTime = state.departureTime,
                    onDepartureNowSelected = onDepartureNowSelected,
                    onDepartureScheduledSelected = onDepartureScheduledSelected,
                    onDepartureDateChanged = onDepartureDateChanged,
                    onDepartureTimeChanged = onDepartureTimeChanged
                )
            }
            item {
                Text(
                    stringResource(R.string.mvp_places_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onSearch,
                    enabled = !state.isSearching,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.mvp_calculate_journey))
                }
            }
            if (state.isSearching) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.mvp_searching_journey))
                    }
                }
            }
            state.errorMessage?.let { errorMessage ->
                item {
                    JourneyProblemCard(
                        kind = state.errorKind,
                        message = errorMessage,
                        retryable = state.errorRetryable,
                        onRetry = when (state.errorKind) {
                            JourneySearchErrorKind.LOCATION_UNAVAILABLE -> onUseCurrentLocation
                            else -> onSearch
                        }
                    )
                }
            }
            state.result?.let { result ->
                if (result.itineraries.isEmpty()) {
                    item {
                        NoJourneyOptionsCard(
                            modifier = Modifier.bringIntoViewRequester(resultsRequester)
                        )
                    }
                } else {
                    item {
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.mvp_results_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .bringIntoViewRequester(resultsRequester)
                        )
                        Text(
                            stringResource(
                                when (state.mode) {
                                    JourneyMode.TRANSIT -> R.string.mvp_assumed_schedule
                                    JourneyMode.BICYCLE -> R.string.mvp_bicycle_data
                                    JourneyMode.WALK -> R.string.mvp_walk_data
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.isComparingModes) {
                        item {
                            Text(
                                stringResource(R.string.mvp_comparing_active_modes),
                                style = MaterialTheme.typography.bodySmall,
                                color = RecommendationGreen
                            )
                        }
                    }
                    if (state.modeRecommendations.isNotEmpty()) {
                        item {
                            FasterModeCard(
                                recommendations = state.modeRecommendations,
                                onModeSelected = onRecommendedModeSelected
                            )
                        }
                    }
                    items(result.itineraries, key = { it.id }) { option ->
                        JourneyOptionCard(
                            option = option,
                            origin = state.origin?.coordinate,
                            destination = state.destination?.coordinate,
                            searchedDepartureAt = state.searchedDepartureAt,
                            isSelected = option.id == selectedItineraryId,
                            onShowOnMap = { selectedItineraryId = option.id }
                        )
                    }
                    item {
                        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.mvp_save_searched_commute))
                        }
                    }
                }
            }
            if (state.saved) {
                item {
                    Text(
                        stringResource(R.string.mvp_commute_saved),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyProblemCard(
    kind: JourneySearchErrorKind?,
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit
) {
    val title = stringResource(
        when (kind) {
            JourneySearchErrorKind.OUTSIDE_SERVICE_AREA -> R.string.mvp_error_outside_area_title
            JourneySearchErrorKind.LOCATION_PERMISSION -> R.string.mvp_error_location_permission_title
            JourneySearchErrorKind.LOCATION_UNAVAILABLE -> R.string.mvp_error_location_unavailable_title
            JourneySearchErrorKind.SERVICE_UNAVAILABLE -> R.string.mvp_error_service_title
            JourneySearchErrorKind.INVALID_DEPARTURE -> R.string.mvp_error_departure_title
            JourneySearchErrorKind.SAVED_COMMUTE -> R.string.mvp_error_saved_commute_title
            JourneySearchErrorKind.ADDRESS_SELECTION,
            JourneySearchErrorKind.MISSING_ADDRESSES -> R.string.mvp_error_address_title
            JourneySearchErrorKind.UNKNOWN,
            null -> R.string.mvp_error_unknown_title
        }
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (retryable) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.mvp_retry))
                }
            }
        }
    }
}

@Composable
private fun NoJourneyOptionsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.mvp_no_routes_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.mvp_no_routes_body))
        }
    }
}

@Composable
private fun FasterModeCard(
    recommendations: List<ModeRecommendation>,
    onModeSelected: (JourneyMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RecommendationGreenContainer,
            contentColor = RecommendationGreenContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.mvp_faster_mode_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.mvp_faster_mode_body),
                style = MaterialTheme.typography.bodySmall
            )
            recommendations.forEach { recommendation ->
                val modeLabel = stringResource(
                    when (recommendation.mode) {
                        JourneyMode.BICYCLE -> R.string.mvp_mode_bicycle
                        JourneyMode.WALK -> R.string.mvp_mode_walk
                        JourneyMode.TRANSIT -> R.string.mvp_mode_transit
                    }
                )
                Text(
                    stringResource(
                        R.string.mvp_faster_mode_detail,
                        modeLabel,
                        recommendation.durationMinutes,
                        pluralStringResource(
                            R.plurals.mvp_minutes_faster,
                            recommendation.minutesSaved,
                            recommendation.minutesSaved
                        )
                    )
                )
                Button(
                    onClick = { onModeSelected(recommendation.mode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecommendationGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        stringResource(
                            when (recommendation.mode) {
                                JourneyMode.BICYCLE -> R.string.mvp_view_bicycle_route
                                JourneyMode.WALK -> R.string.mvp_view_walk_route
                                JourneyMode.TRANSIT -> R.string.mvp_mode_transit
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyOptionCard(
    option: JourneyOption,
    origin: com.example.routeplanning.mvp.domain.Coordinate?,
    destination: com.example.routeplanning.mvp.domain.Coordinate?,
    searchedDepartureAt: java.time.OffsetDateTime?,
    isSelected: Boolean,
    onShowOnMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(
                    R.string.mvp_result_summary,
                    option.startsAt.format(TIME_FORMATTER),
                    option.endsAt.format(TIME_FORMATTER),
                    (option.durationSeconds + 59) / 60
                ),
                style = MaterialTheme.typography.titleMedium
            )
            if (option.legs.any { it.mode == TransportMode.BICYCLE }) {
                Text(stringResource(R.string.mvp_bicycle_route_details))
            } else if (option.legs.all { it.mode == TransportMode.WALK }) {
                Text(
                    stringResource(
                        R.string.mvp_walk_route_details,
                        option.walkDistanceMeters
                    )
                )
            } else {
                Text(
                    stringResource(
                        R.string.mvp_result_details,
                        pluralStringResource(
                            R.plurals.mvp_ticket_count,
                            option.requiredTicketCount,
                            option.requiredTicketCount
                        ),
                        pluralStringResource(
                            R.plurals.mvp_transfer_count,
                            option.transfers,
                            option.transfers
                        ),
                        option.walkDistanceMeters
                    )
                )
                option.fareNotices.forEach { notice ->
                    FareNoticeCard(notice)
                }
            }
            val firstTransitLeg = option.legs.firstOrNull {
                it.mode == TransportMode.BUS || it.mode == TransportMode.RAIL
            }
            if (firstTransitLeg != null && searchedDepartureAt != null) {
                val minutesUntilTransit = minutesCeil(
                    Duration.between(searchedDepartureAt, firstTransitLeg.startsAt)
                        .seconds
                        .coerceAtLeast(0)
                )
                Text(
                    text = if (minutesUntilTransit == 0) {
                        stringResource(
                            R.string.mvp_first_transit_now,
                            firstTransitLeg.startsAt.format(TIME_FORMATTER)
                        )
                    } else {
                        stringResource(
                            R.string.mvp_first_transit_departure,
                            firstTransitLeg.startsAt.format(TIME_FORMATTER),
                            pluralStringResource(
                                R.plurals.mvp_minutes_from_departure,
                                minutesUntilTransit,
                                minutesUntilTransit
                            )
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            option.legs.forEachIndexed { index, leg ->
                if (index > 0) HorizontalDivider()
                val previousEnd = option.legs.getOrNull(index - 1)?.endsAt
                    ?: searchedDepartureAt
                val waitMinutes = previousEnd?.let {
                    minutesCeil(
                        Duration.between(it, leg.startsAt).seconds.coerceAtLeast(0)
                    )
                } ?: 0
                JourneyLegRow(
                    leg = leg,
                    isFirst = index == 0,
                    isLast = index == option.legs.lastIndex,
                    waitMinutes = waitMinutes
                )
            }
            if (isSelected && origin != null && destination != null) {
                JourneyRouteMap(
                    origin = origin,
                    destination = destination,
                    option = option,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedButton(onClick = onShowOnMap) {
                    Text(stringResource(R.string.mvp_show_on_map))
                }
            }
        }
    }
}

@Composable
private fun FareNoticeCard(notice: JourneyFareNotice) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.mvp_new_ticket_required),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (notice.routeShortName.isNullOrBlank()) {
                    stringResource(R.string.mvp_same_line_new_ticket_without_route, notice.stopName)
                } else {
                    stringResource(
                        R.string.mvp_same_line_new_ticket,
                        notice.stopName,
                        notice.routeShortName
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun JourneyLegRow(
    leg: JourneyLeg,
    isFirst: Boolean,
    isLast: Boolean,
    waitMinutes: Int
) {
    val durationMinutes = minutesCeil(leg.durationSeconds.toLong())
    when (leg.mode) {
        TransportMode.WALK -> {
            Text(
                if (leg.distanceMeters == null) {
                    stringResource(
                        R.string.mvp_walk_leg_timed,
                        leg.startsAt.format(TIME_FORMATTER),
                        durationMinutes
                    )
                } else {
                    stringResource(
                        R.string.mvp_walk_leg_detailed,
                        leg.startsAt.format(TIME_FORMATTER),
                        durationMinutes,
                        formatDistance(leg.distanceMeters)
                    )
                }
            )
            Text(
                when {
                    isFirst && isLast -> stringResource(R.string.mvp_walk_origin_to_destination)
                    isFirst -> stringResource(R.string.mvp_walk_to_stop, leg.toName)
                    isLast -> stringResource(R.string.mvp_walk_to_destination, leg.fromName)
                    else -> stringResource(
                        R.string.mvp_walk_transfer,
                        leg.fromName,
                        leg.toName
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TransportMode.BUS -> {
            Text(
                stringResource(
                    R.string.mvp_bus_leg_detailed,
                    leg.startsAt.format(TIME_FORMATTER),
                    leg.routeShortName ?: "—",
                    leg.headsign ?: leg.toName
                )
            )
            if (waitMinutes > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.mvp_wait_minutes,
                        waitMinutes,
                        waitMinutes
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                if (leg.stopCount == null) {
                    stringResource(
                        R.string.mvp_bus_board_alight,
                        leg.fromName,
                        leg.toName
                    )
                } else {
                    stringResource(
                        R.string.mvp_bus_board_alight_stops,
                        leg.fromName,
                        leg.toName,
                        pluralStringResource(
                            R.plurals.mvp_stop_count,
                            leg.stopCount,
                            leg.stopCount
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TransportMode.RAIL -> {
            Text(
                stringResource(
                    R.string.mvp_train_leg_detailed,
                    leg.startsAt.format(TIME_FORMATTER),
                    leg.headsign ?: leg.toName
                )
            )
            if (waitMinutes > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.mvp_wait_minutes,
                        waitMinutes,
                        waitMinutes
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                if (leg.stopCount == null) {
                    stringResource(
                        R.string.mvp_train_board_alight,
                        leg.fromName,
                        leg.toName
                    )
                } else {
                    stringResource(
                        R.string.mvp_train_board_alight_stops,
                        leg.fromName,
                        leg.toName,
                        pluralStringResource(
                            R.plurals.mvp_stop_count,
                            leg.stopCount,
                            leg.stopCount
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!leg.agencyName.isNullOrBlank()) {
                Text(
                    stringResource(R.string.mvp_transit_operator, leg.agencyName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TransportMode.BICYCLE -> {
            Text(
                if (leg.distanceMeters == null) {
                    stringResource(
                        R.string.mvp_bicycle_leg_timed,
                        leg.startsAt.format(TIME_FORMATTER),
                        durationMinutes
                    )
                } else {
                    stringResource(
                        R.string.mvp_bicycle_leg_detailed,
                        leg.startsAt.format(TIME_FORMATTER),
                        durationMinutes,
                        formatDistance(leg.distanceMeters)
                    )
                }
            )
            Text(
                stringResource(R.string.mvp_bicycle_origin_to_destination),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun minutesCeil(seconds: Long): Int = ((seconds + 59) / 60)
    .coerceAtMost(Int.MAX_VALUE.toLong())
    .toInt()

private fun formatDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "$distanceMeters m"
} else {
    val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    "${formatter.format(distanceMeters / 1_000.0)} km"
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val RecommendationGreenContainer = Color(0xFFDDF7E4)
private val RecommendationGreenContent = Color(0xFF0A4020)
private val RecommendationGreen = Color(0xFF187A3D)
