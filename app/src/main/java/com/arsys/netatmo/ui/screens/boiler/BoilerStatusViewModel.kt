package com.arsys.netatmo.ui.screens.boiler

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.model.BoilerHealth
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.domain.model.ThermostatType
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Diagnostic types ──────────────────────────────────────────────────────────

enum class CheckStatus { OK, WARNING, CRITICAL }

data class DiagnosticCheckResult(
    val label: String,
    val status: CheckStatus,
    val detail: String
)

data class DiagnosticResult(
    val grade: String,        // "A+", "A", "B", "C", "F"
    val gradeScore: Int,      // 0–100
    val checks: List<DiagnosticCheckResult>,
    val recommendations: List<String>
)

// ── UI state ──────────────────────────────────────────────────────────────────

data class BoilerStatusUiState(
    val boilerHealth: BoilerHealth = BoilerHealth(
        pressureBar = null,
        modulationPct = null,
        impulsionTempC = null,
        isModulating = false,
        healthScore = 0,
        healthLabel = "Cargando…"
    ),
    val modules: List<ModuleState> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Cached so refresh() works without re-collecting the flow
    val cachedHomeId: String? = null,
    val thermostatType: ThermostatType = ThermostatType.STANDARD
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class BoilerStatusViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoilerStatusUiState())
    val uiState: StateFlow<BoilerStatusUiState> = _uiState.asStateFlow()

    init {
        val prefs = context.getSharedPreferences(ThermostatType.PREFS_NAME, Context.MODE_PRIVATE)
        val type = ThermostatType.fromString(prefs.getString(ThermostatType.PREFS_KEY, null))
        _uiState.update { it.copy(thermostatType = type) }
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                if (homeId != null) {
                    _uiState.update { it.copy(cachedHomeId = homeId) }
                    loadData(homeId)
                }
            }
        }
    }

    fun refresh() {
        val homeId = _uiState.value.cachedHomeId ?: return
        loadData(homeId)
    }

    private fun loadData(homeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getThermostatState(homeId)) {
                is ApiResult.Success -> {
                    val state = result.data
                    val plug = state.modules.firstOrNull { it.type == "NAPlug" }
                    val health = computeBoilerHealth(plug)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            boilerHealth = health,
                            modules = state.modules
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                ApiResult.Loading -> {}
            }
        }
    }

    private fun computeBoilerHealth(plug: ModuleState?): BoilerHealth {
        if (plug == null) return BoilerHealth(null, null, null, false, 0, "Sin datos")
        var score = 100
        if (!plug.reachable) score -= 50
        val rf = plug.rfStrength ?: 90
        if (rf < 40) score -= 15
        val modPct = plug.modulationLevel
        if (modPct != null && modPct == 0 && plug.boilerStatus == true) score -= 5
        score = score.coerceIn(0, 100)
        val label = when {
            score >= 90 -> "Excelente"
            score >= 75 -> "Bueno"
            score >= 55 -> "Regular"
            score >= 35 -> "Atención"
            else -> "Crítico"
        }
        return BoilerHealth(
            pressureBar = null,
            modulationPct = modPct,
            impulsionTempC = null,
            isModulating = (modPct ?: 0) > 0 && plug.boilerStatus == true,
            healthScore = score,
            healthLabel = label
        )
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

/**
 * Builds a status snapshot from real NAPlug data only. Returns null when there is no
 * boiler module data, so no result is ever invented.
 */
fun buildStatusReport(state: BoilerStatusUiState): DiagnosticResult? {
    val plug = state.modules.firstOrNull { it.type == "NAPlug" } ?: return null
    val health = state.boilerHealth
    val rfStrength = plug.rfStrength
    val rfOk = rfStrength == null || rfStrength >= 40
    val modPct = health.modulationPct

    val checks = buildList {
        add(
            DiagnosticCheckResult(
                label = "Conexión del módulo",
                status = if (plug.reachable) CheckStatus.OK else CheckStatus.WARNING,
                detail = if (plug.reachable) "Módulo NAPlug alcanzable" else "Módulo NAPlug inalcanzable"
            )
        )
        if (rfStrength != null) {
            add(
                DiagnosticCheckResult(
                    label = "Conectividad RF",
                    status = if (rfOk) CheckStatus.OK else CheckStatus.WARNING,
                    detail = "Señal RF: $rfStrength"
                )
            )
        }
        if (modPct != null) {
            add(
                DiagnosticCheckResult(
                    label = "Nivel de modulación",
                    status = CheckStatus.OK,
                    detail = "Modulación al $modPct%"
                )
            )
        }
    }

    val grade = when {
        health.healthScore >= 95 -> "A+"
        health.healthScore >= 85 -> "A"
        health.healthScore >= 70 -> "B"
        health.healthScore >= 55 -> "C"
        else -> "F"
    }

    val recommendations = buildList {
        if (!plug.reachable) add("Comprueba la conexión WiFi del módulo NAPlug")
        if (!rfOk) add("Acerca el termostato al relay NAPlug para mejorar la señal RF (actual: $rfStrength)")
        if (health.healthScore < 75) add("Considera programar una revisión técnica anual de la caldera")
    }

    return DiagnosticResult(
        grade = grade,
        gradeScore = health.healthScore,
        checks = checks,
        recommendations = recommendations
    )
}
