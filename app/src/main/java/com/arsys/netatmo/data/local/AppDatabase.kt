package com.arsys.netatmo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arsys.netatmo.data.local.dao.*
import com.arsys.netatmo.data.local.dao.AdvancedAutomationDao
import com.arsys.netatmo.data.local.dao.FamilyMemberDao
import com.arsys.netatmo.data.local.dao.MaintenanceDao
import com.arsys.netatmo.data.local.entities.*
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity

@Database(
    entities = [
        AutomationEntity::class,
        GeofenceEntity::class,
        ScheduleEntity::class,
        ScenarioEntity::class,
        TemperatureHistoryEntity::class,
        CalendarAutomationEntity::class,
        HomeCacheEntity::class,
        AutomationLogEntity::class,
        FamilyMemberEntity::class,
        AdvancedAutomationEntity::class,
        MaintenanceRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun temperatureHistoryDao(): TemperatureHistoryDao
    abstract fun calendarAutomationDao(): CalendarAutomationDao
    abstract fun homeCacheDao(): HomeCacheDao
    abstract fun automationLogDao(): AutomationLogDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun advancedAutomationDao(): AdvancedAutomationDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS maintenance_records " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "date INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "technicianName TEXT NOT NULL DEFAULT '', " +
                    "certificateRef TEXT NOT NULL DEFAULT '', " +
                    "warrantyExtendedUntil INTEGER)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `advanced_automations` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL DEFAULT '', " +
                    "`enabled` INTEGER NOT NULL DEFAULT 1, " +
                    "`triggersJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`conditionsJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`actionsJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`triggerMode` TEXT NOT NULL DEFAULT 'all', " +
                    "`mode` TEXT NOT NULL DEFAULT 'single', " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`lastTriggeredAt` INTEGER, " +
                    "`lastTriggeredResult` TEXT)"
                )
            }
        }
    }
}
