package com.example.routeplanning.mvp.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.CurrentLocationUnavailableException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidCurrentLocationProvider(
    private val context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) : CurrentLocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun currentCoordinate(): Coordinate {
        if (!context.hasLocationPermission()) {
            throw CurrentLocationUnavailableException(
                "Necesitamos permiso de ubicación para usar tu posición como origen."
            )
        }

        val cancellationTokenSource = CancellationTokenSource()
        val current = try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).awaitLocation(cancellationTokenSource)
        } catch (error: SecurityException) {
            throw CurrentLocationUnavailableException(
                "Necesitamos permiso de ubicación para usar tu posición como origen.",
                error
            )
        } catch (error: Exception) {
            throw CurrentLocationUnavailableException(
                "No hemos podido obtener tu ubicación. Comprueba que la ubicación del dispositivo esté activada.",
                error
            )
        }

        val location = current ?: try {
            client.lastLocation.awaitLocation()
        } catch (_: Exception) {
            null
        }
        if (location == null) {
            throw CurrentLocationUnavailableException(
                "No hemos podido obtener tu ubicación. Comprueba que la ubicación del dispositivo esté activada."
            )
        }
        return Coordinate(location.latitude, location.longitude)
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private suspend fun Task<Location>.awaitLocation(
    cancellationTokenSource: CancellationTokenSource? = null
): Location? = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancellationTokenSource?.cancel() }
    addOnSuccessListener { location ->
        if (continuation.isActive) continuation.resume(location)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}
