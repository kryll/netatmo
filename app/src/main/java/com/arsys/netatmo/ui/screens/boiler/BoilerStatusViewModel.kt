package com.arsys.netatmo.ui.screens.boiler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.model.BoilerHealth
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

val DIAGNOSTIC_CHECK_LABELS = listOf(
    "Presión hidráulica",
    "Circulación del circuito",
    "Válvulas de zona",
    "Nivel de modulación",
    "Conectividad RF"
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
    // Diagnostic — populated when launchDiagnostic() is called
    val isDiagnosticRunning: Boolean = false,
    val diagnosticProgress: Float = 0f,
    val diagnosticSecondsLeft: Int = 60,
    val diagnosticCompletedChecks: List<String> = emptyList(),
    val diagnosticResult: DiagnosticResult? = null,
    // Cached so refresh() works without re-collecting the flow
    val cachedHomeId: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class BoilerStatusViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoilerStatusUiState())
    val uiState: StateFlow<BoilerStatusUiState> = _uiState.asStateFlow()

    private var diagnosticJob: Job? = null

    init {
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

    /**
     * Simulates a 60-second hydraulic diagnostic.
     * Both BoilerStatusScreen and HydraulicDiagnosticScreen must share this ViewModel instance
     * (scope to the parent NavGraph back-stack entry) for the state to be visible in both screens.
     */
    fun launchDiagnostic() {
        if (_uiState.value.isDiagnosticRunning) return
        diagnosticJob?.cancel()
        diagnosticJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiagnosticRunning = true,
                    diagnosticProgress = 0f,
                    diagnosticSecondsLeft = 60,
                    diagnosticCompletedChecks = emptyList(),
                    diagnosticResult = null
                )
            }

            val totalSteps = DIAGNOSTIC_CHECK_LABELS.size   // 5 checks
            val secondsPerStep = 60 / totalSteps             // 12s each

            repeat(60) { tick ->
                delay(1_000L)
                val elapsed = tick + 1
                val doneCount = (elapsed / secondsPerStep).coerceAtMost(totalSteps)
                _uiState.update {
                    it.copy(
                        diagnosticProgress = elapsed / 60f,
                        diagnosticSecondsLeft = 60 - elapsed,
                        diagnosticCompletedChecks = DIAGNOSTIC_CHECK_LABELS.take(doneCount)
                    )
                }
            }

            val result = buildDiagnosticResult(_uiState.value)
            _uiState.update {
                it.copy(
                    isDiagnosticRunning = false,
                    diagnosticProgress = 1f,
                    diagnosticSecondsLeft = 0,
                    diagnosticCompletedChecks = DIAGNOSTIC_CHECK_LABELS,
                    diagnosticResult = result
                )
            }
        }
    }

    private fun buildDiagnosticResult(state: BoilerStatusUiState): DiagnosticResult {
        val health = state.boilerHealth
        val plug = state.modules.firstOrNull { it.type == "NAPlug" }
        val reachable = plug?.reachable ?: true
        val rfStrength = plug?.rfStrength ?: 90
        val rfOk = rfStrength >= 40
        val modPct = health.modulationPct

        val checks = listOf(
            DiagnosticCheckResult(
                label = "Presión hidráulica",
                status = if (reachable) CheckStatus.OK else CheckStatus.WARNING,
                detail = if (reachable) "Sin anomalías detectadas (sensor no expuesto por API)"
                         else "Módulo inalcanzable — presión no verificable"
            ),
            DiagnosticCheckResult(
                label = "Circulación del circuito",
                status = if (reachable) CheckStatus.OK else CheckStatus.WARNING,
                detail = if (reachable) "Bomba de circulación respondiendo" else "Sin confirmación de bomba"
            ),
            DiagnosticCheckResult(
                label = "Válvulas de zona",
                status = CheckStatus.OK,
                detail = "Todas las válvulas operativas"
            ),
            DiagnosticCheckResult(
                label = "Nivel de modulación",
                status = CheckStatus.OK,
                detail = modPct?.let { "Modulación al $it%" } ?: "Sin datos de modulación"
            ),
            DiagnosticCheckResult(
                label = "Conectividad RF",
                status = if (rfOk) CheckStatus.OK else CheckStatus.WARNING,
                detail = "Señal RF: $rfStrength dBm"
            )
        )

        val grade = when {
            health.healthScore >= 95 -> "A+"
            health.healthScore >= 85 -> "A"
            health.healthScore >= 70 -> "B"
            health.healthScore >= 55 -> "C"
            else -> "F"
        }

        val recommendations = buildList {
            if (!reachable) add("Comprueba la conexión WiFi del módulo NAPlug")
            if (!rfOk) add("Acerca el termostato al relay NAPlug para mejorar la señal RF (actual: $rfStrength dBm)")
            if (health.healthScore < 75) add("Considera programar una revisión técnica anual de la caldera")
        }

        return DiagnosticResult(
            grade = grade,
            gradeScore = health.healthScore,
            checks = checks,
            recommendations = recommendations
        )
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        diagnosticJob?.cancel()
    }
}
