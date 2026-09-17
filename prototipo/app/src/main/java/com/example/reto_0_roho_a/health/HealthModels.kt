package com.example.prototipo.health

import java.time.Instant

enum class ConnectionStatus {
    CHECKING,
    CONNECTED,
    UNAVAILABLE,
}

data class MetricState(
    val value: String? = null,
    val unit: String = "",
    val recordedAt: Instant? = null,
)

data class HomeUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.CHECKING,
    val permissionsChecked: Boolean = false,
    val permissionsGranted: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncAt: Instant? = null,
    val heartRate: MetricState = MetricState(unit = "bpm"),
    val steps: MetricState = MetricState(unit = "pasos"),
    val distance: MetricState = MetricState(unit = "km"),
    val sleep: MetricState = MetricState(unit = "h"),
)
