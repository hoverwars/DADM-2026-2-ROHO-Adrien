package com.example.demo_oral.tools.impl

import android.content.Context
import com.example.demo_oral.tools.Tool

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
