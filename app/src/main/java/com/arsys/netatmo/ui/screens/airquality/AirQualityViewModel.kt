package com.arsys.netatmo.ui.screens.airquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AirQualityUiState(
    val co2Ppm: Int? = null,
    val humidity: Int? = null,
    val noiseDb: Int? = null,
    val pressureMbar: Double? = null,
    val outdoorTemp: Double? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AirQualityViewModel @Inject constructor(
    private val repo: ThermostatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AirQualityUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                repo.getThermostatState().collect { state ->
                    val mainModule = state?.modules?.firstOrNull { it.type == "NAMain" }
                    _uiState.value = AirQualityUiState(
                        co2Ppm = mainModule?.co2Level,
                        humidity = mainModule?.humidity,
                        noiseDb = mainModule?.noise,
                        pressureMbar = mainModule?.pressure,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun refresh() { loadData() }
}
