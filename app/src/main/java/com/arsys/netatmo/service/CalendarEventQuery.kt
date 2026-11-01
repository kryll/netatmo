package com.arsys.netatmo.service

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** A calendar event instance: its title and the start of THIS occurrence. */
data class CalendarEventReading(val title: String, val startMs: Long)

/**
 * Reads calendar event instances in [fromMs, toMs] through CalendarContract.Instances, which
 * expands recurring events into each occurrence overlapping the window (the Events table only
 * stores the FIRST occurrence's DTSTART, so a repeating event would never match again).
 * All-day events come back at their UTC-midnight start. Returns an empty list without the
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
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
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
                    if (titleFilter == null || title.contains(titleFilter, ignoreCase = true)) {
                        events.add(CalendarEventReading(title, cursor.getLong(1)))
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
