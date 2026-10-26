package com.arsys.netatmo.service

import kotlinx.coroutines.sync.Mutex

/** Abstraction over the persistence side-effects of classic-automation migration. */
interface MigrationStore {
    suspend fun disableClassicAutomation(id: String, type: ClassicAutomationType)
    suspend fun insertAdvancedAutomation(automation: AdvancedAutomation)
    suspend fun rollback(item: MigrationRecord)
    suspend fun markMigrated(item: MigrationRecord)
    suspend fun listMigrated(): List<MigrationRecord>
}

/** Shared in-memory seam used by tests and by the migration flow. */
class FakeMigrationStore : MigrationStore {
    val disabled = mutableListOf<Pair<String, ClassicAutomationType>>()
    val inserted = mutableListOf<AdvancedAutomation>()
    val rolledBack = mutableListOf<MigrationRecord>()
    val migrated = mutableListOf<MigrationRecord>()
    private val mutex = Mutex()

    override suspend fun disableClassicAutomation(id: String, type: ClassicAutomationType) {
        disabled += id to type
    }

    override suspend fun insertAdvancedAutomation(automation: AdvancedAutomation) {
        inserted += automation
    }

    override suspend fun rollback(item: MigrationRecord) {
        rolledBack += item
    }

    override suspend fun markMigrated(item: MigrationRecord) {
        migrated += item
    }

    override suspend fun listMigrated(): List<MigrationRecord> = migrated.toList()
}
