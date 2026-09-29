package com.example.reto_4_roho_a.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reto_4_roho_a.R
import com.example.reto_4_roho_a.data.ClassicTicTacToeBoard
import com.example.reto_4_roho_a.data.Player
import com.example.reto_4_roho_a.data.StartingPlayerMode
import com.example.reto_4_roho_a.data.TicTacToeDifficulty
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ClassicTicTacToeState"

private const val PREFS_NAME = "tictactoe_prefs"
private const val KEY_SCORE_CROSS = "score_cross"
private const val KEY_SCORE_CIRCLE = "score_circle"
private const val KEY_DIFFICULTY = "difficulty"
private const val KEY_STARTING_PLAYER_MODE = "starting_player_mode"

class ClassicTicTacToeState(application: Application) : AndroidViewModel(application)
{
    // Persistance entre redémarrages de l'application (bouton Retour, kill, etc.)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ClassicTicTacToeBoard());
    private var _lastStarter = Player.Cross;
    private val _scores = MutableStateFlow<Map<Player, Int>>(mapOf(
        Player.Cross to prefs.getInt(KEY_SCORE_CROSS, 0),
        Player.Circle to prefs.getInt(KEY_SCORE_CIRCLE, 0)
    ));
    private val _difficulty = MutableStateFlow(
        enumValueOrDefault(prefs.getString(KEY_DIFFICULTY, null), TicTacToeDifficulty.Medium)
    );
    private val _startingPlayerMode = MutableStateFlow(
        enumValueOrDefault(prefs.getString(KEY_STARTING_PLAYER_MODE, null), StartingPlayerMode.Alternate)
    );

    val uiState: StateFlow<ClassicTicTacToeBoard> = _uiState.asStateFlow();
    val scores: StateFlow<Map<Player, Int>> = _scores.asStateFlow();
    val difficulty: StateFlow<TicTacToeDifficulty> = _difficulty.asStateFlow();
    val startingPlayerMode: StateFlow<StartingPlayerMode> = _startingPlayerMode.asStateFlow();

