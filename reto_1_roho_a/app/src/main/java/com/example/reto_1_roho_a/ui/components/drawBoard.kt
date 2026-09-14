package com.example.reto_1_roho_a.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.reto_1_roho_a.data.Player
import com.example.reto_1_roho_a.ui.ui.theme.BoardLineColor

@Composable
fun TicTacToeBoard(
    board: List<Player?>,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Dessin des 4 lignes du plateau
        Canvas (modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val width = size.width
            val height = size.height
            val cellWidth = width / 3
            val cellHeight = height / 3

            // Lignes verticales
            drawLine(
                color = BoardLineColor,
                start = Offset(cellWidth, 0f),
                end = Offset(cellWidth, height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = BoardLineColor,
                start = Offset(cellWidth * 2, 0f),
                end = Offset(cellWidth * 2, height),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Lignes horizontales
            drawLine(
                color = BoardLineColor,
                start = Offset(0f, cellHeight),
                end = Offset(width, cellHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = BoardLineColor,
                start = Offset(0f, cellHeight * 2),
                end = Offset(width, cellHeight * 2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Grille de 9 cases cliquables avec leurs symboles
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = Modifier.weight(1f)) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable() { onCellClick(index) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (board[index]) {
                                Player.Cross -> DrawX()
                                Player.Circle -> DrawO()
                                null -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}