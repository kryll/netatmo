package com.arsys.netatmo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arsys.netatmo.data.local.entities.ZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones")
    suspend fun getAll(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: String): ZoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(zone: ZoneEntity)

    @Delete
    suspend fun delete(zone: ZoneEntity)
}
