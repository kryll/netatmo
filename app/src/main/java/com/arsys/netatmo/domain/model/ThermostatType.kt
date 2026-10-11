package com.arsys.netatmo.domain.model

enum class ThermostatType {
    /** Relay-only thermostat: controls boiler on/off, reads room temperature only. */
    STANDARD,

    /** OpenTherm thermostat: modulating, reads flow/return temp, pressure, modulation %. */
    OPENTHERM;

    companion object {
        const val PREFS_NAME = "thermostat_prefs"
        const val PREFS_KEY  = "thermostat_type"

        fun fromString(value: String?): ThermostatType =
            values().firstOrNull { it.name == value } ?: STANDARD
    }
}
