package com.example.routeplanning.mvp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: SavedCommuteRepository
) : ViewModel() {
    val commutes: StateFlow<List<SavedCommute>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(repository: SavedCommuteRepository): ViewModelProvider.Factory =
            viewModelFactory { HomeViewModel(repository) }
    }
}

internal fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <TViewModel : ViewModel> create(modelClass: Class<TViewModel>): TViewModel {
            return create() as TViewModel
        }
    }
