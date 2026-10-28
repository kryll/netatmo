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
     * Returns true when all registered members are away. With no members registered there is
     * no presence data: report "someone home" (false) so an "everyone away" condition never
     * fires away-actions without evidence, and a "someone home" condition does not silently
     * block the automations of a single-user household.
     */
    suspend fun allMembersAway(): Boolean {
        val total = dao.countAll()
        if (total == 0) return false // no presence data: assume home, the safe default
        return dao.countMembersHome() == 0
    }

    suspend fun setPresence(id: Long, isHome: Boolean) = dao.setPresence(id, isHome)

    suspend fun addMember(member: FamilyMemberEntity) = dao.insert(member)

    suspend fun deleteMember(member: FamilyMemberEntity) = dao.delete(member)

    fun getAllMembers(): Flow<List<FamilyMemberEntity>> = dao.getAll()
}
