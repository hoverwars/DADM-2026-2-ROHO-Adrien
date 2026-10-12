package com.example.reto_7_roho_a.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.reto_7_roho_a.data.OnlineGame
import com.example.reto_7_roho_a.ui.ui.theme.BlueXColor
import com.example.reto_7_roho_a.ui.ui.theme.PrimaryColor
import com.example.reto_7_roho_a.ui.ui.theme.RETO_7_ROHO_ATheme
import com.example.reto_7_roho_a.ui.ui.theme.TextColor
import com.example.reto_7_roho_a.viewmodel.OnlineLobbyState

private const val TAG = "OnlineLobbyActivity"

/** Liste des parties disponibles : on peut en rejoindre une ou en créer une nouvelle. */
class OnlineLobbyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Lobby en línea creado")
        enableEdgeToEdge()
        setContent {
            RETO_7_ROHO_ATheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Lobby(
                        viewModel = viewModel(),
                        onClose = { finish() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun Lobby(viewModel: OnlineLobbyState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val games by viewModel.games.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val playerName by viewModel.playerName.collectAsStateWithLifecycle()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Ses propres parties en attente (restes d'une session précédente) ne sont pas rejoignables
    val availableGames = games.filter { it.hostId != viewModel.playerId }

    // Au retour d'une partie, prévient le joueur resté si son adversaire est parti
    val gameLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data?.getBooleanExtra(OnlineGameActivity.RESULT_EXTRA_OPPONENT_LEFT, false) == true) {
            Toast.makeText(context, "Tu rival abandonó la partida", Toast.LENGTH_SHORT).show()
        }
    }

    fun openGame(gameId: String) {
        gameLauncher.launch(Intent(context, OnlineGameActivity::class.java).apply {
            putExtra(OnlineGameActivity.EXTRA_GAME_ID, gameId)
        })
    }

    val onCreate = {
        viewModel.createGame { id ->
            if (id != null) openGame(id)
            else Toast.makeText(context, "No se pudo crear la partida. Revisa tu conexión", Toast.LENGTH_SHORT).show()
        }
    }
    val onJoin = { game: OnlineGame ->
        viewModel.joinGame(game.id) { ok ->
            if (ok) openGame(game.id)
            else Toast.makeText(context, "La partida ya no está disponible", Toast.LENGTH_SHORT).show()
        }
    }

    val header: @Composable () -> Unit = {
        Text("Partidas en línea", color = PrimaryColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = playerName,
            onValueChange = viewModel::setPlayerName,
            label = { Text("Tu nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val createButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            onClick = onCreate,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor),
            shape = RoundedCornerShape(50.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            modifier = buttonModifier.height(56.dp)
        ) {
            Text("CREAR PARTIDA", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
    val gamesList: @Composable (Modifier) -> Unit = { listModifier ->
        Box(modifier = listModifier, contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(color = PrimaryColor)
                connectionError -> Text(
                    "No se pudo conectar con el servidor. Revisa tu conexión a Internet.",
                    color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(16.dp)
                )
                availableGames.isEmpty() -> Text(
                    "No hay partidas disponibles.\nCrea una y espera a un rival.",
                    color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(availableGames, key = { it.id }) { game ->
                        GameCard(game = game, enabled = !busy, onJoin = { onJoin(game) })
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                CloseRow(onClose)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { header() }
                createButton(Modifier.fillMaxWidth(0.85f))
            }
            Spacer(Modifier.width(24.dp))
            gamesList(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CloseRow(onClose)
            header()
            Spacer(Modifier.height(16.dp))
            gamesList(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(Modifier.height(12.dp))
            createButton(Modifier.fillMaxWidth(0.85f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CloseRow(onClose: () -> Unit) {
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

@Composable
private fun GameCard(game: OnlineGame, enabled: Boolean, onJoin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueXColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onJoin)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = BlueXColor, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(game.hostName, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("Esperando un rival", fontSize = 12.sp, color = Color.Gray)
        }
        Button(
            onClick = onJoin,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor),
            shape = RoundedCornerShape(50.dp)
        ) {
            Text("UNIRSE", fontWeight = FontWeight.Bold)
        }
    }
}
