package com.example.reto_10_roho_a.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Especie
import com.example.reto_10_roho_a.data.InfoGbif
import com.example.reto_10_roho_a.data.Nombres
import com.example.reto_10_roho_a.viewmodel.Carga
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel
import com.example.reto_10_roho_a.viewmodel.FichaEstado

/** Ficha completa: datos del listado oficial (datos.gov.co) + información ampliada y foto (GBIF, iNaturalist). */
@Composable
fun PantallaDetalle(especie: Especie, vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FichaEspecie(especie, vm.ficha, mostrarCategoria = true, mostrarDescripciones = true, vm = vm, onReintentar = { vm.reintentarDetalle() })
    }
}

/**
 * Contenido de la ficha de una especie, reutilizado por el detalle y por el juego.
 * En el juego se oculta la categoría de amenaza (y las descripciones, que podrían revelarla).
 */
@Composable
fun FichaEspecie(
    especie: Especie,
    ficha: FichaEstado,
    mostrarCategoria: Boolean,
    mostrarDescripciones: Boolean,
    vm: EspeciesViewModel,
    onReintentar: () -> Unit,
    debajoDelEncabezado: @Composable () -> Unit = {},
) {
    val contexto = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- Encabezado: foto a la izquierda del nombre ----
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            FotoEspecie(ficha, especie)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mostrarCategoria) InsigniaCategoria(especie.categoria, conTexto = true)
                Text(especie.titulo, style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString { append(especie.nombreCientifico); if (especie.autor.isNotEmpty()) append(" ${especie.autor}") },
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (especie.vernaculos.size > 1) {
                    Text(
                        "También llamada: ${especie.vernaculos.drop(1).joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        ficha.credito?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        debajoDelEncabezado()

        Button(onClick = { vm.abrirMapa(especie) }, enabled = especie.gbifKey != null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("  Ver dónde se ha observado en el mapa")
        }

        // ---- Clasificación (datos.gov.co) ----
        Tarjeta("Clasificación taxonómica") {
            Fila("Reino", Nombres.reino(especie.reino))
            Fila("Filo", especie.filo)
            Fila("Clase", Nombres.clase(especie.clase))
            Fila("Orden", especie.orden)
            Fila("Familia", especie.familia)
            Fila("Género", especie.genero)
            Fila("Rango", especie.rango)
            Fila("Estado taxonómico", especie.estadoTaxonomico)
        }
        especie.observaciones?.let {
            Tarjeta("Observaciones del listado") { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        // ---- Información ampliada (GBIF) ----
        Tarjeta("Información de GBIF") {
            when (val info = ficha.info) {
                is Carga.Cargando -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }

                is Carga.Error -> {
                    Text(info.mensaje, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
                }

                is Carga.Listo -> InfoGbifContenido(info.datos, mostrarDescripciones)
            }
        }

        especie.gbifKey?.let { key ->
            OutlinedButton(
                onClick = { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gbif.org/species/$key"))) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Abrir la ficha en gbif.org")
            }
        }
    }
}

/** Foto cuadrada de la especie; al tocarla se abre ampliada. Muestra un loader mientras carga y un icono "sin foto" si no hay foto fiable. */
@Composable
private fun FotoEspecie(ficha: FichaEstado, especie: Especie) {
    val foto = ficha.foto
    var ampliada by remember { mutableStateOf(false) }
    Box(
        Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (foto != null) Modifier.clickable { ampliada = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (foto != null) {
            Image(
                bitmap = foto.asImageBitmap(),
                contentDescription = "Foto de ${especie.titulo}. Toca para ampliar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (ficha.cargandoFoto) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            // Búsqueda terminada sin resultado fiable
            Icon(
                Icons.Outlined.ImageNotSupported,
                contentDescription = "Sin foto disponible",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (ampliada && foto != null) {
        FotoAmpliada(foto, especie.titulo, ficha.credito, onCerrar = { ampliada = false })
    }
}

/** Foto a pantalla completa: pellizca para hacer zoom, arrastra para moverla y toca el fondo o la X para cerrar. */
@Composable
private fun FotoAmpliada(foto: Bitmap, titulo: String, credito: String?, onCerrar: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onCerrar)) {
            Image(
                bitmap = foto.asImageBitmap(),
                contentDescription = "Foto ampliada de $titulo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestoZoom, _ ->
                            zoom = (zoom * gestoZoom).coerceIn(1f, 5f)
                            desplazamiento = if (zoom == 1f) Offset.Zero else desplazamiento + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = desplazamiento.x,
                        translationY = desplazamiento.y,
                    ),
            )
            IconButton(onClick = onCerrar, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            credito?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoGbifContenido(info: InfoGbif, mostrarDescripciones: Boolean) {
    Fila("Nombre en inglés", info.nombreIngles ?: "—")
    Fila("Estado en GBIF", info.estadoTaxonomico?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—")
    Fila("Observaciones en Colombia", "%,d".format(info.totalObservaciones))
    if (info.departamentos.isNotEmpty()) {
        Text("Departamentos con más registros", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        info.departamentos.forEach { (nombre, total) -> Fila(nombre, "%,d".format(total)) }
    }
    if (mostrarDescripciones) {
        info.descripciones.take(4).forEach { (tipo, texto) ->
            Text(
                tipo.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(texto.take(500), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (info.totalObservaciones == 0) {
        Text(
            "GBIF no tiene observaciones georreferenciadas de esta especie en Colombia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    if (valor.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}
