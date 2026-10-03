package com.arsys.netatmo.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.domain.usecase.GetTemperatureHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val temperatureData: List<TemperatureDataPoint> = emptyList(),
    val avgTemp: Double? = null,
    val maxTemp: Double? = null,
    val minTemp: Double? = null,
    val heatingHours: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getTemperatureHistory: GetTemperatureHistoryUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    fun loadStatistics(days: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val homeId = authRepository.selectedHomeId.first() ?: return@launch

            // Obtener primera habitación disponible del caché
            getTemperatureHistory(homeId, "", days)
                .collect { data ->
                    if (data.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false) }
                        return@collect
                    }
                    val temps = data.map { it.temperature }
                    val heatingPoints = data.count { it.heatingActive }
                    val heatingHours = (heatingPoints * (days * 24.0 / data.size)).toInt()

                    _uiState.update {
                        it.copy(
                            temperatureData = data,
                            avgTemp = temps.average(),
                            maxTemp = temps.max(),
                            minTemp = temps.min(),
                            heatingHours = heatingHours,
                            isLoading = false
                        )
                    }
                }
        }
    }
}
