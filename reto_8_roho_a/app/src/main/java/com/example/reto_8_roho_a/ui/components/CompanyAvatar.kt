package com.example.reto_8_roho_a.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reto_8_roho_a.ui.theme.AvatarColors

/** Iniciales de la empresa sobre un fondo de color (estable: depende solo del nombre). */
@Composable
fun CompanyAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val color = AvatarColors[name.lowercase().hashCode().mod(AvatarColors.size)]
    Box(
        modifier = modifier
            .size(size)
            .background(color, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}

private fun initials(name: String): String =
    name.split(" ", "-", "_")
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
