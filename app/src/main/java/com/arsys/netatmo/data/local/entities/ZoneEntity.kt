package com.arsys.netatmo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A named place (circle on the map) that advanced automations can use as a location trigger. */
@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float // metres
)
