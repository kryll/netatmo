package com.arsys.netatmo.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetatmoCredentialsUiState(
    val clientId: String = "",
    val clientSecret: String = "",
    val hasStoredCredentials: Boolean = false,
    val storedClientIdPreview: String = "",
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NetatmoCredentialsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetatmoCredentialsUiState())
    val uiState: StateFlow<NetatmoCredentialsUiState> = _uiState.asStateFlow()

    init {
        val storedId = authRepository.getStoredClientId()
        _uiState.update {
            it.copy(
                hasStoredCredentials = storedId != null,
                storedClientIdPreview = storedId?.let { id ->
                    if (id.length > 8) id.take(8) + "…" else id
                } ?: ""
            )
        }
    }

    fun updateClientId(value: String) = _uiState.update { it.copy(clientId = value, error = null) }

    fun updateClientSecret(value: String) = _uiState.update { it.copy(clientSecret = value, error = null) }

    fun save() {
        val id = _uiState.value.clientId.trim()
        val secret = _uiState.value.clientSecret.trim()
        if (id.isBlank() || secret.isBlank()) {
            _uiState.update { it.copy(error = "Rellena ambos campos") }
            return
        }
        viewModelScope.launch {
            authRepository.saveCredentials(id, secret)
            _uiState.update {
                it.copy(
                    isSaved = true,
                    hasStoredCredentials = true,
                    storedClientIdPreview = if (id.length > 8) id.take(8) + "…" else id,
                    clientId = "",
                    clientSecret = ""
                )
            }
        }
    }

    fun clearCredentials() {
        authRepository.clearCredentials()
        _uiState.update {
            it.copy(hasStoredCredentials = false, storedClientIdPreview = "", isSaved = false)
        }
    }
}
