package com.arsys.netatmo.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
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

    fun setFrozenAlerts(enabled: Boolean) {
        viewModelScope.launch { vacationRepo.setFrozenAlerts(enabled) }
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
        if (state.endMs <= System.currentTimeMillis()) {
            _uiState.update { it.copy(error = "La fecha de fin debe ser futura") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val result = vacationRepo.activate(state.startMs, state.endMs)
                if (result is ApiResult.Error) {
                    _uiState.update { it.copy(error = "No se pudo activar el modo vacaciones: ${result.message}") }
                }
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
                val result = vacationRepo.deactivate()
                if (result is ApiResult.Error) {
                    _uiState.update { it.copy(error = "No se pudo restaurar la programacion: ${result.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al desactivar el modo vacaciones") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
