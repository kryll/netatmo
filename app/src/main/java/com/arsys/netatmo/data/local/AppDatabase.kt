package com.arsys.netatmo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arsys.netatmo.data.local.dao.*
import com.arsys.netatmo.data.local.entities.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE calendar_automations ADD COLUMN eventTitleExactMatch INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Database(
    entities = [
        AutomationEntity::class,
        GeofenceEntity::class,
        ScheduleEntity::class,
        ScenarioEntity::class,
        TemperatureHistoryEntity::class,
        CalendarAutomationEntity::class,
        HomeCacheEntity::class
    ],
    version = 2,
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
}
