package com.example.reto_1_roho_a.data

// Represents a basic tic tac toe board state.
data class ClassicTicTacToeBoard(
    val board: MutableList<Player?> = MutableList(9) { null },
    val currentPlayer: Player = Player.Cross,
    val winner: Player? = null,
    val isDraw: Boolean = false
)