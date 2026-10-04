package com.arsys.netatmo.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.model.GitHubRelease
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.BackupManager
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.data.repository.UpdateRepository
import com.arsys.netatmo.data.repository.UpdateStatus
import com.arsys.netatmo.data.repository.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val homes: List<Home> = emptyList(),
    val selectedHomeId: String? = null,
    val isLoadingHomes: Boolean = false,
    val autoRefresh: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val error: String? = null,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val backupResult: String? = null,
    val anomalyThreshold: Float = 3.0f,
    val exportedUri: Uri? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val thermostatRepository: ThermostatRepository,
    private val updateRepository: UpdateRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private companion object {
        val AUTO_REFRESH_KEY = booleanPreferencesKey("auto_refresh")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
        val ANOMALY_THRESHOLD_KEY = floatPreferencesKey("anomaly_threshold")
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                _uiState.update { it.copy(selectedHomeId = homeId) }
            }
        }
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _uiState.update {
                    it.copy(
                        autoRefresh = prefs[AUTO_REFRESH_KEY] ?: true,
                        notificationsEnabled = prefs[NOTIFICATIONS_KEY] ?: true,
                        anomalyThreshold = prefs[ANOMALY_THRESHOLD_KEY] ?: 3.0f
                    )
                }
            }
        }
    }

    fun loadHomes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHomes = true) }
            when (val result = thermostatRepository.getHomesData()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(homes = result.data, isLoadingHomes = false)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(error = result.message, isLoadingHomes = false)
                }
                ApiResult.Loading -> {}
            }
        }
    }

    fun selectHome(homeId: String) {
        viewModelScope.launch {
            authRepository.setSelectedHome(homeId)
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[AUTO_REFRESH_KEY] = enabled }
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[NOTIFICATIONS_KEY] = enabled }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Checking) }
            val status = updateRepository.checkForUpdates()
            _uiState.update { it.copy(updateStatus = status) }
        }
    }

    fun downloadAndInstall(release: GitHubRelease) {
        viewModelScope.launch {
            _uiState.update { it.copy(updateStatus = UpdateStatus.Downloading(0f)) }

            val result = updateRepository.downloadApk(release) { progress ->
                _uiState.update { it.copy(updateStatus = UpdateStatus.Downloading(progress)) }
            }

            result.fold(
                onSuccess = { file ->
                    _uiState.update { it.copy(updateStatus = UpdateStatus.Installing) }
                    updateRepository.installApk(file)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(updateStatus = UpdateStatus.Error(error.message ?: "Error desconocido"))
                    }
                }
            )
        }
    }

    fun dismissUpdateError() {
        _uiState.update { it.copy(updateStatus = UpdateStatus.Idle) }
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, backupResult = null) }
            val uri = backupManager.export(context)
            if (uri != null) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        backupResult = "Backup exportado correctamente",
                        exportedUri = uri
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isExporting = false, backupResult = "Error al exportar el backup")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, backupResult = null) }
            val result = backupManager.import(context, uri)
            result.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(isImporting = false, backupResult = "Se importaron $count registros correctamente")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isImporting = false, backupResult = "Error al importar: ${error.message}")
                    }
                }
            )
        }
    }

    fun setAnomalyThreshold(v: Float) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[ANOMALY_THRESHOLD_KEY] = v }
        }
    }

    fun dismissBackupResult() {
        _uiState.update { it.copy(backupResult = null) }
    }

    fun clearExportedUri() {
        _uiState.update { it.copy(exportedUri = null) }
    }
}
