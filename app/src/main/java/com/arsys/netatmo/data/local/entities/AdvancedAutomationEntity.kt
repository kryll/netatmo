package com.arsys.netatmo.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "advanced_automations")
data class AdvancedAutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val triggersJson: String = "[]",   // JSON array of trigger objects
    val conditionsJson: String = "[]", // JSON array of condition objects
    val actionsJson: String = "[]",    // JSON array of action objects
    val triggerMode: String = "any",   // "any" | "all"
    val mode: String = "single",       // "single" | "restart" | "queued"
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val lastTriggeredResult: String? = null // "success" | "skipped" | "error"
)