    // --- Audio: soft feedback sounds for cell selection and victory ---
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    Log.d(TAG, "Sonido cargado correctamente (sampleId=$sampleId)")
                } else {
                    Log.w(TAG, "Fallo al cargar el sonido (sampleId=$sampleId, status=$status)")
                }
            }
        }

    private val selectBlueSoundId = soundPool.load(application, R.raw.sound_select_blue, 1)
    private val selectRedSoundId = soundPool.load(application, R.raw.sound_select_red, 1)
    private val winSoundId = soundPool.load(application, R.raw.sound_win, 1)
    private val drawSoundId = soundPool.load(application, R.raw.sound_draw, 1)

    init {
        Log.i(TAG, "Preferencias cargadas: marcador=${_scores.value}, dificultad=${_difficulty.value}, modo inicial=${_startingPlayerMode.value}")
        Log.i(TAG, "ViewModel creado. Cargando sonidos (blue=$selectBlueSoundId, red=$selectRedSoundId, win=$winSoundId, draw=$drawSoundId)")
    }

    private fun playSelectSound(player: Player) {
        val soundId = if (player == Player.Cross) selectBlueSoundId else selectRedSoundId
        Log.d(TAG, "Reproduciendo sonido de selección para $player")
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    private fun playWinSound() {
        Log.d(TAG, "Reproduciendo sonido de victoria")
        soundPool.play(winSoundId, 1f, 1f, 2, 0, 1f)
    }

    private fun playDrawSound() {
        Log.d(TAG, "Reproduciendo sonido de empate")
        soundPool.play(drawSoundId, 1f, 1f, 2, 0, 1f)
    }

    private fun saveScores() {
        prefs.edit {
            putInt(KEY_SCORE_CROSS, _scores.value[Player.Cross]!!)
            putInt(KEY_SCORE_CIRCLE, _scores.value[Player.Circle]!!)
        }
        Log.d(TAG, "Marcador guardado en SharedPreferences: ${_scores.value}")
    }

    override fun onCleared() {
        Log.i(TAG, "ViewModel destruido, liberando SoundPool")
        soundPool.release()
        super.onCleared()
    }

    fun setDifficulty(difficulty: TicTacToeDifficulty) {
        Log.i(TAG, "Dificultad cambiada de ${_difficulty.value} a $difficulty")
        _difficulty.value = difficulty;
        prefs.edit { putString(KEY_DIFFICULTY, difficulty.name) }
    }

    fun setStartingPlayerMode(mode: StartingPlayerMode) {
        Log.i(TAG, "Modo de jugador inicial cambiado de ${_startingPlayerMode.value} a $mode")
        _startingPlayerMode.value = mode;
        prefs.edit { putString(KEY_STARTING_PLAYER_MODE, mode.name) }
    }

    fun resetScores() {
        Log.i(TAG, "Marcador reiniciado (anterior: ${_scores.value})")
        _scores.value = mapOf(
            Player.Cross to 0,
            Player.Circle to 0
        );
        saveScores()
    }


    fun play(index: Int, againstAI: Boolean): Boolean {
        val cell = _uiState.value.board[index];
        val player = _uiState.value.currentPlayer;

        // Game is over.
        if (isOver()) {
            Log.w(TAG, "Movimiento rechazado: la partida ya terminó (index=$index)")
            return false;
        }
        // Cannot play here.
        if (cell != null) {
            Log.w(TAG, "Movimiento rechazado: celda $index ya ocupada por $cell")
            return false;
        }

        Log.d(TAG, "Movimiento aceptado: $player juega en la celda $index (contra IA=$againstAI)")

        val newBoard = _uiState.value.board.toMutableList().apply {
            this[index] = _uiState.value.currentPlayer
        }

        val state = checkForWinner(newBoard)

        playSelectSound(player)

        if (state == 2 || state == 3) {
            val winner = if (state == 2) Player.Cross else Player.Circle
            Log.i(TAG, "Fin de la partida: gana $winner (celda decisiva=$index)")
            _scores.value = _scores.value.toMutableMap().apply {
                this[Player.Cross] = this[Player.Cross]!! + (if (state == 2) 1 else 0)
                this[Player.Circle] = this[Player.Circle]!! + (if (state == 3) 1 else 0)
            }
            Log.i(TAG, "Marcador actualizado: ${_scores.value}")
            saveScores()
            playWinSound()
        } else if (state == 1) {
            Log.i(TAG, "Fin de la partida: empate")
            playDrawSound()
        }

        _uiState.value = _uiState.value.copy(
            board = newBoard,
            currentPlayer = if (state == 0) switchPlayer(_uiState.value.currentPlayer) else _uiState.value.currentPlayer,
            winner = if (state == 2) Player.Cross else if (state == 3) Player.Circle else null,
            isDraw = state == 1
        )

        if (state == 0 && againstAI) {
            Log.d(TAG, "Programando movimiento de la IA")
            viewModelScope.launch {
                delay((Random.nextInt(500) + 500).milliseconds);
                play(getComputerMove(newBoard), false);
            }
        }

        return true;
    }

    fun isOver(): Boolean {
        return _uiState.value.winner != null || _uiState.value.isDraw;
    }

    fun restartGame(againstAI: Boolean) {
        _lastStarter = when (_startingPlayerMode.value) {
            StartingPlayerMode.AlwaysBlue -> Player.Cross
            StartingPlayerMode.AlwaysRed -> Player.Circle
            StartingPlayerMode.Alternate -> switchPlayer(_lastStarter)
        }
        Log.i(TAG, "Nueva partida iniciada. Empieza $_lastStarter (modo=${_startingPlayerMode.value}, contra IA=$againstAI)")
        val newGame = ClassicTicTacToeBoard(
            currentPlayer = _lastStarter
        );
        _uiState.value = newGame;

        if (_lastStarter == Player.Circle && againstAI) {
            Log.d(TAG, "Programando movimiento inicial de la IA")
            viewModelScope.launch {
                delay((Random.nextInt(500) + 500).milliseconds);
                play(getComputerMove(newGame.board), false);
            }
        }
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

    fun getComputerMove(board: List<Player?>): Int{
        val difficulty = _difficulty.value;

        // See if there's a move O can make to win
        if (difficulty == TicTacToeDifficulty.Medium || difficulty == TicTacToeDifficulty.Hard) {
            for (i in 0 until 9) {
                if (board[i] != Player.Cross && board[i] != Player.Circle) {
                    val tempBoard = board.toMutableList().apply {
                        this[i] = Player.Circle
                    }
                    if (checkForWinner(tempBoard) == 3) {
                        Log.d(TAG, "IA (dificultad=$difficulty): movimiento ganador encontrado en celda $i")
                        return i;
                    }
                }
            }
        }

        // See if there's a move O can make to block X from winning
        if (difficulty == TicTacToeDifficulty.Hard) {
            for (i in 0 until 9) {
                if (board[i] != Player.Cross && board[i] != Player.Circle) {
                    val tempBoard = board.toMutableList().apply {
                        this[i] = Player.Cross
                    }
                    if (checkForWinner(tempBoard) == 2) {
                        Log.d(TAG, "IA (dificultad=$difficulty): bloqueando victoria del jugador en celda $i")
                        return i;
                    }
                }
            }
        }

        // Generate random move
        var move: Int
        do {
            move = Random.nextInt(9)
        } while (board[move] == Player.Cross || board[move] == Player.Circle)

        Log.d(TAG, "IA (dificultad=$difficulty): movimiento aleatorio elegido en celda $move")
        return move;
    }
    
    private fun switchPlayer(currentPlayer: Player): Player{
        return if (currentPlayer == Player.Cross) Player.Circle else Player.Cross;
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
