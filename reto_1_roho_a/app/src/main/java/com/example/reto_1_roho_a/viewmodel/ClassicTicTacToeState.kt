package com.example.reto_1_roho_a.viewmodel

import androidx.lifecycle.ViewModel
import com.example.reto_1_roho_a.data.ClassicTicTacToeBoard
import com.example.reto_1_roho_a.data.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClassicTicTacToeState : ViewModel()
{
    private val _uiState = MutableStateFlow(ClassicTicTacToeBoard());
    private val _scores = MutableStateFlow<Map<Player, Int>>(mapOf(
        Player.Cross to 0,
        Player.Circle to 0
    ));
    val uiState: StateFlow<ClassicTicTacToeBoard> = _uiState.asStateFlow();
    val scores: StateFlow<Map<Player, Int>> = _scores.asStateFlow();


    fun play(index: Int): Boolean {
        val cell = _uiState.value.board[index];

        // Game is over.
        if (isOver()) return false;
        // Cannot play here.
        if (cell != null) return false;

        val newBoard = _uiState.value.board.toMutableList().apply {
            this[index] = _uiState.value.currentPlayer
        }

        val state = checkForWinner(newBoard)

        if (state == 2 || state == 3)
            _scores.value = _scores.value.toMutableMap().apply {
                this[Player.Cross] = this[Player.Cross]!! + (if (state == 2) 1 else 0)
                this[Player.Circle] = this[Player.Circle]!! + (if (state == 3) 1 else 0)
            }

        _uiState.value = _uiState.value.copy(
            board = newBoard,
            currentPlayer = if (state == 0) switchPlayer(_uiState.value.currentPlayer) else _uiState.value.currentPlayer,
            winner = if (state == 2) Player.Cross else if (state == 3) Player.Circle else null,
            isDraw = state == 1
        )

        return true;
    }

    fun isOver(): Boolean {
        return _uiState.value.winner != null || _uiState.value.isDraw;
    }

    fun restartGame() {
        _uiState.value = ClassicTicTacToeBoard();
    }

    /**
     * Returns a code based on result.
     * 0 = game ongoing
     * 1 = game ended on draw
     * 2 = Player 1 won
     * 3 = Player 2 won
     */
    private fun checkForWinner(board: List<Player?>): Int {

        // Check horizontal wins
        for (i in 0..6 step 3) {
            if (board[i] == Player.Cross &&
                board[i + 1] == Player.Cross &&
                board[i + 2] == Player.Cross
            )
                return 2
            if (board[i] == Player.Circle &&
                board[i + 1] == Player.Circle &&
                board[i + 2] == Player.Circle
            )
                return 3
        }

        // Check vertical wins
        for (i in 0..2) {
            if (board[i] == Player.Cross &&
                board[i + 3] == Player.Cross &&
                board[i + 6] == Player.Cross
            )
                return 2
            if (board[i] == Player.Circle &&
                board[i + 3] == Player.Circle &&
                board[i + 6] == Player.Circle
            )
                return 3
        }

        // Check for diagonal wins
        if ((board[0] == Player.Cross &&
                    board[4] == Player.Cross &&
                    board[8] == Player.Cross) ||
            (board[2] == Player.Cross &&
                    board[4] == Player.Cross &&
                    board[6] == Player.Cross)
        )
            return 2
        if ((board[0] == Player.Circle &&
                    board[4] == Player.Circle &&
                    board[8] == Player.Circle) ||
            (board[2] == Player.Circle &&
                    board[4] == Player.Circle &&
                    board[6] == Player.Circle)
        )
            return 3

        // Check for tie
        for (i in 0 until 9) {
            // If we find a number, then no one has won yet
            if (board[i] != Player.Cross && board[i] != Player.Circle)
                return 0
        }

        // If we make it through the previous loop, all places are taken, so it's a tie
        return 1
    }

    private fun switchPlayer(currentPlayer: Player): Player{
        return if (currentPlayer == Player.Cross) Player.Circle else Player.Cross;
    }
}