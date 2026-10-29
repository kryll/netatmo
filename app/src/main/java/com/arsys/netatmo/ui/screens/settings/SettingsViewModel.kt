package com.arsys.netatmo.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.model.GitHubRelease
import com.arsys.netatmo.domain.model.ThemeMode
import com.arsys.netatmo.domain.model.ThermostatType
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
    val exportedUri: Uri? = null,
    val exportError: String? = null,
    val thermostatType: ThermostatType = ThermostatType.STANDARD,
    val openWindowEnabled: Boolean = true,
    val openWindowOffAction: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK
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
        val OPEN_WINDOW_KEY = booleanPreferencesKey("open_window_enabled")
        val OPEN_WINDOW_OFF_ACTION_KEY = booleanPreferencesKey("open_window_off_action")
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load persisted thermostat type
        val tprefs = context.getSharedPreferences(ThermostatType.PREFS_NAME, Context.MODE_PRIVATE)
        val savedType = ThermostatType.fromString(tprefs.getString(ThermostatType.PREFS_KEY, null))
        _uiState.update { it.copy(thermostatType = savedType) }

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
                        anomalyThreshold = prefs[ANOMALY_THRESHOLD_KEY] ?: 3.0f,
                        openWindowEnabled = prefs[OPEN_WINDOW_KEY] ?: true,
                        openWindowOffAction = prefs[OPEN_WINDOW_OFF_ACTION_KEY] ?: true,
                        themeMode = ThemeMode.fromString(prefs[THEME_KEY])
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
                    if (updateRepository.isSignatureCompatible(file) == false) {
                        _uiState.update {
                            it.copy(
                                updateStatus = UpdateStatus.Error(
                                    "Esta versión está firmada con otra clave y Android no puede instalarla " +
                                        "sobre la actual. Haz una copia de seguridad en Ajustes, desinstala la app, " +
                                        "instala la nueva y restaura la copia."
                                )
                            )
                        }
                        return@fold
                    }
                    val sizeMb = file.length().toFloat() / 1_048_576f
                    _uiState.update { it.copy(updateStatus = UpdateStatus.Installing(apkSizeMb = sizeMb, apkPath = file.absolutePath)) }
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
            _uiState.update { it.copy(isExporting = true, backupResult = null, exportError = null) }
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
                    it.copy(
                        isExporting = false,
                        backupResult = "Error al exportar el backup",
                        exportError = "No se pudo generar el archivo de backup. Revisa los permisos o el almacenamiento."
                    )
                }
            }
        }
    }

    fun clearExportError() {
        _uiState.update { it.copy(exportError = null) }
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[THEME_KEY] = mode.name }
        }
    }

    fun setThermostatType(type: ThermostatType) {
        val prefs = context.getSharedPreferences(ThermostatType.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ThermostatType.PREFS_KEY, type.name).apply()
        _uiState.update { it.copy(thermostatType = type) }
    }

    fun setOpenWindowEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[OPEN_WINDOW_KEY] = enabled }
        }
    }

    fun setOpenWindowOffAction(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[OPEN_WINDOW_OFF_ACTION_KEY] = enabled }
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

