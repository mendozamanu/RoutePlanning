package com.example.routeplanning.mvp.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.routeplanning.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun CurrentLocationButton(
    isLocating: Boolean,
    onUseCurrentLocation: () -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onUseCurrentLocation()
        } else {
            onPermissionDenied()
        }
    }

    OutlinedButton(
        onClick = {
            if (context.hasLocationPermission()) {
                onUseCurrentLocation()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        enabled = !isLocating,
        modifier = modifier.fillMaxWidth()
    ) {
        if (isLocating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.mvp_locating),
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            Text(stringResource(R.string.mvp_use_current_location))
        }
    }
}

@Composable
fun DeparturePicker(
    choice: DepartureChoice,
    departureDate: String,
    departureTime: String,
    onDepartureNowSelected: () -> Unit,
    onDepartureScheduledSelected: () -> Unit,
    onDepartureDateChanged: (String) -> Unit,
    onDepartureTimeChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate = departureDate.toLocalDateOrToday()
    val selectedTime = departureTime.toLocalTimeOrNow()
    val locale = LocalConfiguration.current.locales[0]
    val displayDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.mvp_departure_question),
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = choice == DepartureChoice.NOW,
                onClick = onDepartureNowSelected,
                label = { Text(stringResource(R.string.mvp_departure_now)) }
            )
            FilterChip(
                selected = choice == DepartureChoice.SCHEDULED,
                onClick = onDepartureScheduledSelected,
                label = { Text(stringResource(R.string.mvp_departure_schedule)) }
            )
        }
        if (choice == DepartureChoice.NOW) {
            Text(
                stringResource(R.string.mvp_departure_now_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerButton(
                    label = stringResource(R.string.mvp_departure_date),
                    value = selectedDate.format(displayDateFormatter),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                onDepartureDateChanged(
                                    LocalDate.of(year, month + 1, day).toString()
                                )
                            },
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth
                        ).apply {
                            datePicker.minDate = LocalDate.now()
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }.show()
                    },
                    modifier = Modifier.weight(1.45f)
                )
                PickerButton(
                    label = stringResource(R.string.mvp_departure_time),
                    value = selectedTime.format(DISPLAY_TIME_FORMATTER),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onDepartureTimeChanged(
                                    LocalTime.of(hour, minute).format(DISPLAY_TIME_FORMATTER)
                                )
                            },
                            selectedTime.hour,
                            selectedTime.minute,
                            DateFormat.is24HourFormat(context)
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PickerButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun String.toLocalDateOrToday(): LocalDate = try {
    LocalDate.parse(this)
} catch (_: DateTimeParseException) {
    LocalDate.now()
}

private fun String.toLocalTimeOrNow(): LocalTime = try {
    LocalTime.parse(this)
} catch (_: DateTimeParseException) {
    LocalTime.now()
}

private val DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
