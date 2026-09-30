package com.example.demo_oral.tools.impl

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
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
import java.time.format.DateTimeFormatter

/** Sets an alarm in the clock app, without opening it (SKIP_UI). */
class SetAlarmTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "set_alarm",
        description = "Sets an alarm that rings at a given time of the day.",
        params = listOf(
            ToolParam("time", ParamType.STRING, "Time of the day, as said by the user, e.g. \"7:30\" or \"19h\""),
            ToolParam("label", ParamType.STRING, "Optional name of the alarm", required = false),
        ),
        keywords = listOf("alarm", "despiert", "desperta", "wake", "reveil", "reveill"),
    )

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.ALARM,
        title = context.getString(R.string.card_alarm),
        fields = listOfNotNull(
            context.getString(R.string.field_time) to
                (args.string("time")?.let(TimeExpressions::parseTime)?.format(HOUR_FORMAT) ?: args.string("time").orEmpty()),
            args.string("label")?.let { context.getString(R.string.field_label) to it },
        ),
    )

    override suspend fun execute(args: ToolArgs): ToolResult {
        val time = args.string("time")?.let(TimeExpressions::parseTime)
            ?: return ToolResult.Failed(context.getString(R.string.tool_time_unclear))
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        args.string("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return if (context.startSafely(intent)) {
            ToolResult.Success(context.getString(R.string.tool_alarm_set, time.format(HOUR_FORMAT)))
        } else {
            ToolResult.Failed(context.getString(R.string.tool_alarm_failed))
        }
    }

    private companion object {
        val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Starts a countdown in the clock app, without opening it (SKIP_UI). */
class SetTimerTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "set_timer",
        description = "Starts a countdown timer that rings after a given duration.",
        params = listOf(
            ToolParam("minutes", ParamType.INTEGER, "Number of minutes", required = false),
            ToolParam("seconds", ParamType.INTEGER, "Number of seconds", required = false),
        ),
        keywords = listOf("temporiz", "timer", "minuteur", "cronom", "countdown"),
    )

    private fun totalSeconds(args: ToolArgs) = (args.int("minutes") ?: 0) * 60 + (args.int("seconds") ?: 0)

    private fun durationText(total: Int) = when {
        total % 60 == 0 -> context.getString(R.string.tool_duration_minutes, total / 60)
        total < 60 -> context.getString(R.string.tool_duration_seconds, total)
        else -> context.getString(R.string.tool_duration_both, total / 60, total % 60)
    }

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.TIMER,
        title = context.getString(R.string.card_timer),
        fields = totalSeconds(args).takeIf { it > 0 }
            ?.let { listOf(context.getString(R.string.field_duration) to durationText(it)) }
            .orEmpty(),
    )

    override suspend fun execute(args: ToolArgs): ToolResult {
        val total = totalSeconds(args)
        if (total !in 1..MAX_SECONDS) return ToolResult.Failed(context.getString(R.string.tool_duration_unclear))
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, total)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val spoken = durationText(total)
        return if (context.startSafely(intent)) {
            ToolResult.Success(context.getString(R.string.tool_timer_set, spoken))
        } else {
            ToolResult.Failed(context.getString(R.string.tool_timer_failed))
        }
    }

    private companion object {
        const val MAX_SECONDS = 24 * 3600
    }
}
