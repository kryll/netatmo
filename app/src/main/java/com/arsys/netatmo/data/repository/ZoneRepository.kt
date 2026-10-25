package com.arsys.netatmo.data.repository

import com.arsys.netatmo.data.local.dao.ZoneDao
import com.arsys.netatmo.data.local.entities.ZoneEntity
import com.arsys.netatmo.service.GeofenceManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val zoneDao: ZoneDao,
    private val advancedAutomationRepository: AdvancedAutomationRepository,
    private val geofenceManager: GeofenceManager,
    private val geofenceStateStore: com.arsys.netatmo.service.GeofenceStateStore
) {
    fun observeZones(): Flow<List<ZoneEntity>> = zoneDao.observeAll()

    suspend fun getZone(id: String): ZoneEntity? = zoneDao.getById(id)

    suspend fun saveZone(zone: ZoneEntity) {
        zoneDao.upsert(zone)
        // The place may have moved: forget whether the phone was inside the old circle.
        geofenceStateStore.reset(GeofenceManager.zoneRequestId(zone.id))
        syncGeofences()
    }

    suspend fun deleteZone(zone: ZoneEntity) {
        zoneDao.delete(zone)
        syncGeofences()
    }

    /** Names of enabled advanced automations that use [zoneId] as a trigger. */
    suspend fun automationsUsing(zoneId: String): List<String> =
        advancedAutomationRepository.getEnabledAdvancedAutomations()
            .filter { zoneId in zoneIdsOf(it.triggersJson) }
            .map { it.name }

    /**
     * Makes Play Services watch exactly the zones that enabled advanced automations use as a
     * trigger, and stop watching the rest. Safe to call any time; needs location permission to add.
     */
    suspend fun syncGeofences() {
        val zones = zoneDao.getAll()
        val used = advancedAutomationRepository.getEnabledAdvancedAutomations()
            .flatMap { zoneIdsOf(it.triggersJson) }
            .toSet()
        geofenceManager.removeZones(zones.filter { it.id !in used }.map { it.id } +
            used.filter { id -> zones.none { it.id == id } })
        zones.filter { it.id in used }.forEach { zone ->
            try {
                geofenceManager.registerZone(zone)
            } catch (e: SecurityException) {
                // Location permission not granted yet; registered again on the next sync.
            }
        }
    }

    private fun zoneIdsOf(triggersJson: String): List<String> = try {
        val arr = JSONArray(triggersJson)
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "geofence" }
            .map { it.optString("zoneId") }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}
