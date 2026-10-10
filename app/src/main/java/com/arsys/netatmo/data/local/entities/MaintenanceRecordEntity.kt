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
    val warrantyExtendedUntil: Long? = null,

    // Combustion analysis readings (from boiler service report / parte de trabajo)
    val rendimiento: Float? = null,          // Efficiency %
    val co: Float? = null,                   // CO ppm
    val coCorrected: Float? = null,          // CO corrected ppm
    val coAmbiente: Float? = null,           // Ambient CO ppm
    val co2: Float? = null,                  // CO2 %
    val o2: Float? = null,                   // O2 %
    val o2Ref: Float? = null,                // O2 reference %
    val temperaturaHumos: Float? = null,     // Flue gas temperature °C
    val temperaturaAmbiente: Float? = null,  // Ambient temperature °C
    val temperaturaACS: Float? = null,       // ACS (hot water) temperature °C
    val temperaturaAguaFria: Float? = null,  // Cold water inlet temperature °C
    val caudalAgua: Float? = null,           // Water flow L/min
    val potenciaACS: Float? = null,          // Instantaneous ACS power KW
    val presionDinamica: Float? = null,      // Dynamic gas pressure mbar
    val presionEstatica: Float? = null,      // Static gas pressure mbar
    val tiro: Float? = null,                 // Draft mbar
    val lambda: Float? = null,              // Lambda (excess air) %
    val perdidasChimenea: Float? = null,     // Chimney losses %
    val combustible: String? = null,         // Fuel type e.g. "GASNAT."
    val modelo: String? = null,              // Boiler model
    val version: String? = null,             // Firmware version
    val numSerie: String? = null,            // Serial number
    val visitTimeRange: String? = null       // e.g. "10:04 - 10:39"
)
