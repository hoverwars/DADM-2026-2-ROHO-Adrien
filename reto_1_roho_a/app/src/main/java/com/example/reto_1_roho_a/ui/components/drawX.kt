package com.example.reto_1_roho_a.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.reto_1_roho_a.ui.ui.theme.BlueXColor

@Composable
fun DrawX(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidthPx = strokeWidth.toPx()

        drawLine(
            color = BlueXColor,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        drawLine(
            color = BlueXColor,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
    }
}