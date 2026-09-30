package com.example.demo_oral.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import com.example.demo_oral.R
import com.example.demo_oral.tools.ParamType
import com.example.demo_oral.tools.Tool
import com.example.demo_oral.tools.ToolArgs
import com.example.demo_oral.tools.ToolCard
import com.example.demo_oral.tools.ToolIcon
import com.example.demo_oral.tools.ToolParam
import com.example.demo_oral.tools.ToolResult
import com.example.demo_oral.tools.ToolSpec

private val FLASHLIGHT_KEYWORDS = listOf("linterna", "flash", "torch", "flashlight", "lampe", "lanterna")

/** Turns the flashlight on or off (no permission needed). */
class FlashlightTool(private val context: Context, private val turnOn: Boolean) : Tool {
    override val spec = ToolSpec(
        name = if (turnOn) "turn_on_flashlight" else "turn_off_flashlight",
        description = if (turnOn) "Turns the flashlight on." else "Turns the flashlight off.",
        keywords = FLASHLIGHT_KEYWORDS,
    )

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.FLASHLIGHT,
        title = context.getString(R.string.card_flashlight),
        fields = listOf(
            context.getString(R.string.field_action) to
                context.getString(if (turnOn) R.string.value_turn_on else R.string.value_turn_off)
        ),
    )

    override suspend fun execute(args: ToolArgs): ToolResult {
        val manager = context.getSystemService(CameraManager::class.java)
        val camera = manager.cameraIdList.firstOrNull { id ->
            val c = manager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return ToolResult.Failed(context.getString(R.string.tool_flashlight_missing))
        return try {
            manager.setTorchMode(camera, turnOn)
            ToolResult.Success(
                context.getString(if (turnOn) R.string.tool_flashlight_on else R.string.tool_flashlight_off)
            )
        } catch (_: CameraAccessException) {
            ToolResult.Failed(context.getString(R.string.tool_flashlight_busy))
        }
    }
}

/** Reads the battery level. */
class BatteryTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "get_battery_level",
        description = "Tells how much battery the phone has left and whether it is charging.",
        keywords = listOf("bateria", "battery", "batterie", "carga"),
    )

    override fun card(args: ToolArgs) = ToolCard(ToolIcon.BATTERY, context.getString(R.string.card_battery))

    override suspend fun execute(args: ToolArgs): ToolResult {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }
        return ToolResult.Success(
            context.getString(if (charging) R.string.tool_battery_charging else R.string.tool_battery, level)
        )
    }
}

/** Sets the media volume. */
class SetVolumeTool(private val context: Context) : Tool {
    override val spec = ToolSpec(
        name = "set_volume",
        description = "Sets the media volume of the phone.",
        params = listOf(ToolParam("level", ParamType.INTEGER, "Volume in percent, from 0 to 100")),
        keywords = listOf("volum"),
    )

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.VOLUME,
        title = context.getString(R.string.card_volume),
        fields = args.int("level")
            ?.let { listOf(context.getString(R.string.field_level) to "${it.coerceIn(0, 100)} %") }
            .orEmpty(),
    )

    override suspend fun execute(args: ToolArgs): ToolResult {
        val percent =(args.int("level") ?: return ToolResult.Failed(context.getString(R.string.tool_volume_unclear)))
            .coerceIn(0, 100)
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * percent / 100f), 0)
        return ToolResult.Success(context.getString(R.string.tool_volume_set, percent))
    }
}
