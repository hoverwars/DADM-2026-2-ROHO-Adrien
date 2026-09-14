package com.example.reto_1_roho_a.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.reto_1_roho_a.ui.ui.theme.RedOColor

@Composable
fun DrawO(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidthPx = strokeWidth.toPx()

        drawCircle(
            color = RedOColor,
            style = Stroke(width = strokeWidthPx)
        )
    }
}