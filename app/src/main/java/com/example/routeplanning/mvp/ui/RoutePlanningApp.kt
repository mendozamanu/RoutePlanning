package com.example.routeplanning.mvp.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.routeplanning.R
import com.example.routeplanning.mvp.domain.JourneyRepository
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository

private const val HOME_ROUTE = "home"
private const val SEARCH_ROUTE = "search"
private const val SAVED_COMMUTE_ID_ARGUMENT = "savedCommuteId"
private const val SEARCH_ROUTE_PATTERN =
    "$SEARCH_ROUTE?$SAVED_COMMUTE_ID_ARGUMENT={$SAVED_COMMUTE_ID_ARGUMENT}"

@Composable
fun RoutePlanningApp(
    repository: SavedCommuteRepository,
    journeyRepository: JourneyRepository,
    currentLocationProvider: CurrentLocationProvider,
    onOpenLegacy: () -> Unit
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
            val commutes by homeViewModel.commutes.collectAsStateWithLifecycle()
            HomeScreen(
                commutes = commutes,
                onSearchJourney = { navController.navigate(SEARCH_ROUTE) },
                onOpenCommute = { commute ->
                    navController.navigate(
                        "$SEARCH_ROUTE?$SAVED_COMMUTE_ID_ARGUMENT=${Uri.encode(commute.id)}"
                    )
                },
                onDeleteCommute = homeViewModel::delete,
                onOpenLegacy = onOpenLegacy
            )
        }
        composable(
            route = SEARCH_ROUTE_PATTERN,
            arguments = listOf(
                navArgument(SAVED_COMMUTE_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val savedCommuteId = backStackEntry.arguments
                ?.getString(SAVED_COMMUTE_ID_ARGUMENT)
            val searchViewModel: JourneySearchViewModel = viewModel(
                factory = JourneySearchViewModel.factory(
                    journeyRepository = journeyRepository,
                    savedCommuteRepository = repository,
                    currentLocationProvider = currentLocationProvider,
                    savedCommuteId = savedCommuteId
                )
            )
            val state by searchViewModel.state.collectAsStateWithLifecycle()
            JourneySearchScreen(
                state = state,
                onOriginSelected = searchViewModel::selectOrigin,
                onDestinationSelected = searchViewModel::selectDestination,
                onAddressError = searchViewModel::showAddressError,
                onUseCurrentLocation = searchViewModel::useCurrentLocation,
                onLocationPermissionDenied = searchViewModel::showLocationPermissionDenied,
                onSwapLocations = searchViewModel::swapLocations,
                onDepartureNowSelected = searchViewModel::selectDepartureNow,
                onDepartureScheduledSelected = searchViewModel::scheduleDeparture,
                onDepartureDateChanged = searchViewModel::updateDepartureDate,
                onDepartureTimeChanged = searchViewModel::updateDepartureTime,
                onModeSelected = searchViewModel::selectMode,
                onRecommendedModeSelected = searchViewModel::showRecommendedMode,
                onSearch = searchViewModel::search,
                onSave = searchViewModel::save,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    commutes: List<SavedCommute>,
    onSearchJourney: () -> Unit,
    onOpenCommute: (SavedCommute) -> Unit,
    onDeleteCommute: (String) -> Unit,
    onOpenLegacy: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.mvp_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(stringResource(R.string.mvp_subtitle), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.mvp_scheduled_data),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(12.dp))
            }
            if (commutes.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.mvp_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.mvp_empty_body))
                        }
                    }
                }
            } else {
                items(commutes, key = { it.id }) { commute ->
                    CommuteCard(
                        commute = commute,
                        onOpenMap = { onOpenCommute(commute) },
                        onDelete = { onDeleteCommute(commute.id) }
                    )
                }
            }
            item {
                Button(onClick = onSearchJourney, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mvp_search_journey))
                }
                OutlinedButton(onClick = onOpenLegacy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mvp_open_legacy))
                }
                Text(
                    stringResource(R.string.mvp_offline_storage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommuteCard(
    commute: SavedCommute,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit
) {
    val modeLabel = when (commute.mode) {
        JourneyMode.TRANSIT -> stringResource(R.string.mvp_mode_transit)
        JourneyMode.BICYCLE -> stringResource(R.string.mvp_mode_bicycle)
        JourneyMode.WALK -> stringResource(R.string.mvp_mode_walk)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    R.string.mvp_commute_description,
                    commute.originLabel,
                    commute.destinationLabel
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.mvp_departure_description,
                    commute.formattedDeparture,
                    commute.activeDays.joinToString("") { it.shortLabel }
                )
            )
            Text(modeLabel, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenMap,
                    enabled = commute.canCalculateJourney
                ) {
                    Text(stringResource(R.string.mvp_open_saved_map))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.mvp_delete_commute))
                }
            }
            if (!commute.canCalculateJourney) {
                Text(
                    stringResource(R.string.mvp_saved_commute_missing_coordinates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
