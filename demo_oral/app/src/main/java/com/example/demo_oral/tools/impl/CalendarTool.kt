package com.example.demo_oral.tools.impl

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.demo_oral.R
import com.example.demo_oral.tools.ParamType
import com.example.demo_oral.tools.Tool
import com.example.demo_oral.tools.ToolArgs
import com.example.demo_oral.tools.ToolCard
import com.example.demo_oral.tools.ToolIcon
import com.example.demo_oral.tools.ToolParam
import com.example.demo_oral.tools.ToolResult
import com.example.demo_oral.tools.ToolSpec
import com.example.demo_oral.tools.resolve.TimeExpressions
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Adds an event to the phone's calendar, without opening any app. */
class CreateCalendarEventTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "create_calendar_event",
        description = "Creates an event in the calendar.",
        params = listOf(
            ToolParam("title", ParamType.STRING, "Title of the event", ask = context.getString(R.string.ask_event_title)),
            ToolParam(
                "day", ParamType.STRING, "Day of the event, as said by the user, e.g. \"tomorrow\" or \"friday\"",
                ask = context.getString(R.string.ask_event_day),
            ),
            ToolParam("time", ParamType.STRING, "Start time, as said by the user, e.g. \"15:30\"", required = false),
            ToolParam("duration_minutes", ParamType.INTEGER, "Length of the event in minutes", required = false),
        ),
        keywords = listOf("calendar", "agenda", "evento", "event", "cita", "reunion", "rendez", "meeting", "appointment", "recuerd", "recordatorio"),
        permissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
    )

    override fun card(args: ToolArgs): ToolCard {
        val day = args.string("day")
        val time = args.string("time")
        return ToolCard(
            icon = ToolIcon.CALENDAR,
            title = context.getString(R.string.card_calendar),
            fields = listOfNotNull(
                args.string("title")?.let { context.getString(R.string.field_title) to it },
                day?.let {
                    context.getString(R.string.field_day) to
                        (TimeExpressions.parseDay(it)?.format(DAY_FORMAT) ?: it)
                },
                time?.let {
                    context.getString(R.string.field_time) to
                        (TimeExpressions.parseTime(it)?.format(TIME_FORMAT) ?: it)
                },
            ),
        )
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val title =args.string("title") ?: return ToolResult.Failed(context.getString(R.string.tool_title_unclear))
        val day = args.string("day")?.let { TimeExpressions.parseDay(it) }
            ?: return ToolResult.Failed(context.getString(R.string.tool_date_unclear))
        // No time given: an hour-long event at 9:00
        val time = args.string("time")?.let(TimeExpressions::parseTime) ?: LocalTime.of(9, 0)
        val minutes = (args.int("duration_minutes") ?: DEFAULT_MINUTES).coerceIn(1, 24 * 60)
        val calendarId = writableCalendarId()
            ?: return ToolResult.Failed(context.getString(R.string.tool_calendar_missing))

        val zone = ZoneId.systemDefault()
        val start = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + minutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        val created = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return if (created != null) {
            ToolResult.Success(
                context.getString(
                    R.string.tool_calendar_created, title, day.format(DAY_FORMAT), time.format(TIME_FORMAT)
                )
            )
        } else {
            ToolResult.Failed(context.getString(R.string.tool_calendar_failed))
        }
    }

    /** The primary calendar if it is writable, otherwise any writable one. */
    private fun writableCalendarId(): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) return it.getLong(0) }
        return null
    }

    private companion object {
        const val DEFAULT_MINUTES = 60
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("es", "ES"))
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
