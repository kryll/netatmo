package com.arsys.netatmo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.arsys.netatmo.data.local.dao.*
import com.arsys.netatmo.data.local.entities.*

@Database(
    entities = [
        AutomationEntity::class,
        GeofenceEntity::class,
        ScheduleEntity::class,
        ScenarioEntity::class,
        TemperatureHistoryEntity::class,
        CalendarAutomationEntity::class,
        HomeCacheEntity::class,
        AutomationLogEntity::class
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
    abstract fun automationLogDao(): AutomationLogDao
}
