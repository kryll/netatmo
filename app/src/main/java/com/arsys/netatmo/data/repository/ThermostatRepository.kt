package com.arsys.netatmo.data.repository

import com.arsys.netatmo.data.api.NetatmoApiService
import com.arsys.netatmo.data.api.models.*
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.local.dao.TemperatureHistoryDao
import com.arsys.netatmo.data.local.entities.HomeCacheEntity
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

@Singleton
class ThermostatRepository @Inject constructor(
    private val apiService: NetatmoApiService,
    private val authRepository: AuthRepository,
    private val homeCacheDao: HomeCacheDao,
    private val historyDao: TemperatureHistoryDao
) {
    private val gson = Gson()

    suspend fun getHomesData(): ApiResult<List<Home>> {
        return try {
            val response = apiService.getHomesData()
            if (response.isSuccessful) {
                val homes = response.body()?.body?.homes ?: emptyList()
                homes.forEach { home ->
                    homeCacheDao.insertHome(
                        HomeCacheEntity(
                            homeId = home.id,
                            name = home.name,
                            country = home.country,
                            timezone = home.timezone,
                            roomsJson = gson.toJson(home.rooms),
                            modulesJson = gson.toJson(home.modules)
                        )
                    )
                }
                ApiResult.Success(homes)
            } else {
                ApiResult.Error("Error ${response.code()}: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error desconocido")
        }
    }

    suspend fun getHomeStatus(homeId: String): ApiResult<HomeStatus> {
        return try {
            val response = apiService.getHomeStatus(homeId)
            if (response.isSuccessful) {
                val homeStatus = response.body()?.body?.home
                    ?: return ApiResult.Error("Respuesta vacía")

                homeStatus.rooms?.forEach { room ->
                    room.measuredTemperature?.let { temp ->
                        historyDao.insertHistory(
                            TemperatureHistoryEntity(
                                homeId = homeId,
                                roomId = room.id,
                                timestamp = System.currentTimeMillis(),
                                temperature = temp,
                                setpoint = room.setpointTemperature,
                                heatingActive = (room.heatingPower ?: 0) > 0
                            )
                        )
                    }
                }

                ApiResult.Success(homeStatus)
            } else {
                ApiResult.Error("Error ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun setTemperature(
        homeId: String,
        roomId: String,
        temperature: Double,
        durationMinutes: Int = 60
    ): ApiResult<Unit> {
        return try {
            val endTime = if (durationMinutes > 0)
                (System.currentTimeMillis() / 1000) + (durationMinutes * 60L)
            else null
            val response = apiService.setRoomThermpoint(
                homeId = homeId,
                roomId = roomId,
                mode = "manual",
                temperature = temperature,
                endTime = endTime
            )
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun setMode(
        homeId: String,
        roomId: String,
        mode: String
    ): ApiResult<Unit> {
        return try {
            // away, hg (frost guard) and off are home-level modes — must use setthermmode
            val response = if (mode in listOf("away", "hg", "off")) {
                apiService.setThermMode(homeId, mode)
            } else {
                // "schedule" cancels a manual override; "manual" (without temp) resets to schedule too
                apiService.setRoomThermpoint(homeId = homeId, roomId = roomId, mode = mode)
            }
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}: ${response.errorBody()?.string()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun setHomeMode(homeId: String, mode: String, endTime: Long? = null): ApiResult<Unit> {
        return try {
            val response = apiService.setThermMode(homeId, mode, endTime)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun switchSchedule(homeId: String, scheduleId: String): ApiResult<Unit> {
        return try {
            val response = apiService.switchHomeSchedule(homeId, scheduleId)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun syncSchedule(homeId: String, body: Map<String, Any>): ApiResult<Unit> {
        return try {
            val response = apiService.syncHomeSchedule(body)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    suspend fun createSchedule(homeId: String, body: Map<String, Any>): ApiResult<Unit> {
        return try {
            val response = apiService.createHomeSchedule(body)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Error ${response.code()}", response.code())
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Error de red")
        }
    }

    fun getTemperatureHistory(
        homeId: String,
        roomId: String,
        fromTimestamp: Long
    ): Flow<List<TemperatureHistoryEntity>> =
        historyDao.getHistory(homeId, roomId, fromTimestamp)

    suspend fun getRoomsForHome(homeId: String): List<Room> {
        val cache = homeCacheDao.getHome(homeId) ?: return emptyList()
        return try {
            gson.fromJson(cache.roomsJson, Array<Room>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches outdoor temperature from the Weather Station API (getstationsdata).
     * Looks for an NAModule1 (outdoor sensor) in the station's modules list.
     */
    suspend fun getOutdoorTemperature(homeId: String): Double? {
        return try {
            // Primary: Weather Station API returns real-time NAModule1 data
            val stationsResponse = apiService.getStationsData()
            if (stationsResponse.isSuccessful) {
                val outdoorTemp = stationsResponse.body()?.body?.devices
                    ?.flatMap { device -> device.modules ?: emptyList() }
                    ?.find { it.type == "NAModule1" }
                    ?.dashboardData?.temperature
                if (outdoorTemp != null) return outdoorTemp
            }
            // Fallback: Energy API + getmeasure (works if NAModule1 is bridged via NAMain)
            val homesResult = getHomesData()
            if (homesResult is ApiResult.Success) {
                val home = homesResult.data.find { it.id == homeId }
                val outdoorModule = home?.modules?.find { it.type == "NAModule1" }
                val bridgeId = outdoorModule?.bridge
                if (outdoorModule != null && bridgeId != null) {
                    val response = apiService.getMeasure(
                        deviceId = bridgeId,
                        moduleId = outdoorModule.id,
                        scale = "max",
                        type = "temperature",
                        limit = 1,
                        realTime = true
                    )
                    if (response.isSuccessful) {
                        return response.body()?.body
                            ?.lastOrNull()
                            ?.value
                            ?.lastOrNull()
                            ?.firstOrNull()
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("ThermostatRepo", "getOutdoorTemperature failed", e)
            null
        }
    }
}
