package com.example.routeplanning.mvp.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.CurrentLocationProvider
import com.example.routeplanning.mvp.domain.CurrentLocationUnavailableException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
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

    override suspend fun addressLabel(coordinate: Coordinate): String? {
        if (!Geocoder.isPresent()) return null
        val address = Geocoder(context, Locale.getDefault()).firstAddress(coordinate)
            ?: return null
        return address.toConciseLabel()
    }
}

private suspend fun Geocoder.firstAddress(coordinate: Coordinate): Address? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            getFromLocation(
                coordinate.latitude,
                coordinate.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            getFromLocation(coordinate.latitude, coordinate.longitude, 1)?.firstOrNull()
        }
    }

private fun Address.toConciseLabel(): String? {
    val street = listOfNotNull(
        thoroughfare?.trim()?.takeIf(String::isNotEmpty),
        subThoroughfare?.trim()?.takeIf(String::isNotEmpty)
    ).joinToString(" ")
    val city = locality?.trim()?.takeIf(String::isNotEmpty)
    val concise = listOfNotNull(
        street.takeIf(String::isNotEmpty),
        city?.takeUnless { it.equals(street, ignoreCase = true) }
    ).joinToString(", ")
    return concise.takeIf(String::isNotEmpty)
        ?: getAddressLine(0)?.trim()?.takeIf(String::isNotEmpty)
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
