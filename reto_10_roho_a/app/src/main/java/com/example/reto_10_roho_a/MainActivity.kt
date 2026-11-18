package com.example.reto_10_roho_a

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.reto_10_roho_a.data.HTTP_USER_AGENT
import com.example.reto_10_roho_a.ui.PantallaDetalle
import com.example.reto_10_roho_a.ui.PantallaExplorar
import com.example.reto_10_roho_a.ui.PantallaJuego
import com.example.reto_10_roho_a.ui.PantallaMapa
import com.example.reto_10_roho_a.ui.PantallaResumen
import com.example.reto_10_roho_a.ui.PantallaTaxonomia
import com.example.reto_10_roho_a.ui.theme.RETO_10_ROHO_ATheme
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel
import com.example.reto_10_roho_a.viewmodel.Pantalla
import com.example.reto_10_roho_a.viewmodel.Pestana
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: EspeciesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurarOsmdroid()
        enableEdgeToEdge()
        setContent {
            RETO_10_ROHO_ATheme { Aplicacion(viewModel) }
        }
    }

    /** osmdroid exige un user agent propio; el caché va en cacheDir para no pedir permisos de almacenamiento. */
    private fun configurarOsmdroid() {
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        config.userAgentValue = HTTP_USER_AGENT
        val base = File(cacheDir, "osmdroid_tiles")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Aplicacion(vm: EspeciesViewModel) {
    val pantalla = vm.pila.lastOrNull()
    BackHandler(enabled = pantalla != null) { vm.atras() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (pantalla) {
                            is Pantalla.Detalle -> pantalla.especie.nombreCientifico
                            is Pantalla.Mapa -> "Mapa · ${pantalla.especie.titulo}"
                            null -> "Especies amenazadas de Colombia"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (pantalla != null) {
                        IconButton(onClick = { vm.atras() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (pantalla == null) {
                NavigationBar {
                    Pestana.entries.forEach { p ->
                        NavigationBarItem(
                            selected = vm.pestana == p,
                            onClick = { vm.pestana = p },
                            icon = {
                                Icon(
                                    when (p) {
                                        Pestana.EXPLORAR -> Icons.Outlined.Pets
                                        Pestana.TAXONOMIA -> Icons.Outlined.AccountTree
                                        Pestana.RESUMEN -> Icons.Outlined.BarChart
                                        Pestana.JUEGO -> Icons.Outlined.Quiz
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(p.titulo) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modificador = Modifier.padding(padding)
        when (pantalla) {
            is Pantalla.Detalle -> PantallaDetalle(pantalla.especie, vm, modificador)
            is Pantalla.Mapa -> PantallaMapa(pantalla.especie, vm, modificador)
            null -> when (vm.pestana) {
                Pestana.EXPLORAR -> PantallaExplorar(vm, modificador)
                Pestana.TAXONOMIA -> PantallaTaxonomia(vm, modificador)
                Pestana.RESUMEN -> PantallaResumen(vm, modificador)
                Pestana.JUEGO -> PantallaJuego(vm, modificador)
            }
        }
    }
}
