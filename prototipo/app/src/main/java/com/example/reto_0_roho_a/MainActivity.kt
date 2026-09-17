package com.example.prototipo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import com.example.prototipo.health.ConnectionStatus

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { grantedPermissions -> viewModel.onPermissionsResult(grantedPermissions) }

            LaunchedEffect(uiState.connectionStatus, uiState.permissionsChecked, uiState.permissionsGranted) {
                if (uiState.connectionStatus == ConnectionStatus.CONNECTED &&
                    uiState.permissionsChecked &&
                    !uiState.permissionsGranted
                ) {
                    permissionLauncher.launch(viewModel.requiredPermissions)
                }
            }

            HomeScreen(
                uiState = uiState,
                onRequestPermissions = { permissionLauncher.launch(viewModel.requiredPermissions) },
            )
        }
    }
}
