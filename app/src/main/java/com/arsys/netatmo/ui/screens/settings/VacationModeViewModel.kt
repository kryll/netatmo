package com.arsys.netatmo.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.VacationModeRepository
import com.arsys.netatmo.data.repository.VacationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VacationModeUiState(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val temperature: Double = 15.0,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VacationModeViewModel @Inject constructor(
    private val vacationRepo: VacationModeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VacationModeUiState())
    val uiState: StateFlow<VacationModeUiState> = _uiState.asStateFlow()

    val vacationState: StateFlow<VacationState> = vacationRepo.state

    fun updateStart(startMs: Long) {
        _uiState.update { it.copy(startMs = startMs, error = null) }
    }

    fun updateEnd(endMs: Long) {
        _uiState.update { it.copy(endMs = endMs, error = null) }
    }

    fun updateTemp(temp: Double) {
        _uiState.update { it.copy(temperature = temp) }
    }

    fun activate() {
        val state = _uiState.value
        if (state.startMs == 0L || state.endMs == 0L) {
            _uiState.update { it.copy(error = "Selecciona las fechas de inicio y fin") }
            return
        }
        if (state.endMs <= state.startMs) {
            _uiState.update { it.copy(error = "La fecha de fin debe ser posterior a la de inicio") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                vacationRepo.activate(state.startMs, state.endMs, state.temperature)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al activar el modo vacaciones") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                vacationRepo.deactivate()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al desactivar el modo vacaciones") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
