package com.arsys.netatmo.data.local.dao

import androidx.room.*
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_records ORDER BY date DESC")
    fun getAll(): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records WHERE id = :id")
    suspend fun getById(id: Long): MaintenanceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MaintenanceRecordEntity): Long

    @Delete
    suspend fun delete(record: MaintenanceRecordEntity)

    @Query("DELETE FROM maintenance_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
