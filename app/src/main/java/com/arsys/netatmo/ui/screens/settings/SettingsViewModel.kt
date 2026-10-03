package com.arsys.netatmo.ui.screens.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.model.GitHubRelease
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
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
    val updateStatus: UpdateStatus = UpdateStatus.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val thermostatRepository: ThermostatRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private companion object {
        val AUTO_REFRESH_KEY = booleanPreferencesKey("auto_refresh")
        val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")
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
                        notificationsEnabled = prefs[NOTIFICATIONS_KEY] ?: true
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
}
