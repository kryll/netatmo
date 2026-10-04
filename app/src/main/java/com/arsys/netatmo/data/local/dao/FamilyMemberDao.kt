package com.arsys.netatmo.data.local.dao

import androidx.room.*
import com.arsys.netatmo.data.local.entities.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Query("SELECT * FROM family_members ORDER BY name ASC")
    fun getAll(): Flow<List<FamilyMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: FamilyMemberEntity)

    @Delete
    suspend fun delete(m: FamilyMemberEntity)

    @Query("UPDATE family_members SET isHome = :home WHERE id = :id")
    suspend fun setPresence(id: Long, home: Boolean)

    @Query("SELECT COUNT(*) FROM family_members WHERE isHome = 1")
    suspend fun countMembersHome(): Int

    @Query("SELECT COUNT(*) FROM family_members")
    suspend fun countAll(): Int
}
