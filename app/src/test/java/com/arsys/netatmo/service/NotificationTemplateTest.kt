package com.arsys.netatmo.service

import org.junit.Assert.*
import org.junit.Test

class NotificationTemplateTest {
    private val rooms = listOf(
        NotificationTemplate.Room("h", "r1", "Salón", 21.25, 22.0, "schedule"),
        NotificationTemplate.Room("h", "r2", "Dormitorio", 19.0, 20.0, "manual")
    )

    @Test fun `plain text and percent signs stay literal`() {
        assertEquals("Batería 50% y 100% listo", NotificationTemplate.render("Batería 50% y 100% listo", emptyMap()))
        assertTrue(NotificationTemplate.variables("50% y %inexistente%").isEmpty())
    }
    @Test fun `replaces title and message repeated tokens`() {
        val values = NotificationTemplate.values("Frío", rooms, "h", null, -2.5)
        assertEquals("Frío: -2,5 °C", NotificationTemplate.render("%automatizacion%: %tem_ext% °C", values))
        assertEquals("Dormitorio: 19,0 / 19,0; 20,0 Manual", NotificationTemplate.render("%estancia%: %tem_int% / %tem_int%; %consigna% %modo%", values))
    }
    @Test fun `unknown malformed and missing tokens are safe`() {
        assertEquals("%unknown% %tem_ext no disponible", NotificationTemplate.render("%unknown% %tem_ext %tem_ext%", emptyMap()))
        assertEquals("no disponible", NotificationTemplate.render("%tem_int:missing%", emptyMap()))
    }
    @Test fun `values are never recursively expanded`() {
        assertEquals("%tem_ext%", NotificationTemplate.render("%automatizacion%", mapOf("automatizacion" to "%tem_ext%", "tem_ext" to "99")))
    }
    @Test fun `alert room overrides coldest room`() {
        val values = NotificationTemplate.values("Alerta", rooms, "h", "r1", null)
        assertEquals("Salón", values["estancia"])
        assertEquals("21,3", values["tem_int"])
    }
    @Test fun `explicit room tokens and summary include every room`() {
        val values = NotificationTemplate.values("Frío", rooms, "h", null, null)
        assertEquals("Salón 21,3 22,0 Programado", NotificationTemplate.render("%estancia:r1% %tem_int:r1% %consigna:r1% %modo:r1%", values))
        assertEquals("Salón: 21,3 °C; Dormitorio: 19,0 °C", values["temperaturas"])
    }
    @Test fun `missing nonfinite and zero temperatures`() {
        assertNull(NotificationTemplate.number(null))
        assertNull(NotificationTemplate.number(Double.NaN))
        assertNull(NotificationTemplate.number(Double.POSITIVE_INFINITY))
        assertEquals("0,0", NotificationTemplate.number(0.0))
        val values = NotificationTemplate.values("A", emptyList(), null, null, null)
        assertEquals("no disponible / no disponible", NotificationTemplate.render("%tem_ext% / %tem_int%", values))
    }
    @Test fun `multiple homes do not pick an arbitrary room`() {
        val all = rooms + NotificationTemplate.Room("other", "r3", "Otra casa", 10.0, 15.0, "away")
        val values = NotificationTemplate.values("A", all, null, null, null)
        assertNull(values["tem_int"])
        assertNull(values["temperaturas"])
        assertEquals("10,0", values["tem_int:r3"])
    }
    @Test fun `explicit missing home does not use another homes data`() {
        assertNull(NotificationTemplate.values("A", rooms, "missing", null, null)["tem_int"])
    }
    @Test fun `known room with missing temperature can still show setpoint`() {
        val values = NotificationTemplate.values("A", listOf(rooms[0].copy(temperature = null)), "h", "r1", null)
        assertEquals("no disponible 22,0", NotificationTemplate.render("%tem_int% %consigna%", values))
    }
    @Test fun `updated snapshot is used for each rendering`() {
        val first = NotificationTemplate.values("A", rooms, "h", "r1", 1.0)
        val second = NotificationTemplate.values("A", listOf(rooms[0].copy(temperature = 23.0)), "h", "r1", 2.0)
        assertEquals("21,3 1,0", NotificationTemplate.render("%tem_int% %tem_ext%", first))
        assertEquals("23,0 2,0", NotificationTemplate.render("%tem_int% %tem_ext%", second))
    }
}
