package com.example.routeplanning.mvp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.routeplanning.R
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.JourneyOption
import com.example.routeplanning.mvp.domain.TransportMode
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

private val WalkColor = Color(0xFF616161)
private val BusColor = Color(0xFF1565C0)
private val RailColor = Color(0xFF7B1FA2)
private val BicycleColor = Color(0xFF00897B)

internal data class JourneyMapSegment(
    val mode: TransportMode,
    val points: List<Coordinate>
)

internal fun JourneyOption.mapSegments(): List<JourneyMapSegment> = legs.mapNotNull { leg ->
    leg.geometry.takeIf { it.size >= 2 }?.let { geometry ->
        JourneyMapSegment(mode = leg.mode, points = geometry)
    }
}

internal fun JourneyOption.mapPoints(
    origin: Coordinate,
    destination: Coordinate
): List<Coordinate> = buildList {
    add(origin)
    legs.forEach { addAll(it.geometry) }
    add(destination)
}.distinct()

@Composable
internal fun JourneyRouteMap(
    origin: Coordinate,
    destination: Coordinate,
    option: JourneyOption,
    modifier: Modifier = Modifier
) {
    val segments = remember(option) { option.mapSegments() }
    val mapPoints = remember(option, origin, destination) {
        option.mapPoints(origin, destination)
    }
    val cameraPositionState = rememberCameraPositionState()
    val originMarkerState = remember(origin) { MarkerState(position = origin.toLatLng()) }
    val destinationMarkerState = remember(destination) {
        MarkerState(position = destination.toLatLng())
    }
    val originTitle = stringResource(R.string.mvp_map_origin)
    val destinationTitle = stringResource(R.string.mvp_map_destination)
    var mapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, mapPoints) {
        if (!mapLoaded || mapPoints.isEmpty()) return@LaunchedEffect
        val latLngPoints = mapPoints.map(Coordinate::toLatLng)
        val update = if (latLngPoints.distinct().size == 1) {
            CameraUpdateFactory.newLatLngZoom(latLngPoints.first(), 16f)
        } else {
            val bounds = LatLngBounds.builder().apply {
                latLngPoints.forEach(::include)
            }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, 72)
        }
        cameraPositionState.animate(update, 500)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.mvp_route_map),
            style = MaterialTheme.typography.titleSmall
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    compassEnabled = true,
                    mapToolbarEnabled = false,
                    zoomControlsEnabled = true
                ),
                onMapLoaded = { mapLoaded = true }
            ) {
                segments.forEachIndexed { index, segment ->
                    Polyline(
                        points = segment.points.map(Coordinate::toLatLng),
                        color = segment.mode.mapColor(),
                        width = 11f,
                        zIndex = index.toFloat()
                    )
                }
                Marker(
                    state = originMarkerState,
                    title = originTitle,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN
                    )
                )
                Marker(
                    state = destinationMarkerState,
                    title = destinationTitle,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (segments.any { it.mode == TransportMode.WALK }) {
                MapLegendItem(WalkColor, stringResource(R.string.mvp_walk_leg))
            }
            if (segments.any { it.mode == TransportMode.BUS }) {
                MapLegendItem(BusColor, stringResource(R.string.mvp_map_bus))
            }
            if (segments.any { it.mode == TransportMode.RAIL }) {
                MapLegendItem(RailColor, stringResource(R.string.mvp_map_train))
            }
            if (segments.any { it.mode == TransportMode.BICYCLE }) {
                MapLegendItem(BicycleColor, stringResource(R.string.mvp_bicycle_leg))
            }
        }
    }
}

@Composable
private fun MapLegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Coordinate.toLatLng() = LatLng(latitude, longitude)

private fun TransportMode.mapColor(): Color = when (this) {
    TransportMode.WALK -> WalkColor
    TransportMode.BUS -> BusColor
    TransportMode.RAIL -> RailColor
    TransportMode.BICYCLE -> BicycleColor
}
