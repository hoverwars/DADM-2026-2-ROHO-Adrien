package com.example.reto_9_roho_a.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reto_9_roho_a.data.PoiCategory
import com.example.reto_9_roho_a.data.SettingsStore
import com.example.reto_9_roho_a.ui.theme.RETO_9_ROHO_ATheme

/** Preferencias: radio de búsqueda en km y categorías de puntos de interés. Se guardan al instante. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(this)
        setContent {
            RETO_9_ROHO_ATheme {
                SettingsScreen(store, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    var radius by remember { mutableFloatStateOf(store.radiusKm.toFloat()) }
    var categories by remember { mutableStateOf(store.categories) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text("Radio de búsqueda", style = MaterialTheme.typography.titleMedium)
            Text("${radius.toInt()} km", style = MaterialTheme.typography.headlineSmall)
            Slider(
                value = radius,
                onValueChange = { radius = it },
                onValueChangeFinished = { store.radiusKm = radius.toInt() },
                valueRange = SettingsStore.MIN_RADIUS_KM.toFloat()..SettingsStore.MAX_RADIUS_KM.toFloat(),
                steps = SettingsStore.MAX_RADIUS_KM - SettingsStore.MIN_RADIUS_KM - 1
            )

            Text(
                "Categorías",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            PoiCategory.entries.forEach { category ->
                val checked = category in categories
                Row(
                    Modifier.fillMaxWidth().clickable { categories = toggle(store, categories, category) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { categories = toggle(store, categories, category) })
                    Text(category.label)
                }
            }
        }
    }
}

private fun toggle(store: SettingsStore, current: Set<PoiCategory>, category: PoiCategory): Set<PoiCategory> {
    val next = if (category in current) current - category else current + category
    store.categories = next
    return next
}
