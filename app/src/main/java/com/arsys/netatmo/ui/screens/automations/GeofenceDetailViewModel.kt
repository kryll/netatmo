package com.arsys.netatmo.ui.screens.automations

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.service.GeofenceManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

data class GeofenceDetailUiState(
    val name: String = "",
    val locationName: String = "Casa",
    val latitude: String = "",
    val longitude: String = "",
    val radius: Float = 200f,
    val triggerOnEnter: Boolean = true,
    val triggerOnExit: Boolean = true,
    val tempOnEnter: Double = 21.0,
    val tempOnExit: Double = 17.0,
    val isSaving: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GeofenceDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRepository: AutomationRepository,
    private val authRepository: AuthRepository,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeofenceDetailUiState())
    val uiState: StateFlow<GeofenceDetailUiState> = _uiState.asStateFlow()

    private var currentAutomationId: Long = -1L
    private var existingGeofenceId: String? = null

    fun load(automationId: Long) {
        if (automationId == -1L) return
        currentAutomationId = automationId
        viewModelScope.launch {
            val automation = automationRepository.getAutomationById(automationId) ?: return@launch
            val geofence = automationRepository.getGeofenceByAutomation(automationId)
            existingGeofenceId = geofence?.id
            _uiState.update { state ->
                state.copy(
                    name = automation.name,
                    locationName = geofence?.name ?: "Casa",
                    latitude = geofence?.latitude?.toString() ?: "",
                    longitude = geofence?.longitude?.toString() ?: "",
                    radius = geofence?.radius ?: 200f,
                    triggerOnEnter = geofence?.triggerOnEnter ?: true,
                    triggerOnExit = geofence?.triggerOnExit ?: true,
                    tempOnEnter = geofence?.temperatureOnEnter ?: 21.0,
                    tempOnExit = geofence?.temperatureOnExit ?: 17.0
                )
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateLocationName(name: String) = _uiState.update { it.copy(locationName = name) }
    fun updateLatitude(lat: String) = _uiState.update { it.copy(latitude = lat) }
    fun updateLongitude(lng: String) = _uiState.update { it.copy(longitude = lng) }
    fun updateRadius(radius: Float) = _uiState.update { it.copy(radius = radius) }
    fun updateTriggerOnEnter(value: Boolean) = _uiState.update { it.copy(triggerOnEnter = value) }
    fun updateTriggerOnExit(value: Boolean) = _uiState.update { it.copy(triggerOnExit = value) }
    fun updateTempOnEnter(temp: Double) = _uiState.update { it.copy(tempOnEnter = temp) }
    fun updateTempOnExit(temp: Double) = _uiState.update { it.copy(tempOnExit = temp) }

    @SuppressLint("MissingPermission")
    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true, error = null) }
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                val location = fusedClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()
                if (location != null) {
                    _uiState.update { it.copy(
                        latitude = location.latitude.toString(),
                        longitude = location.longitude.toString(),
                        isLoadingLocation = false
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoadingLocation = false,
                        error = "No se pudo obtener la ubicación. Activa el GPS e inténtalo de nuevo."
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoadingLocation = false,
                    error = "Error al obtener ubicación: ${e.message}"
                ) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        val lat = state.latitude.toDoubleOrNull()
        val lng = state.longitude.toDoubleOrNull()
        if (lat == null || lng == null) {
            _uiState.update { it.copy(error = "Coordenadas inválidas") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val homeId = authRepository.selectedHomeId.first() ?: ""
                val automation = AutomationEntity(
                    id = if (currentAutomationId == -1L) 0L else currentAutomationId,
                    name = state.name,
                    type = "GEOFENCE",
                    homeId = homeId,
                    roomId = "",
                    targetTemperature = state.tempOnEnter,
                    mode = "manual",
                    triggerData = "{}"
                )
                val automationId = automationRepository.saveAutomation(automation)

                val geofenceId = existingGeofenceId ?: UUID.randomUUID().toString()
                val geofence = GeofenceEntity(
                    id = geofenceId,
                    automationId = automationId,
                    name = state.locationName,
                    latitude = lat,
                    longitude = lng,
                    radius = state.radius,
                    triggerOnEnter = state.triggerOnEnter,
                    triggerOnExit = state.triggerOnExit,
                    temperatureOnEnter = if (state.triggerOnEnter) state.tempOnEnter else null,
                    temperatureOnExit = if (state.triggerOnExit) state.tempOnExit else null
                )
                automationRepository.saveGeofence(geofence)
                geofenceManager.registerGeofence(geofence)

                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
