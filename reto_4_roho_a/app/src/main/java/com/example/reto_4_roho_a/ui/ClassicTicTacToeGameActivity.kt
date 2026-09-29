package com.example.reto_4_roho_a.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reto_4_roho_a.data.Player
import com.example.reto_4_roho_a.ui.components.TicTacToeBoard
import com.example.reto_4_roho_a.ui.ui.theme.BlueXColor
import com.example.reto_4_roho_a.ui.ui.theme.PrimaryColor
import com.example.reto_4_roho_a.ui.ui.theme.RETO_4_ROHO_ATheme
import com.example.reto_4_roho_a.ui.ui.theme.RedOColor
import com.example.reto_4_roho_a.ui.ui.theme.TextColor
import com.example.reto_4_roho_a.viewmodel.ClassicTicTacToeState
import androidx.core.net.toUri
import com.example.reto_4_roho_a.ui.components.ChangeDifficultyDialog
import com.example.reto_4_roho_a.ui.components.ChangeStartingPlayerDialog


private const val TAG = "ClassicTicTacToeGameActivity"

class ClassicTicTacToeGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val againstAi = intent.getBooleanExtra("AGAINST_AI", false)
        Log.i(TAG, "Actividad de juego creada (contra IA=$againstAi)")
        enableEdgeToEdge()
        setContent {
            RETO_4_ROHO_ATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        viewModel = viewModel(),
                        againstAi = againstAi,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(viewModel: ClassicTicTacToeState, againstAi: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val openAlertDialog = remember { mutableStateOf(false) }
    val openStartingPlayerDialog = remember { mutableStateOf(false) }
    val activity = (context as? Activity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scores by viewModel.scores.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val startingPlayerMode by viewModel.startingPlayerMode.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isOver = viewModel.isOver()

    if (openAlertDialog.value) {
        ChangeDifficultyDialog (
            currentDifficulty = difficulty,
            onDismissRequest = { openAlertDialog.value = false },
            onConfirmation = { value ->
                openAlertDialog.value = false
                viewModel.setDifficulty(value)
                Toast.makeText(context, "El cambio se aplicará en la próxima partida", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (openStartingPlayerDialog.value) {
        ChangeStartingPlayerDialog (
            currentStartingPlayerMode = startingPlayerMode,
            onDismissRequest = { openStartingPlayerDialog.value = false },
            onConfirmation = { value ->
                openStartingPlayerDialog.value = false
                viewModel.setStartingPlayerMode(value)
                Toast.makeText(context, "El cambio se aplicará en la próxima partida", Toast.LENGTH_SHORT).show()
            },
        )
    }

    val topActions: @Composable () -> Unit = {
        GameTopActions(
            againstAi = againstAi,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onClose = {
                Log.i(TAG, "Usuario cerró la partida y vuelve a la pantalla principal")
                activity?.finish()
            },
            onChangeDifficulty = {
                Log.d(TAG, "Menú: abrir diálogo de dificultad")
                openAlertDialog.value = true
            },
            onChangeStartingPlayer = {
                Log.d(TAG, "Menú: abrir diálogo de jugador inicial")
                openStartingPlayerDialog.value = true
            },
            onResetScores = {
                Log.i(TAG, "Menú: reiniciar marcador solicitado")
                viewModel.resetScores()
            },
            onOpenGitHub = {
                Log.i(TAG, "Menú: abriendo repositorio en GitHub")
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/hoverwars/DADM-2026-2-ROHO-Adrien".toUri()
                )
                context.startActivity(browserIntent)
            }
        )
    }

    val board: @Composable (Modifier) -> Unit = { boardModifier ->
        TicTacToeBoard(
            board = state.board,
            onCellClick = { index ->
                if (!againstAi || state.currentPlayer == Player.Cross)
                    viewModel.play(index, againstAi)
            },
            modifier = boardModifier
        )
    }

    val onNewGame = {
        if (isOver) {
            Log.i(TAG, "Botón NUEVA PARTIDA presionado")
            viewModel.restartGame(againstAi)
        }
    }

    if (isLandscape) {
        // Mode paysage : plateau à gauche, informations à droite
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            board(
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
                ScoreRow(scores = scores)
                TurnIndicator(currentPlayer = state.currentPlayer)
                GameResult(isOver = isOver, isDraw = state.isDraw, winner = state.winner)
                NewGameButton(
                    enabled = isOver,
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
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
                ScoreRow(scores = scores, modifier = Modifier.padding(top = 8.dp))
                TurnIndicator(currentPlayer = state.currentPlayer)
                board(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                GameResult(isOver = isOver, isDraw = state.isDraw, winner = state.winner)
                NewGameButton(
                    enabled = isOver,
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GameTopActions(
    againstAi: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onChangeDifficulty: () -> Unit,
    onChangeStartingPlayer: () -> Unit,
    onResetScores: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar y volver a la pagina principal",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
        Box {
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                if (againstAi) {
                    DropdownMenuItem(
                        text = { Text("Cambiar dificultad") },
                        onClick = {
                            onExpandedChange(false)
                            onChangeDifficulty()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Cambiar quién empieza") },
                    onClick = {
                        onExpandedChange(false)
                        onChangeStartingPlayer()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reiniciar marcador") },
                    onClick = {
                        onExpandedChange(false)
                        onResetScores()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ver project en GitHub") },
                    onClick = {
                        onExpandedChange(false)
                        onOpenGitHub()
                    }
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(scores: Map<Player, Int>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = BlueXColor,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text("Jugador 1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("(X)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BlueXColor)
            }
        }

        Text(
            text = "${scores[Player.Cross]!!} - ${scores[Player.Circle]!!}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Jugador 2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("(O)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RedOColor)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = RedOColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun TurnIndicator(currentPlayer: Player) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (currentPlayer == Player.Cross) BlueXColor.copy(alpha = 0.75f)
                else RedOColor.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Turno del Jugador ${if (currentPlayer == Player.Cross) "1 (X)" else "2 (O)"}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextColor
        )
    }
}

@Composable
private fun GameResult(isOver: Boolean, isDraw: Boolean, winner: Player?) {
    if (isOver) {
        Text(
            text = if (isDraw) "¡Empate!" else "¡Jugador ${if (winner == Player.Cross) "1" else "2"} ganó!",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextColor
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NewGameButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) PrimaryColor else PrimaryColor.copy(alpha = 0.25f),
            contentColor = if (enabled) TextColor else TextColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(50.dp),
        modifier = modifier.height(56.dp)
    ) {
        Text(
            text = "NUEVA PARTIDA",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
