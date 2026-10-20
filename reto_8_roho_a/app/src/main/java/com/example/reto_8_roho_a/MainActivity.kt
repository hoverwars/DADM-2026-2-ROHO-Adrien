package com.example.reto_8_roho_a

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.reto_8_roho_a.data.Classification
import com.example.reto_8_roho_a.ui.CompanyFormActivity
import com.example.reto_8_roho_a.ui.CompanyListActivity
import com.example.reto_8_roho_a.ui.components.color
import com.example.reto_8_roho_a.ui.components.icon
import com.example.reto_8_roho_a.ui.theme.Indigo
import com.example.reto_8_roho_a.ui.theme.RETO_8_ROHO_ATheme
import com.example.reto_8_roho_a.ui.theme.Teal
import com.example.reto_8_roho_a.viewmodel.HomeState

private const val TAG = "MainActivity"

/** Pantalla de inicio: presentación, estadísticas del directorio y accesos rápidos. */
class MainActivity : ComponentActivity() {
    private val viewModel: HomeState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.i(TAG, "MainActivity creada")

        setContent {
            RETO_8_ROHO_ATheme {
                val counts by viewModel.counts.collectAsState()
                val context = LocalContext.current
                HomeScreen(
                    counts = counts,
                    onOpenList = { classification -> openList(context, classification) },
                    onAddCompany = { context.startActivity(CompanyFormActivity.newIntent(context)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openList(context: Context, classification: Classification?) {
        Log.i(TAG, "Abrir directorio, filtro inicial: $classification")
        context.startActivity(CompanyListActivity.newIntent(context, classification))
    }
}

@Composable
private fun HomeScreen(
    counts: Map<Classification?, Int>,
    onOpenList: (Classification?) -> Unit,
    onAddCompany: () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroCard(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatsGrid(counts, onOpenList)
                    HomeButtons(onOpenList, onAddCompany)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                HeroCard(modifier = Modifier.fillMaxWidth())
                Text("Resumen", style = MaterialTheme.typography.titleMedium)
                StatsGrid(counts, onOpenList)
                Spacer(Modifier.height(4.dp))
                HomeButtons(onOpenList, onAddCompany)
                Text(
                    text = "Versión 0.8 - Desarrollado por Adrien ROHO",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Cabecera con degradado: logo, nombre de la app y descripción. */
@Composable
private fun HeroCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Brush.linearGradient(listOf(Indigo, Teal)), RoundedCornerShape(28.dp))
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Business, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Directorio TI", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Empresas de desarrollo de software: consultoría, desarrollo a la medida y fábricas de software.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

/** Total + una tarjeta por clasificación. Tocar una tarjeta abre la lista ya filtrada. */
@Composable
private fun StatsGrid(counts: Map<Classification?, Int>, onOpenList: (Classification?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Empresas",
                value = counts[null],
                icon = Icons.Outlined.Business,
                color = MaterialTheme.colorScheme.primary,
                onClick = { onOpenList(null) },
                modifier = Modifier.weight(1f)
            )
            StatCard(
                classification = Classification.CONSULTORIA,
                counts = counts,
                onOpenList = onOpenList,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                classification = Classification.DESARROLLO_MEDIDA,
                counts = counts,
                onOpenList = onOpenList,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                classification = Classification.FABRICA_SOFTWARE,
                counts = counts,
                onOpenList = onOpenList,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    classification: Classification,
    counts: Map<Classification?, Int>,
    onOpenList: (Classification?) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatCard(
        label = classification.label,
        value = counts[classification],
        icon = classification.icon,
        color = classification.color,
        onClick = { onOpenList(classification) },
        modifier = modifier
    )
}

@Composable
private fun StatCard(
    label: String,
    value: Int?,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            Text(
                text = value?.toString() ?: "–",
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeButtons(onOpenList: (Classification?) -> Unit, onAddCompany: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onOpenList(null) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ver directorio", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onAddCompany,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Registrar empresa", fontWeight = FontWeight.SemiBold)
        }
    }
}
