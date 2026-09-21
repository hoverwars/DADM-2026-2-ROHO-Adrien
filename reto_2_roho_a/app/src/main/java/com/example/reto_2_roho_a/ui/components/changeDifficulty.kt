package com.example.reto_2_roho_a.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.reto_2_roho_a.data.TicTacToeDifficulty

@Composable
fun ChangeDifficultyDialog(
    currentDifficulty: TicTacToeDifficulty,
    onDismissRequest: () -> Unit,
    onConfirmation: (TicTacToeDifficulty) -> Unit,
) {
    var selectedDifficulty by remember { mutableStateOf(currentDifficulty) }
    AlertDialog(
        title = {
            Text(text = "Cambiar dificultad")
        },
        text = {
            RadioButtonSingleSelection(
                selectedDifficulty = selectedDifficulty,
                onOptionSelected = { selectedDifficulty = it }
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(selectedDifficulty)
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
private fun RadioButtonSingleSelection(
    selectedDifficulty: TicTacToeDifficulty,
    onOptionSelected: (TicTacToeDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    // Note that Modifier.selectableGroup() is essential to ensure correct accessibility behavior
    Column (modifier.selectableGroup()) {
        TicTacToeDifficulty.entries.forEach { difficulty ->
            Row (
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = (difficulty == selectedDifficulty),
                        onClick = { onOptionSelected(difficulty) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (difficulty == selectedDifficulty),
                    onClick = null // null recommended for accessibility with screen readers
                )
                Text(
                    text = difficulty.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
