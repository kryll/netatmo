package com.arsys.netatmo.domain.usecase

import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.api.models.HomeStatus
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetThermostatStateUseCase @Inject constructor(
    private val repository: ThermostatRepository
) {
    suspend operator fun invoke(homeId: String): ApiResult<ThermostatState> {
        val homesResult = repository.getHomesData()
        val home = when (homesResult) {
            is ApiResult.Success -> homesResult.data.find { it.id == homeId }
            else -> null
        }

        return when (val statusResult = repository.getHomeStatus(homeId)) {
            is ApiResult.Success -> {
                ApiResult.Success(statusResult.data.toDomainModel(home))
            }
            is ApiResult.Error -> statusResult
            ApiResult.Loading -> ApiResult.Loading
        }
    }

    private fun HomeStatus.toDomainModel(home: Home?): ThermostatState {
        val roomNames = home?.rooms?.associate { it.id to it.name } ?: emptyMap()

        return ThermostatState(
            homeId = id,
            homeName = home?.name ?: "Mi hogar",
            rooms = rooms?.map { room ->
                RoomState(
                    id = room.id,
                    name = roomNames[room.id] ?: "Habitación",
                    currentTemp = room.measuredTemperature,
                    targetTemp = room.setpointTemperature,
                    mode = ThermostatMode.fromApi(room.setpointMode),
                    heatingActive = (room.heatingPower ?: 0) > 0,
                    reachable = room.reachable ?: false,
                    setpointEndTime = room.setpointEndTime
                )
            } ?: emptyList(),
            modules = modules?.map { module ->
                ModuleState(
                    id = module.id,
                    type = module.type ?: "",
                    reachable = module.reachable ?: false,
                    batteryLevel = module.batteryLevel,
                    rfStrength = module.rfStrength,
                    wifiStrength = module.wifiStrength,
                    boilerStatus = module.boilerStatus
                )
            } ?: emptyList()
        )
    }
}

class SetTemperatureUseCase @Inject constructor(
    private val repository: ThermostatRepository
) {
    suspend operator fun invoke(
        homeId: String,
        roomId: String,
        temperature: Double,
        durationMinutes: Int = 60
    ): ApiResult<Unit> = repository.setTemperature(homeId, roomId, temperature, durationMinutes)
}

class SetModeUseCase @Inject constructor(
    private val repository: ThermostatRepository
) {
    suspend operator fun invoke(
        homeId: String,
        roomId: String,
        mode: ThermostatMode
    ): ApiResult<Unit> = repository.setMode(homeId, roomId, mode.apiValue)

    suspend fun setHomeMode(
        homeId: String,
        mode: ThermostatMode,
        endTime: Long? = null
    ): ApiResult<Unit> = repository.setHomeMode(homeId, mode.apiValue, endTime)
}

class GetTemperatureHistoryUseCase @Inject constructor(
    private val repository: ThermostatRepository
) {
    operator fun invoke(
        homeId: String,
        roomId: String,
        days: Int = 7
    ): Flow<List<TemperatureDataPoint>> {
        val fromTimestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return repository.getTemperatureHistory(homeId, roomId, fromTimestamp)
            .map { entities ->
                entities.map { entity ->
                    TemperatureDataPoint(
                        timestamp = entity.timestamp,
                        temperature = entity.temperature,
                        setpoint = entity.setpoint,
                        heatingActive = entity.heatingActive
                    )
                }
            }
    }
}

class GetHomesUseCase @Inject constructor(
    private val repository: ThermostatRepository
) {
    suspend operator fun invoke(): ApiResult<List<Home>> = repository.getHomesData()
}
