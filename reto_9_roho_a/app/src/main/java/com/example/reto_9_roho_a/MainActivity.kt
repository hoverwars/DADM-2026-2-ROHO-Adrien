package com.example.reto_9_roho_a

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.reto_9_roho_a.data.HTTP_USER_AGENT
import com.example.reto_9_roho_a.data.Poi
import com.example.reto_9_roho_a.data.PoiCategory
import com.example.reto_9_roho_a.data.toDistanceLabel
import com.example.reto_9_roho_a.ui.PoiMap
import com.example.reto_9_roho_a.ui.SettingsActivity
import com.example.reto_9_roho_a.ui.theme.RETO_9_ROHO_ATheme
import com.example.reto_9_roho_a.viewmodel.MapState
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MapState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid()
        enableEdgeToEdge()
        setContent {
            RETO_9_ROHO_ATheme {
                MapScreen(viewModel, onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) })
            }
        }
    }

    /** osmdroid exige un user agent propio; el caché va en cacheDir para no pedir permisos de almacenamiento. */
    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        config.userAgentValue = HTTP_USER_AGENT
        // Ancien cache : il contient les tuiles « Access blocked » reçues avec le mauvais user agent.
        File(cacheDir, "osmdroid").deleteRecursively()
        val base = File(cacheDir, "osmdroid_tiles")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }
}

private fun hasLocationPermission(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun MapScreen(viewModel: MapState, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var listExpanded by rememberSaveable { mutableStateOf(false) }

    // Los ajustes (radio, categorías) pueden cambiar en SettingsActivity: se releen al volver.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadSettings()
        })
    }

    fun locate() {
        if (viewModel.isLocationEnabled()) {
            viewModel.useMyLocation()
        } else {
            viewModel.showMessage("El GPS está desactivado.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locate()
        else viewModel.showMessage("Sin permiso de ubicación. Puedes buscar por dirección.")
    }

    fun requestLocation() {
        if (hasLocationPermission(context)) locate()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // Primera apertura: si ya hay permiso, se centra solo en la posición actual.
    LaunchedEffect(Unit) {
        if (viewModel.state.value.center == null && hasLocationPermission(context)) locate()
        else if (viewModel.state.value.center == null) requestLocation()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val gpsOff = message.startsWith("El GPS")
        val result = snackbarHost.showSnackbar(
            message = message,
            actionLabel = if (gpsOff) "Activar" else null,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        viewModel.consumeMessage()
    }

    Box(Modifier.fillMaxSize()) {
        PoiMap(
            center = state.center,
            radiusKm = state.radiusKm,
            pois = state.visiblePois,
            selectedPoiId = state.selectedPoiId,
            onPoiSelected = viewModel::selectPoi,
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.safeDrawingPadding().padding(12.dp)) {
            SearchCard(
                address = state.address,
                loading = state.loading,
                onAddressChange = viewModel::onAddressChange,
                onSearch = viewModel::searchAddress,
                onMyLocation = ::requestLocation,
                onSettings = onOpenSettings
            )
            Spacer(Modifier.size(8.dp))
            CategoryChips(state.activeCategories, viewModel::toggleCategory)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(12.dp)
        ) {
            if (state.center != null) {
                PoiListCard(
                    pois = state.visiblePois,
                    radiusKm = state.radiusKm,
                    searched = state.searched,
                    expanded = listExpanded,
                    selectedId = state.selectedPoiId,
                    onToggle = { listExpanded = !listExpanded },
                    onSelect = viewModel::selectPoi
                )
            }
            SnackbarHost(snackbarHost)
        }
    }
}

@Composable
private fun SearchCard(
    address: String,
    loading: Boolean,
    onAddressChange: (String) -> Unit,
    onSearch: () -> Unit,
    onMyLocation: () -> Unit,
    onSettings: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar dirección") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailingIcon = {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Buscar") }
                }
            )
            IconButton(onClick = onMyLocation) { Icon(Icons.Outlined.MyLocation, "Mi ubicación") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ajustes") }
        }
    }
}

@Composable
private fun CategoryChips(active: Set<PoiCategory>, onToggle: (PoiCategory) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PoiCategory.entries) { category ->
            FilterChip(
                selected = category in active,
                onClick = { onToggle(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    Box(Modifier.size(10.dp).background(Color(category.colorArgb), CircleShape))
                }
            )
        }
    }
}

@Composable
private fun PoiListCard(
    pois: List<Poi>,
    radiusKm: Int,
    searched: Boolean,
    expanded: Boolean,
    selectedId: Long?,
    onToggle: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = when {
                !searched -> "Buscando puntos de interés…"
                pois.isEmpty() -> "No se encontraron puntos de interés en $radiusKm km"
                else -> "${pois.size} puntos de interés en $radiusKm km"
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null)
        }
        if (expanded && pois.isNotEmpty()) {
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(pois, key = { it.id }) { poi ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(poi.id) }
                            .background(
                                if (poi.id == selectedId) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).background(Color(poi.category.colorArgb), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(poi.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                poi.category.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(poi.distanceMeters.toDistanceLabel(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
