package com.example.reto_8_roho_a.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.ui.theme.ConsultoriaColor
import com.example.reto_8_roho_a.ui.theme.DesarrolloMedidaColor
import com.example.reto_8_roho_a.ui.theme.FabricaSoftwareColor

/** Color asociado a cada clasificación. */
val Classification.color: Color
    get() = when (this) {
        Classification.CONSULTORIA -> ConsultoriaColor
        Classification.DESARROLLO_MEDIDA -> DesarrolloMedidaColor
        Classification.FABRICA_SOFTWARE -> FabricaSoftwareColor
    }

/** Icono asociado a cada clasificación. */
val Classification.icon: ImageVector
    get() = when (this) {
        Classification.CONSULTORIA -> Icons.Outlined.Lightbulb
        Classification.DESARROLLO_MEDIDA -> Icons.Outlined.Build
        Classification.FABRICA_SOFTWARE -> Icons.Outlined.Factory
    }

/** Pequeña "píldora" de color que muestra una clasificación. */
@Composable
fun ClassificationBadge(classification: Classification, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(classification.color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = classification.icon,
            contentDescription = null,
            tint = classification.color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = classification.label,
            style = MaterialTheme.typography.labelMedium,
            color = classification.color
        )
    }
}

/** Todas las clasificaciones de una empresa, en el orden del enum. */
@Composable
fun ClassificationBadges(classifications: Set<Classification>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Classification.entries.filter { it in classifications }.forEach { ClassificationBadge(it) }
    }
}
