package com.example.routeplanning.mvp.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.routeplanning.R
import com.example.routeplanning.mvp.domain.AddressPlace
import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.CordobaServiceArea
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity

@Composable
fun AddressPickerField(
    label: String,
    selectedPlace: AddressPlace?,
    onPlaceSelected: (AddressPlace) -> Unit,
    onError: (JourneySearchErrorMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val placesClient = remember(context) {
        if (Places.isInitialized()) Places.createClient(context) else null
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data ?: return@rememberLauncherForActivityResult
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val prediction = PlaceAutocomplete.getPredictionFromIntent(intent)
                if (prediction == null) {
                    onError(JourneySearchErrorMessage.ADDRESS_NO_SELECTION)
                    return@rememberLauncherForActivityResult
                }
                val sessionToken = PlaceAutocomplete.getSessionTokenFromIntent(intent)
                val client = placesClient
                if (client == null) {
                    onError(JourneySearchErrorMessage.PLACES_NOT_CONFIGURED)
                    return@rememberLauncherForActivityResult
                }
                val request = FetchPlaceRequest.builder(prediction.placeId, PLACE_FIELDS)
                    .setSessionToken(sessionToken)
                    .setRegionCode("ES")
                    .build()
                client.fetchPlace(request)
                    .addOnSuccessListener { response ->
                        val place = response.place
                        val location = place.location
                        val placeId = place.id
                        val address = place.formattedAddress ?: place.displayName
                        if (location == null || placeId.isNullOrBlank() || address.isNullOrBlank()) {
                            onError(JourneySearchErrorMessage.ADDRESS_COORDINATES_UNAVAILABLE)
                        } else {
                            onPlaceSelected(
                                AddressPlace(
                                    id = placeId,
                                    label = address,
                                    coordinate = Coordinate(location.latitude, location.longitude)
                                )
                            )
                        }
                    }
                    .addOnFailureListener {
                        onError(JourneySearchErrorMessage.ADDRESS_DETAILS_UNAVAILABLE)
                    }
            }

            PlaceAutocompleteActivity.RESULT_ERROR -> {
                onError(JourneySearchErrorMessage.ADDRESS_SEARCH_FAILED)
            }
        }
    }

    OutlinedButton(
        onClick = {
            if (placesClient == null) {
                onError(JourneySearchErrorMessage.MAPS_API_MISSING)
                return@OutlinedButton
            }
            val autocompleteIntent = PlaceAutocomplete.createIntent(context) {
                setInitialQuery(selectedPlace?.label.orEmpty())
                setCountries(listOf("ES"))
                setRegionCode("ES")
                setLocationRestriction(CORDOBA_BOUNDS)
                setOrigin(CORDOBA_CENTER)
            }
            launcher.launch(autocompleteIntent)
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                selectedPlace?.label ?: stringResource(R.string.mvp_address_placeholder),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private val PLACE_FIELDS = listOf(
    Place.Field.ID,
    Place.Field.FORMATTED_ADDRESS,
    Place.Field.DISPLAY_NAME,
    Place.Field.LOCATION
)

private val CORDOBA_CENTER = LatLng(37.8882, -4.7794)
private val CORDOBA_BOUNDS = RectangularBounds.newInstance(
    LatLng(CordobaServiceArea.MIN_LATITUDE, CordobaServiceArea.MIN_LONGITUDE),
    LatLng(CordobaServiceArea.MAX_LATITUDE, CordobaServiceArea.MAX_LONGITUDE)
)
