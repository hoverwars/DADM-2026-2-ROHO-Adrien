package com.example.prototipo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.prototipo.health.ConnectionStatus
import com.example.prototipo.health.HealthConnectManager
import com.example.prototipo.health.HomeUiState
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 30_000L

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectManager = HealthConnectManager(application)
    val requiredPermissions = healthConnectManager.requiredPermissions

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        val status = if (healthConnectManager.isAvailable) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.UNAVAILABLE
        }
        _uiState.update { it.copy(connectionStatus = status) }

        if (status == ConnectionStatus.CONNECTED) {
            viewModelScope.launch {
                val granted = healthConnectManager.hasAllPermissions()
                _uiState.update { it.copy(permissionsGranted = granted, permissionsChecked = true) }
                if (granted) startPolling()
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        val allGranted = grantedPermissions.containsAll(requiredPermissions)
        _uiState.update { it.copy(permissionsGranted = allGranted, permissionsChecked = true) }
        if (allGranted) startPolling()
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                syncNow()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncNow() {
        _uiState.update { it.copy(isSyncing = true) }
        runCatching {
            val heartRate = healthConnectManager.readLatestHeartRate()
            val steps = healthConnectManager.readTodaySteps()
            val distance = healthConnectManager.readTodayDistance()
            val sleep = healthConnectManager.readLastSleepSession()
            _uiState.update {
                it.copy(
                    heartRate = heartRate,
                    steps = steps,
                    distance = distance,
                    sleep = sleep,
                    lastSyncAt = Instant.now(),
                )
            }
        }
        _uiState.update { it.copy(isSyncing = false) }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
