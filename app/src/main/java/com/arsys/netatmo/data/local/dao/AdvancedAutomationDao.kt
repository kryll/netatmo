package com.arsys.netatmo.data.local.dao

import androidx.room.*
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvancedAutomationDao {
    @Query("SELECT * FROM advanced_automations ORDER BY createdAt DESC")
    fun getAllAdvancedAutomations(): Flow<List<AdvancedAutomationEntity>>

    @Query("SELECT * FROM advanced_automations WHERE enabled = 1")
    suspend fun getEnabledAdvancedAutomations(): List<AdvancedAutomationEntity>

    @Query("SELECT * FROM advanced_automations WHERE id = :id")
    suspend fun getById(id: Long): AdvancedAutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AdvancedAutomationEntity): Long

    @Update
    suspend fun update(entity: AdvancedAutomationEntity)

    @Delete
    suspend fun delete(entity: AdvancedAutomationEntity)

    @Query("UPDATE advanced_automations SET lastTriggeredAt = :ts, lastTriggeredResult = :result WHERE id = :id")
    suspend fun updateLastTriggered(id: Long, ts: Long, result: String)
}
