package com.example.reto_2_roho_a.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.reto_2_roho_a.data.StartingPlayerMode

@Composable
fun ChangeStartingPlayerDialog(
    currentStartingPlayerMode: StartingPlayerMode,
    onDismissRequest: () -> Unit,
    onConfirmation: (StartingPlayerMode) -> Unit,
) {
    var selectedMode by remember { mutableStateOf(currentStartingPlayerMode) }
    AlertDialog(
        title = {
            Text(text = "Cambiar quién empieza")
        },
        text = {
            RadioButtonStartingPlayerSelection(
                selectedMode = selectedMode,
                onOptionSelected = { selectedMode = it }
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(selectedMode)
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RadioButtonStartingPlayerSelection(
    selectedMode: StartingPlayerMode,
    onOptionSelected: (StartingPlayerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Note that Modifier.selectableGroup() is essential to ensure correct accessibility behavior
    Column (modifier.selectableGroup()) {
        StartingPlayerMode.entries.forEach { mode ->
            Row (
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = (mode == selectedMode),
                        onClick = { onOptionSelected(mode) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (mode == selectedMode),
                    onClick = null // null recommended for accessibility with screen readers
                )
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
