package com.example.prototipo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prototipo.health.ConnectionStatus
import com.example.prototipo.health.HomeUiState
import com.example.prototipo.health.MetricState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatTime(instant: Instant?): String {
    if (instant == null) return "—"
    val zone = ZoneId.systemDefault()
    val isToday = instant.atZone(zone).toLocalDate() == LocalDate.now(zone)
    return if (isToday) timeFormatter.format(instant) else dateTimeFormatter.format(instant)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Mis datos de salud",
                color = Color.hsl(4F, 0.0F, 0.27F),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
            Text(
                "Adrien ROHO",
                color = Color.hsl(48F, 1.0F, 0.56F),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            HealthConnectStatusCard(
                uiState = uiState,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "Frecuencia cardíaca",
                    emoji = "❤️",
                    metric = uiState.heartRate,
                    accentColor = Color.hsl(0F, 0.7F, 0.55F),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                MetricCard(
                    title = "Pasos",
                    emoji = "👣",
                    metric = uiState.steps,
                    accentColor = Color.hsl(140F, 0.6F, 0.4F),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = "Distancia",
                    emoji = "📍",
                    metric = uiState.distance,
                    accentColor = Color.hsl(205F, 0.7F, 0.5F),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                MetricCard(
                    title = "Sueño",
                    emoji = "😴",
                    metric = uiState.sleep,
                    accentColor = Color.hsl(260F, 0.55F, 0.55F),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HealthConnectStatusCard(
    uiState: HomeUiState,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dotColor, statusText) = when {
        uiState.connectionStatus == ConnectionStatus.UNAVAILABLE ->
            Color.hsl(0F, 0.7F, 0.55F) to "Health Connect no disponible"
        uiState.connectionStatus == ConnectionStatus.CHECKING ->
            Color.hsl(45F, 1.0F, 0.5F) to "Verificando la conexión..."
        !uiState.permissionsChecked ->
            Color.hsl(45F, 1.0F, 0.5F) to "Verificando los permisos..."
        !uiState.permissionsGranted ->
            Color.hsl(45F, 1.0F, 0.5F) to "Se requieren permisos"
        else ->
            Color.hsl(140F, 0.6F, 0.4F) to "Conectado a Health Connect"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = Color.hsl(4F, 0.0F, 0.27F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.hsl(48F, 1.0F, 0.56F),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (uiState.isSyncing) {
                    "Sincronización en curso..."
                } else {
                    "Última actualización: ${formatTime(uiState.lastSyncAt)}"
                },
                color = Color.hsl(4F, 0.0F, 0.45F),
                fontSize = 13.sp,
            )

            if (uiState.permissionsChecked && !uiState.permissionsGranted &&
                uiState.connectionStatus == ConnectionStatus.CONNECTED
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onRequestPermissions) {
                    Text("Autorizar acceso a los datos")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    emoji: String,
    metric: MetricState,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = Color.hsl(4F, 0.0F, 0.45F),
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = metric.value?.let { "$it ${metric.unit}" } ?: "Sin datos",
                color = Color.hsl(4F, 0.0F, 0.2F),
                fontSize = if (metric.value != null) 22.sp else 14.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Registrado a las ${formatTime(metric.recordedAt)}",
                color = accentColor,
                fontSize = 11.sp,
            )
        }
    }
}
