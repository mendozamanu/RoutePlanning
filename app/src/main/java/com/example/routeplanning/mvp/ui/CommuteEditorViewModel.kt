package com.example.routeplanning.mvp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommuteEditorState(
    val origin: String = "",
    val destination: String = "",
    val departure: String = "08:00",
    val showValidationError: Boolean = false,
    val saved: Boolean = false
)

class CommuteEditorViewModel(
    private val repository: SavedCommuteRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(CommuteEditorState())
    val state: StateFlow<CommuteEditorState> = mutableState.asStateFlow()

    fun updateOrigin(value: String) = mutableState.update {
        it.copy(origin = value, showValidationError = false)
    }

    fun updateDestination(value: String) = mutableState.update {
        it.copy(destination = value, showValidationError = false)
    }

    fun updateDeparture(value: String) = mutableState.update { it.copy(departure = value) }

    fun save() {
        val current = mutableState.value
        if (current.origin.isBlank() || current.destination.isBlank()) {
            mutableState.update { it.copy(showValidationError = true) }
            return
        }
        val (hour, minute) = parseTime(current.departure)
        viewModelScope.launch {
            repository.upsert(
                SavedCommute(
                    originLabel = current.origin.trim(),
                    destinationLabel = current.destination.trim(),
                    departureHour = hour,
                    departureMinute = minute
                )
            )
            mutableState.update { it.copy(saved = true) }
        }
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val parts = value.split(":", limit = 2)
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    companion object {
        fun factory(repository: SavedCommuteRepository): ViewModelProvider.Factory =
            viewModelFactory { CommuteEditorViewModel(repository) }
    }
}
