package com.example.reto_7_roho_a

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reto_7_roho_a.ui.ClassicTicTacToeGameActivity
import com.example.reto_7_roho_a.ui.OnlineLobbyActivity
import com.example.reto_7_roho_a.ui.ui.theme.PrimaryColor
import com.example.reto_7_roho_a.ui.ui.theme.TextColor

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity creada")

        setContent {
            val context = LocalContext.current;
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val startGame = { againstAi: Boolean ->
                Log.i(TAG, if (againstAi) "Usuario seleccionó modo Solo vs IA" else "Usuario seleccionó modo 2 Jugadores")
                val intent = Intent(context, ClassicTicTacToeGameActivity::class.java).apply {
                    putExtra("AGAINST_AI", againstAi)
                };
                context.startActivity(intent)
            }
            val startOnline = {
                Log.i(TAG, "Usuario seleccionó modo En línea")
                context.startActivity(Intent(context, OnlineLobbyActivity::class.java))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (isLandscape) {
                    // Paysage : titre + logo à gauche, boutons à droite
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                            .padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Triqui", color = PrimaryColor, fontSize = 50.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo Tic Tac Toe",
                                modifier = Modifier.size(180.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            MenuButtons(onStartGame = startGame, onStartOnline = startOnline)
                        }
                    }
                } else {
                    Text(
                        "Triqui",
                        color = PrimaryColor,
                        fontSize = 50.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 180.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(50.dp))
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo Tic Tac Toe",
                            modifier = Modifier.size(260.dp)
                        )
                        Spacer(modifier = Modifier.height(50.dp))
                        MenuButtons(onStartGame = startGame, onStartOnline = startOnline)
                    }
                }
                Text(
                    text = "Versión 0.7 - Desarollado por Adrien ROHO",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isLandscape) 12.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuButtons(onStartGame: (Boolean) -> Unit, onStartOnline: () -> Unit) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        MenuButton("Modo Solo vs IA") { onStartGame(true) }
        MenuButton("Modo 2 Jugadores") { onStartGame(false) }
        MenuButton("Modo En línea", onClick = onStartOnline)
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryColor,
            contentColor = TextColor
        ),
        shape = RoundedCornerShape(50.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        modifier = Modifier.height(60.dp).fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
