package com.arsys.netatmo.ui.screens.automations

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.ZoneEntity
import com.arsys.netatmo.data.repository.ZoneRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

/** Form being edited in the zone dialog. [id] is null for a new zone. */
data class ZoneForm(
    val id: String? = null,
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radius: Float = 150f,
    val isLoadingLocation: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            latitude.replace(',', '.').toDoubleOrNull()?.let { it in -90.0..90.0 } == true &&
            longitude.replace(',', '.').toDoubleOrNull()?.let { it in -180.0..180.0 } == true
}

@HiltViewModel
class ZonesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    val zones: StateFlow<List<ZoneEntity>> =
        zoneRepository.observeZones().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow<ZoneForm?>(null)
    val form: StateFlow<ZoneForm?> = _form.asStateFlow()

    /** Zone waiting for delete confirmation, with the enabled automations that use it. */
    private val _pendingDelete = MutableStateFlow<Pair<ZoneEntity, List<String>>?>(null)
    val pendingDelete: StateFlow<Pair<ZoneEntity, List<String>>?> = _pendingDelete.asStateFlow()

    fun startNew() { _form.value = ZoneForm() }

    fun startEdit(zone: ZoneEntity) {
        _form.value = ZoneForm(
            id = zone.id,
            name = zone.name,
            latitude = zone.latitude.toString(),
            longitude = zone.longitude.toString(),
            radius = zone.radius
        )
    }

    fun dismissForm() { _form.value = null }

    fun updateName(v: String) = _form.update { it?.copy(name = v) }
    fun updateLatitude(v: String) = _form.update { it?.copy(latitude = v) }
    fun updateLongitude(v: String) = _form.update { it?.copy(longitude = v) }
    fun updateRadius(v: Float) = _form.update { it?.copy(radius = v) }

    @SuppressLint("MissingPermission")
    fun useCurrentLocation() {
        viewModelScope.launch {
            _form.update { it?.copy(isLoadingLocation = true, error = null) }
            try {
                val cts = CancellationTokenSource()
                val location = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()
                if (location != null) {
                    _form.update {
                        it?.copy(
                            latitude = location.latitude.toString(),
                            longitude = location.longitude.toString(),
                            isLoadingLocation = false
                        )
                    }
                } else {
                    _form.update { it?.copy(isLoadingLocation = false, error = "No se pudo obtener la ubicación") }
                }
            } catch (e: SecurityException) {
                _form.update { it?.copy(isLoadingLocation = false, error = "Falta el permiso de ubicación") }
            } catch (e: Exception) {
                _form.update { it?.copy(isLoadingLocation = false, error = "No se pudo obtener la ubicación") }
            }
        }
    }

    fun save() {
        val f = _form.value ?: return
        if (!f.isValid) return
        viewModelScope.launch {
            zoneRepository.saveZone(
                ZoneEntity(
                    id = f.id ?: UUID.randomUUID().toString(),
                    name = f.name.trim(),
                    latitude = f.latitude.replace(',', '.').toDouble(),
                    longitude = f.longitude.replace(',', '.').toDouble(),
                    radius = f.radius
                )
            )
            _form.value = null
        }
    }

    fun askDelete(zone: ZoneEntity) {
        viewModelScope.launch {
            _pendingDelete.value = zone to zoneRepository.automationsUsing(zone.id)
        }
    }

    fun cancelDelete() { _pendingDelete.value = null }

    fun confirmDelete() {
        val zone = _pendingDelete.value?.first ?: return
        viewModelScope.launch {
            zoneRepository.deleteZone(zone)
            _pendingDelete.value = null
        }
    }
}
