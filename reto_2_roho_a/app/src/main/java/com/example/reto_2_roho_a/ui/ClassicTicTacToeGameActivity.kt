package com.example.reto_2_roho_a.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reto_2_roho_a.data.Player
import com.example.reto_2_roho_a.ui.components.TicTacToeBoard
import com.example.reto_2_roho_a.ui.ui.theme.BlueXColor
import com.example.reto_2_roho_a.ui.ui.theme.PrimaryColor
import com.example.reto_2_roho_a.ui.ui.theme.RETO_2_ROHO_ATheme
import com.example.reto_2_roho_a.ui.ui.theme.RedOColor
import com.example.reto_2_roho_a.ui.ui.theme.TextColor
import com.example.reto_2_roho_a.viewmodel.ClassicTicTacToeState
import androidx.core.net.toUri
import com.example.reto_2_roho_a.ui.components.ChangeDifficultyDialog
import com.example.reto_2_roho_a.ui.components.ChangeStartingPlayerDialog


class ClassicTicTacToeGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RETO_2_ROHO_ATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        viewModel = viewModel(),
                        againstAi = intent.getBooleanExtra("AGAINST_AI", false),
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

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton (onClick = { activity?.finish() } ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar y volver a la pagina principal",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box() {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu (
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (againstAi) {
                            DropdownMenuItem(
                                text = { Text("Cambiar dificultad") },
                                onClick = {
                                    expanded = !expanded;
                                    openAlertDialog.value = true;
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cambiar quién empieza") },
                            onClick = {
                                expanded = !expanded;
                                openStartingPlayerDialog.value = true;
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reiniciar marcador") },
                            onClick = {
                                expanded = !expanded;
                                viewModel.resetScores();
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ver project en GitHub") },
                            onClick = {
                                expanded = !expanded;
                                val browserIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/hoverwars/DADM-2026-2-ROHO-Adrien".toUri()
                                )
                                context.startActivity(browserIntent)
                            }
                        )
                    }
                    when {
                        openAlertDialog.value -> {
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
                    }
                    when {
                        openStartingPlayerDialog.value -> {
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
                    }
                }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (state.currentPlayer == Player.Cross) BlueXColor.copy(alpha = 0.75f)
                        else RedOColor.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Turno del Jugador ${if (state.currentPlayer == Player.Cross) "1 (X)" else "2 (O)"}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextColor
                )
            }

            TicTacToeBoard(
                board = state.board,
                onCellClick = { index ->
                    if (!againstAi || state.currentPlayer == Player.Cross)
                        viewModel.play(index, againstAi)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            if (viewModel.isOver()) {
                Text(
                    text = if (state.isDraw) "¡Empate!" else "¡Jugador ${if (state.winner == Player.Cross) "1" else "2"} ganó!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextColor
                )
            }
            else {
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = {
                    if (viewModel.isOver()) {
                        viewModel.restartGame(againstAi)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isOver()) PrimaryColor else PrimaryColor.copy(alpha = 0.25f),
                    contentColor = if (viewModel.isOver()) TextColor else TextColor.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
            ) {
                Text(
                    text = "NUEVA PARTIDA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}