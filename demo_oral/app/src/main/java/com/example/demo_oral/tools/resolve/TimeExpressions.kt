package com.example.demo_oral.tools.resolve

import com.example.demo_oral.tools.ToolRegistry
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The model only extracts what the user said ("mañana", "7:30"); turning it into real dates and
 * times is done here, where it is exact.
 */
object TimeExpressions {

    /** "7:30", "19h30", "7.30", "7", "7pm", "las 7 y media", "siete" are all understood. */
    fun parseTime(text: String): LocalTime? {
        val t = ToolRegistry.fold(text).replace(Regex("\\s+"), " ").trim()
        val pm = Regex("\\b(pm|de la tarde|de la noche|du soir)\\b").containsMatchIn(t)
        val am = Regex("\\b(am|de la manana|du matin)\\b").containsMatchIn(t)

        val numeric = Regex("(\\d{1,2})\\s*(?:[:h.]\\s*(\\d{2}))?").find(t)
        var hour: Int
        var minute = 0
        if (numeric != null) {
            hour = numeric.groupValues[1].toInt()
            numeric.groupValues[2].takeIf { it.isNotEmpty() }?.let { minute = it.toInt() }
        } else {
            hour = t.split(' ').firstNotNullOfOrNull { NUMBER_WORDS[it] } ?: return null
        }
        if (minute == 0) {
            if (Regex("\\by media\\b|\\bet demie\\b").containsMatchIn(t)) minute = 30
            else if (Regex("\\by cuarto\\b|\\bet quart\\b").containsMatchIn(t)) minute = 15
        }
        if (pm && hour in 1..11) hour += 12
        if (am && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** "hoy", "mañana", "pasado mañana", "el viernes", "25/12", "tomorrow", "demain". */
    fun parseDay(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val t = ToolRegistry.fold(text).trim()
        return when {
            Regex("\\b(hoy|today|aujourd)").containsMatchIn(t) -> today
            Regex("pasado manana|after tomorrow|apres demain").containsMatchIn(t) -> today.plusDays(2)
            Regex("\\b(manana|tomorrow|demain)\\b").containsMatchIn(t) -> today.plusDays(1)
            else -> dayOfWeek(t)?.let { wanted ->
                // The next such day, never today
                today.plusDays(((wanted.value - today.dayOfWeek.value + 6) % 7 + 1).toLong())
            } ?: dayAndMonth(t, today)
        }
    }

    private fun dayOfWeek(t: String): DayOfWeek? = WEEKDAYS.entries
        .firstOrNull { (name, _) -> Regex("\\b$name").containsMatchIn(t) }?.value

    private fun dayAndMonth(t: String, today: LocalDate): LocalDate? {
        val match = Regex("(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})").find(t) ?: return null
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        return try {
            val date = LocalDate.of(today.year, month, day)
            if (date.isBefore(today)) date.plusYears(1) else date
        } catch (_: DateTimeException) {
            null
        }
    }

    private val NUMBER_WORDS = mapOf(
        "una" to 1, "un" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
        "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    private val WEEKDAYS = mapOf(
        "lunes" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY, "lundi" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY, "mardi" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "mercredi" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY, "jeudi" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY, "vendredi" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY, "samedi" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY, "dimanche" to DayOfWeek.SUNDAY,
    )
}
