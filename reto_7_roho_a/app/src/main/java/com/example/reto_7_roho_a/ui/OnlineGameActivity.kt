package com.example.reto_7_roho_a.ui

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.reto_7_roho_a.data.OnlineGameStatus
import com.example.reto_7_roho_a.data.Player
import com.example.reto_7_roho_a.ui.components.TicTacToeBoard
import com.example.reto_7_roho_a.ui.ui.theme.RETO_7_ROHO_ATheme
import com.example.reto_7_roho_a.viewmodel.OnlineGameState

private const val TAG = "OnlineGameActivity"

/** Partie en ligne à deux : même interface que le mode local, synchronisée via Firebase. */
class OnlineGameActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME_ID = "GAME_ID"
        /** Résultat renvoyé au lobby : l'adversaire a quitté la partie en cours. */
        const val RESULT_EXTRA_OPPONENT_LEFT = "OPPONENT_LEFT"
    }

    private val viewModel: OnlineGameState by viewModels {
        viewModelFactory {
            initializer {
                OnlineGameState(this[APPLICATION_KEY] as Application, intent.getStringExtra(EXTRA_GAME_ID)!!)
            }
        }
    }

    // Une partie sans rival disparaît du lobby dès que son créateur quitte l'écran
    // (hors rotation) ; en cours de partie, on ne quitte que par la croix.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) viewModel.leaveIfWaiting()
    }

    private fun closeGame(opponentLeft: Boolean = false) {
        viewModel.leave()
        if (opponentLeft) setResult(RESULT_OK, Intent().putExtra(RESULT_EXTRA_OPPONENT_LEFT, true))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        if (gameId == null) {
            Log.e(TAG, "Falta el identificador de la partida")
            finish()
            return
        }
        Log.i(TAG, "Actividad de partida en línea creada (gameId=$gameId)")
        enableEdgeToEdge()
        setContent {
            RETO_7_ROHO_ATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnlineGameScreen(
                        viewModel = viewModel,
                        onClose = { closeGame() },
                        onOpponentLeft = { closeGame(opponentLeft = true) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineGameScreen(
    viewModel: OnlineGameState,
    onClose: () -> Unit,
    onOpponentLeft: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val game by viewModel.game.collectAsStateWithLifecycle()
    val deleted by viewModel.gameDeleted.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val opponentLeft by viewModel.opponentLeft.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) {
        if (deleted) {
            Toast.makeText(context, "La partida ya no existe", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    // Le joueur resté est renvoyé au lobby, qui affiche la snackbar
    LaunchedEffect(opponentLeft) {
        if (opponentLeft) onOpponentLeft()
    }

    val current = game
    val myMark = viewModel.myMark
    val board = current?.board ?: List(9) { null }
    val status = current?.status
    val isOver = status == OnlineGameStatus.Finished
    val opponentName = if (myMark == Player.Cross) current?.guestName else current?.hostName

    val turnPlayer = current?.turn ?: Player.Cross
    val turnText = when {
        current == null -> if (connectionError) "Sin conexión con el servidor" else "Conectando..."
        status == OnlineGameStatus.Waiting -> "Esperando a que otro jugador se una..."
        isOver -> "Partida terminada"
        turnPlayer == myMark -> "Es tu turno"
        else -> "Turno de ${opponentName ?: "tu rival"}"
    }
    val resultText = when {
        current?.winner == null -> null
        current.winner == myMark -> "¡Ganaste!"
        else -> "¡${opponentName ?: "Tu rival"} ganó!"
    }

    val topActions: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar y volver a la pagina principal",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
    val scoreRow: @Composable (Modifier) -> Unit = { scoreModifier ->
        ScoreRow(
            scores = mapOf(Player.Cross to (current?.scoreX ?: 0), Player.Circle to (current?.scoreO ?: 0)),
            modifier = scoreModifier,
            nameCross = playerLabel(current?.hostName ?: "Jugador 1", myMark == Player.Cross),
            nameCircle = playerLabel(current?.guestName ?: "Esperando...", myMark == Player.Circle)
        )
    }
    val boardView: @Composable (Modifier) -> Unit = { boardModifier ->
        TicTacToeBoard(
            board = board,
            onCellClick = { index -> viewModel.play(index) },
            modifier = boardModifier
        )
    }
    val newGameButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        NewGameButton(enabled = isOver, onClick = { viewModel.newGame() }, modifier = buttonModifier)
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            boardView(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.width(32.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                topActions()
                scoreRow(Modifier)
                TurnIndicator(currentPlayer = turnPlayer, text = turnText)
                GameResult(isOver = isOver, isDraw = current?.isDraw == true, winner = current?.winner, winnerText = resultText)
                newGameButton(Modifier.fillMaxWidth(0.85f))
            }
        }
    } else {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    topActions()
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                scoreRow(Modifier.padding(top = 8.dp))
                TurnIndicator(currentPlayer = turnPlayer, text = turnText)
                boardView(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                GameResult(isOver = isOver, isDraw = current?.isDraw == true, winner = current?.winner, winnerText = resultText)
                newGameButton(Modifier.fillMaxWidth(0.85f))
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun playerLabel(name: String, isMe: Boolean) = if (isMe) "$name (tú)" else name
