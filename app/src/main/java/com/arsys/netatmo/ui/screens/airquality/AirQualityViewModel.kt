package com.arsys.netatmo.ui.screens.airquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AirQualityUiState(
    val co2Level: Int? = null,           // ppm — from NAMain module
    val humidity: Int? = null,           // %
    val noise: Int? = null,              // dB
    val pressure: Double? = null,        // mbar
    val outdoorTemp: Double? = null,     // °C — from Meteosource / NAModule1
    val weatherCondition: String = "",
    val airQualityScore: Int = 0,        // 0-100 computed
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class AirQualityViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AirQualityUiState())
    val uiState: StateFlow<AirQualityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                homeId?.let { loadData(it) }
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

    private suspend fun loadData(homeId: String) = coroutineScope {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val thermostatDeferred = async { getThermostatState(homeId) }
        val outdoorTempDeferred = async { thermostatRepository.getOutdoorTemperature(homeId) }

        val thermostatResult = thermostatDeferred.await()
        val outdoorTemp = outdoorTempDeferred.await()

        when (thermostatResult) {
            is ApiResult.Success -> {
                val mainModule = thermostatResult.data.modules.firstOrNull { it.type == "NAMain" }
                val co2 = mainModule?.co2Level
                val humidity = mainModule?.humidity
                val noise = mainModule?.noise
                val pressure = mainModule?.pressure
                val score = computeAirQualityScore(co2, humidity, noise)
                _uiState.update {
                    it.copy(
                        co2Level = co2,
                        humidity = humidity,
                        noise = noise,
                        pressure = pressure,
                        outdoorTemp = outdoorTemp,
                        weatherCondition = deriveWeatherCondition(outdoorTemp),
                        airQualityScore = score,
                        isLoading = false
                    )
                }
            }
            is ApiResult.Error -> {
                // Still show outdoor data even if thermostat fails
                _uiState.update {
                    it.copy(
                        outdoorTemp = outdoorTemp,
                        weatherCondition = deriveWeatherCondition(outdoorTemp),
                        isLoading = false,
                        error = thermostatResult.message
                    )
                }
            }
            ApiResult.Loading -> {}
        }
    }

    /**
     * Scores 0-100 from CO2 (primary), humidity (secondary) and noise (minor).
     * Returns 0 when no data is available.
     */
    private fun computeAirQualityScore(co2Ppm: Int?, humidity: Int?, noiseDb: Int?): Int {
        if (co2Ppm == null && humidity == null) return 0

        // CO2 component — weight 70 %
        val co2Score = when {
            co2Ppm == null -> 70            // neutral when unknown
            co2Ppm < 600  -> 100
            co2Ppm < 800  -> 85
            co2Ppm < 1000 -> 65
            co2Ppm < 1200 -> 40
            co2Ppm < 1500 -> 20
            else           -> 5
        }

        // Humidity component — weight 20 % (optimal 40-60 %)
        val humScore = when {
            humidity == null       -> 20    // neutral
            humidity in 40..60    -> 20
            humidity in 30..39
            || humidity in 61..70 -> 14
            humidity in 20..29
            || humidity in 71..80 -> 8
            else                   -> 3
        }

        // Noise component — weight 10 % (comfortable < 40 dB)
        val noiseScore = when {
            noiseDb == null  -> 10          // neutral
            noiseDb < 40     -> 10
            noiseDb < 55     -> 7
            noiseDb < 70     -> 4
            else              -> 1
        }

        return (co2Score * 0.70 + humScore + noiseScore).toInt().coerceIn(0, 100)
    }

    private fun deriveWeatherCondition(temp: Double?): String = when {
        temp == null   -> ""
        temp < 0       -> "Bajo cero"
        temp < 10      -> "Frío"
        temp < 18      -> "Fresco"
        temp < 25      -> "Templado"
        temp < 32      -> "Cálido"
        else           -> "Muy caluroso"
    }
}
