package com.arsys.netatmo.ui.screens.airquality

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

data class AirQualityUiState(
    val co2Ppm: Int? = null,
    val humidity: Int? = null,
    val noiseDb: Int? = null,
    val pressureMbar: Double? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AirQualityViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AirQualityUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                homeId?.let { loadData(it) }
            }
        }
    }

    fun loadData(homeId: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val id = homeId ?: return@launch
            when (val result = getThermostatState(id)) {
                is ApiResult.Success -> {
                    val mainModule = result.data.modules.firstOrNull { it.type == "NAMain" }
                    _uiState.update {
                        it.copy(
                            co2Ppm = mainModule?.co2Level,
                            humidity = mainModule?.humidity,
                            noiseDb = mainModule?.noise,
                            pressureMbar = mainModule?.pressure,
                            isLoading = false
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                ApiResult.Loading -> {}
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                homeId?.let { loadData(it) }
            }
        }
    }
}
