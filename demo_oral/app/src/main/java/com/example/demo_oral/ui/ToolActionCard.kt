package com.example.demo_oral.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.demo_oral.R
import com.example.demo_oral.tools.ToolAction
import com.example.demo_oral.tools.ToolCard
import com.example.demo_oral.tools.ToolIcon
import com.example.demo_oral.tools.ToolStatus

/**
 * An action of the assistant in the conversation: what it does, with which values, and how far
 * it got. Waiting states carry the buttons to answer with (the voice works too).
 *
 * [text] is what the assistant said about it (the question, or the result).
 */
@Composable
fun ToolActionCard(
    action: ToolAction,
    text: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val waiting = action.status == ToolStatus.AWAITING_CONFIRMATION || action.status == ToolStatus.AWAITING_PERMISSION
    val accent = when (action.status) {
        ToolStatus.DONE -> colors.primary
        ToolStatus.FAILED, ToolStatus.DENIED -> colors.error
        ToolStatus.CANCELLED, ToolStatus.RUNNING -> colors.outline
        else -> if (action.risky) colors.tertiary else colors.secondary
    }
    val container = if (waiting && action.risky) colors.tertiaryContainer else colors.surfaceVariant
    val label = stringResource(action.status.label())
    val spokenDescription = buildString {
        append(action.card.title).append(", ").append(label)
        action.card.fields.forEach { (name, value) -> append(", ").append(name).append(' ').append(value) }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = container,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .semantics(mergeDescendants = true) { contentDescription = spokenDescription },
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                Column(Modifier.padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                    Header(action, label, accent)
                    if (action.card.fields.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Fields(action.card, struck = action.status == ToolStatus.CANCELLED)
                    }
                    if (text.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (action.status == ToolStatus.FAILED || action.status == ToolStatus.DENIED) colors.error
                            else colors.onSurface,
                        )
                    }
                    Footer(action, onConfirm, onCancel, onGrant, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun Header(action: ToolAction, statusLabel: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = action.card.icon.vector(),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = action.card.title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        StatusBadge(action.status, statusLabel, accent)
    }
}

@Composable
private fun StatusBadge(status: ToolStatus, label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            ToolStatus.RUNNING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = accent)
            else -> Icon(status.vector(), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@Composable
private fun Fields(card: ToolCard, struck: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((name, value) in card.fields) {
            Row {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 64.dp).padding(end = 10.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

@Composable
private fun Footer(
    action: ToolAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val buttonHeight = Modifier.heightIn(min = 48.dp)
    when (action.status) {
        ToolStatus.AWAITING_CONFIRMATION -> Column {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = buttonHeight) { Text(stringResource(R.string.button_confirm)) }
                OutlinedButton(onClick = onCancel, modifier = buttonHeight) { Text(stringResource(R.string.button_cancel)) }
            }
            Text(
                text = stringResource(R.string.hint_voice_confirm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        ToolStatus.AWAITING_PERMISSION -> Column {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onGrant, modifier = buttonHeight) { Text(stringResource(R.string.button_grant)) }
        }
        // Android stops showing its dialog after a few refusals: the settings are the only way left
        ToolStatus.DENIED -> Column {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenSettings, modifier = buttonHeight) {
                Text(stringResource(R.string.button_open_settings))
            }
        }
        else -> Unit
    }
}

private fun ToolStatus.label() = when (this) {
    ToolStatus.AWAITING_PERMISSION -> R.string.card_status_awaiting_permission
    ToolStatus.AWAITING_CONFIRMATION -> R.string.card_status_awaiting_confirmation
    ToolStatus.RUNNING -> R.string.card_status_running
    ToolStatus.DONE -> R.string.card_status_done
    ToolStatus.FAILED -> R.string.card_status_failed
    ToolStatus.CANCELLED -> R.string.card_status_cancelled
    ToolStatus.DENIED -> R.string.card_status_denied
}

private fun ToolStatus.vector(): ImageVector = when (this) {
    ToolStatus.AWAITING_PERMISSION -> Icons.Filled.Lock
    ToolStatus.AWAITING_CONFIRMATION -> Icons.Filled.HelpOutline
    ToolStatus.RUNNING -> Icons.Filled.Timer
    ToolStatus.DONE -> Icons.Filled.CheckCircle
    ToolStatus.FAILED -> Icons.Filled.Error
    ToolStatus.CANCELLED -> Icons.Filled.Cancel
    ToolStatus.DENIED -> Icons.Filled.Block
}

private fun ToolIcon.vector(): ImageVector = when (this) {
    ToolIcon.ALARM -> Icons.Filled.Alarm
    ToolIcon.TIMER -> Icons.Filled.Timer
    ToolIcon.FLASHLIGHT -> Icons.Filled.FlashlightOn
    ToolIcon.BATTERY -> Icons.Filled.BatteryFull
    ToolIcon.VOLUME -> Icons.Filled.VolumeUp
    ToolIcon.CALENDAR -> Icons.Filled.Event
    ToolIcon.SMS -> Icons.Filled.Sms
    ToolIcon.CALL -> Icons.Filled.Call
}

@Preview(showBackground = true)
@Composable
private fun ToolActionCardPreview() {
    val sms = ToolCard(
        ToolIcon.SMS, "Enviar SMS",
        listOf("Para" to "Marie López", "Mensaje" to "Llego tarde, empezad sin mí"),
    )
    MaterialTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (status in ToolStatus.entries) {
                ToolActionCard(
                    action = ToolAction(sms, status, risky = true),
                    text = "¿Envío a Marie López el mensaje?",
                    onConfirm = {}, onCancel = {}, onGrant = {}, onOpenSettings = {},
                )
            }
        }
    }
}
