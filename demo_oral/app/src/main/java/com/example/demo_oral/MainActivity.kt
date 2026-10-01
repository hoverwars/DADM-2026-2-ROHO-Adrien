package com.example.demo_oral

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.demo_oral.ui.ChatScreen
import com.example.demo_oral.ui.MenuScreen

private const val ROUTE_MENU = "menu"
private const val ROUTE_CHAT = "chat"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_MENU,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(ROUTE_MENU) {
                            MenuScreen(
                                onChat = { navController.navigate(ROUTE_CHAT) },
                                onQuit = ::finish,
                            )
                        }
                        // The ChatViewModel belongs to this destination: the models are loaded when
                        // the chat opens and freed when going back to the menu
                        composable(ROUTE_CHAT) {
                            ChatScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
