package com.arsys.netatmo.service

import java.util.Locale

/** Single-pass substitution: inserted values are never interpreted as templates. */
object NotificationTemplate {
    private val token = Regex("%([a-z_]+(?::[A-Za-z0-9_-]+)?)%")
    private val fixed = setOf("automatizacion", "tem_ext", "tem_int", "estancia", "consigna", "modo", "temperaturas")
    private val roomFields = setOf("tem_int", "consigna", "modo", "estancia")
    const val UNAVAILABLE = "no disponible"

    fun variables(text: String): Set<String> = token.findAll(text).map { it.groupValues[1] }
        .filter { key -> key in fixed || (key.substringBefore(':') in roomFields && ':' in key) }.toSet()

    fun render(text: String, values: Map<String, String?>): String = token.replace(text) { match ->
        val key = match.groupValues[1]
        if (key in variables(match.value)) values[key] ?: UNAVAILABLE else match.value
    }

    /** Numbers have no unit so the user can write "%tem_int% °C". */
    fun number(value: Double?): String? = value?.takeIf { it.isFinite() }
        ?.let { String.format(Locale.forLanguageTag("es-ES"), "%.1f", it) }

    data class Room(
        val homeId: String, val id: String, val name: String,
        val temperature: Double?, val setpoint: Double?, val mode: String?
    )

    fun values(name: String, rooms: List<Room>, homeId: String?, roomId: String?, outdoor: Double?): Map<String, String?> {
        // Never mix homes. Without an explicit alert, a single home is required.
        val home = homeId ?: rooms.map { it.homeId }.distinct().singleOrNull()
        val candidates = rooms.filter { it.homeId == home }
        val selected = if (roomId != null) candidates.find { it.id == roomId }
            else candidates.filter { it.temperature?.isFinite() == true }.minByOrNull { it.temperature!! }
        val result = mutableMapOf<String, String?>(
            "automatizacion" to name,
            "tem_ext" to number(outdoor),
            "temperaturas" to candidates.takeIf { it.isNotEmpty() }?.joinToString("; ") {
                "${it.name}: ${number(it.temperature)?.plus(" °C") ?: UNAVAILABLE}"
            }
        )
        fun add(suffix: String, room: Room?) {
            result["tem_int$suffix"] = number(room?.temperature)
            result["consigna$suffix"] = number(room?.setpoint)
            result["estancia$suffix"] = room?.name
            result["modo$suffix"] = room?.mode?.let {
                when (it) {
                    "schedule" -> "Programado"
                    "manual" -> "Manual"
                    "away" -> "Ausente"
                    "hg" -> "Anticongelación"
                    "off" -> "Apagado"
                    "max" -> "Máximo"
                    "home" -> "En casa"
                    else -> it
                }
            }
        }
        add("", selected)
        rooms.groupBy { it.id }.filterValues { it.size == 1 }.forEach { (id, matches) -> add(":$id", matches.single()) }
        return result
    }
}
