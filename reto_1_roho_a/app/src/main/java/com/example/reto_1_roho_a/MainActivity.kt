package com.example.reto_1_roho_a

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reto_1_roho_a.ui.ClassicTicTacToeGameActivity
import com.example.reto_1_roho_a.ui.ui.theme.PrimaryColor
import com.example.reto_1_roho_a.ui.ui.theme.TextColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current;
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                    Spacer(modifier = Modifier.height(120.dp))
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo Tic Tac Toe",
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(modifier = Modifier.height(120.dp))
                    Column(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    {
                        Button(
                            onClick = {
                                val intent = Intent(context, ClassicTicTacToeGameActivity::class.java).apply {
                                    putExtra("AGAINST_AI", true)
                                };
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryColor,
                                contentColor = TextColor
                            ),
                            shape = RoundedCornerShape(50.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                            ),
                            modifier = Modifier.height(60.dp).fillMaxWidth()
                        ) {
                            Text(
                                text = "Modo Solo vs IA",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = {
                                val intent = Intent(context, ClassicTicTacToeGameActivity::class.java).apply {
                                    putExtra("AGAINST_AI", false)
                                };
                                context.startActivity(intent)
                            },
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
                                text = "Modo 2 Jugadores",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Text(
                    text = "Versión 0.1 - Desarollado por Adrien ROHO",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}