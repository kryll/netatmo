package com.arsys.netatmo.service

import com.arsys.netatmo.data.local.dao.FamilyMemberDao
import com.arsys.netatmo.data.local.entities.FamilyMemberEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyPresenceManager @Inject constructor(
    private val dao: FamilyMemberDao
) {

    /**
     * Returns true when all registered members are away (or when no members are registered,
     * treating the device owner as a single user who is always present/absent on their own).
     */
    suspend fun allMembersAway(): Boolean {
        val total = dao.countAll()
        if (total == 0) return true // no members registered = treat as single user
        return dao.countMembersHome() == 0
    }

    suspend fun setPresence(id: Long, isHome: Boolean) = dao.setPresence(id, isHome)

    suspend fun addMember(member: FamilyMemberEntity) = dao.insert(member)

    suspend fun deleteMember(member: FamilyMemberEntity) = dao.delete(member)

    fun getAllMembers(): Flow<List<FamilyMemberEntity>> = dao.getAll()
}
