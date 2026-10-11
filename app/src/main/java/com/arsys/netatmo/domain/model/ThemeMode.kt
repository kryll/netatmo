package com.arsys.netatmo.domain.model

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        const val PREFS_KEY = "theme_mode"

        fun fromString(value: String?) = when (value) {
            "LIGHT" -> LIGHT
            "SYSTEM" -> SYSTEM
            else -> DARK
        }
    }
}
