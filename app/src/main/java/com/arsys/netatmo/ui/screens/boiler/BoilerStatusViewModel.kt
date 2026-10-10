package com.arsys.netatmo.ui.screens.boiler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BoilerUiState(
    val isBoilerOn: Boolean = false,
    val modulationPct: Int? = null,
    val pressureBar: Double? = null,
    val heatingActive: Boolean = false,
    val roomCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BoilerStatusViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoilerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                homeId?.let { loadData(it) }
            }
        }
    }

    private fun loadData(homeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getThermostatState(homeId)) {
                is ApiResult.Success -> {
                    val state = result.data
                    val plug = state.modules.firstOrNull { it.type == "NAPlug" }
                    val mainModule = state.modules.firstOrNull { it.type == "NAMain" }
                    val heatingRooms = state.rooms.count { it.heatingActive }
                    _uiState.update {
                        BoilerUiState(
                            isBoilerOn = plug?.boilerStatus == true,
                            modulationPct = plug?.modulationLevel,
                            pressureBar = mainModule?.pressure,
                            heatingActive = heatingRooms > 0,
                            roomCount = heatingRooms,
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            authRepository.selectedHomeId.value?.let { loadData(it) }
        }
    }
}
