package com.arsys.netatmo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenance_records")
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val type: String,
    val description: String,
    val technicianName: String = "",
    val certificateRef: String = "",
    val warrantyExtendedUntil: Long? = null
)
