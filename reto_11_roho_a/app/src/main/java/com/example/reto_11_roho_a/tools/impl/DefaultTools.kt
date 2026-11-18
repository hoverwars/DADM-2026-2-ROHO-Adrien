package com.example.reto_11_roho_a.tools.impl

import android.content.Context
import com.example.reto_11_roho_a.tools.Tool

/** Every tool the assistant can use. Adding an action to the assistant = adding a line here. */
fun defaultTools(context: Context): List<Tool> = listOf(
    SetAlarmTool(context),
    SetTimerTool(context),
    FlashlightTool(context, turnOn = true),
    FlashlightTool(context, turnOn = false),
    BatteryTool(context),
    SetVolumeTool(context),
    CreateCalendarEventTool(context),
    SendSmsTool(context),
    CallContactTool(context),
)
