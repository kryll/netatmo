package com.arsys.netatmo.data.repository

import com.arsys.netatmo.data.local.dao.AdvancedAutomationDao
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedAutomationRepository @Inject constructor(
    private val dao: AdvancedAutomationDao
) {
    fun getAllAdvancedAutomations(): Flow<List<AdvancedAutomationEntity>> =
        dao.getAllAdvancedAutomations()

    suspend fun getEnabledAdvancedAutomations(): List<AdvancedAutomationEntity> =
        dao.getEnabledAdvancedAutomations()

    suspend fun getById(id: Long): AdvancedAutomationEntity? = dao.getById(id)

    suspend fun save(entity: AdvancedAutomationEntity): Long = dao.insert(entity)

    suspend fun update(entity: AdvancedAutomationEntity) = dao.update(entity)

    suspend fun delete(entity: AdvancedAutomationEntity) = dao.delete(entity)

    suspend fun updateLastTriggered(id: Long, ts: Long, result: String) =
        dao.updateLastTriggered(id, ts, result)
}
