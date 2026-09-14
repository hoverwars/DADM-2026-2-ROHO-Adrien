package com.example.reto_1_roho_a

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.White,
                            0.7f to Color.White,
                            1.0f to Color.hsl(47F, 1.0F, 0.53F, alpha = 0.25f)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Hello World !",
                        color = Color.hsl(4F, 0.0F, 0.27F),
                        fontSize = 50.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        "Adrien ROHO",
                        color = Color.hsl(48F, 1.0F, 0.56F),
                        fontSize = 20.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}