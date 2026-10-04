package com.arsys.netatmo.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.arsys.netatmo.data.local.dao.ScenarioDao
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.GeofenceEntity
import com.arsys.netatmo.data.local.entities.ScenarioEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val scenarioDao: ScenarioDao
) {
    private val gson = Gson()

    suspend fun export(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val automations = automationRepository.getAllAutomations().first()
            val geofences = automationRepository.getAllGeofences().first()
            val scenarios = scenarioDao.getAllScenarios().first()

            val json = JSONObject().apply {
                put("version", 1)
                put("automations", gson.toJson(automations))
                put("geofences", gson.toJson(geofences))
                put("scenarios", gson.toJson(scenarios))
            }

            val dir = File(context.cacheDir, "backup").also { it.mkdirs() }
            val file = File(dir, "netatmo_backup_${System.currentTimeMillis()}.json")
            file.writeText(json.toString(2))

            FileProvider.getUriForFile(context, "com.arsys.netatmo.provider", file)
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            null
        }
    }

    suspend fun import(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: return@withContext Result.failure(IllegalStateException("Cannot open URI"))

            val json = JSONObject(text)
            val automationsJson = json.getString("automations")
            val geofencesJson = json.getString("geofences")
            val scenariosJson = json.getString("scenarios")

            val automationListType = object : TypeToken<List<AutomationEntity>>() {}.type
            val automations: List<AutomationEntity> = gson.fromJson(automationsJson, automationListType)

            val geofenceListType = object : TypeToken<List<GeofenceEntity>>() {}.type
            val geofences: List<GeofenceEntity> = gson.fromJson(geofencesJson, geofenceListType)

            val scenarioListType = object : TypeToken<List<ScenarioEntity>>() {}.type
            val scenarios: List<ScenarioEntity> = gson.fromJson(scenariosJson, scenarioListType)

            var count = 0

            automations.forEach { automation ->
                automationRepository.saveAutomation(automation.copy(id = 0))
                count++
            }

            geofences.forEach { geofence ->
                automationRepository.saveGeofence(geofence)
                count++
            }

            scenarios.forEach { scenario ->
                scenarioDao.insertScenario(scenario.copy(id = 0))
                count++
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
