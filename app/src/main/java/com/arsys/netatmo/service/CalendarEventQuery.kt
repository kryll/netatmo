package com.arsys.netatmo.service

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * A calendar event instance: its title, the start of THIS occurrence and the id of the event
 * it belongs to (stable per CalendarContract.Events row: a recurring series keeps one id for
 * all its occurrences, a modified occurrence gets its own).
 */
data class CalendarEventReading(val title: String, val startMs: Long, val eventId: Long)

/**
 * Reads calendar event instances in [fromMs, toMs] through CalendarContract.Instances, which
 * expands recurring events into each occurrence overlapping the window (the Events table only
 * stores the FIRST occurrence's DTSTART, so a repeating event would never match again).
 * All-day events are converted to local (device-zone) midnight of their date. Returns an
 * empty list without the
 * calendar permission or on a provider error; callers report the permission case themselves
 * via [hasCalendarPermission].
 */
object CalendarEventQuery {

    fun hasCalendarPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun queryEvents(
        context: Context,
        calendarId: Long,
        titleFilter: String?,
        fromMs: Long,
        toMs: Long
    ): List<CalendarEventReading> {
        if (!hasCalendarPermission(context)) return emptyList()
        val events = mutableListOf<CalendarEventReading>()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMs)
            ContentUris.appendId(this, toMs)
        }.build()
        val selection = "${CalendarContract.Instances.CALENDAR_ID} = ?"
        val args = arrayOf(calendarId.toString())
        try {
            context.contentResolver.query(
                uri, projection, selection, args,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0) ?: ""
                    // All-day instances come as UTC midnight; the trigger means local midnight.
                    val startMs = cursor.getLong(1).let {
                        if (cursor.getInt(2) == 1) AutomationTriggerMath.allDayLocalStart(it) else it
                    }
                    if (titleFilter == null || title.contains(titleFilter, ignoreCase = true)) {
                        events.add(CalendarEventReading(title, startMs, cursor.getLong(3)))
                    }
                }
            }
        } catch (e: SecurityException) {
            // Calendar permission revoked meanwhile.
        } catch (e: Exception) {
            // Provider unavailable or malformed calendar data: treat as no events.
        }
        return events
    }
}
