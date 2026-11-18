package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Categoria
import com.example.reto_10_roho_a.data.Especie
import com.example.reto_10_roho_a.data.Nombres
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel

/** Lista de especies con los filtros (categoría, reino, clase y texto) enviados al servicio web. */
@Composable
fun PantallaExplorar(vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    val filtro = vm.filtro
    val estadoLista = rememberLazyListState()

    // Carga la siguiente página cuando el usuario se acerca al final
    val cercaDelFinal by remember {
        derivedStateOf {
            val ultimo = estadoLista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimo >= vm.lista.size - 5
        }
    }
    LaunchedEffect(cercaDelFinal, vm.lista.size) { if (cercaDelFinal) vm.cargarMas() }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filtro.texto,
            onValueChange = { vm.cambiarFiltro(filtro.copy(texto = it), conRetraso = true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Buscar por nombre científico o común") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filtro.texto.isNotEmpty()) {
                    IconButton(onClick = { vm.cambiarFiltro(filtro.copy(texto = "")) }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            singleLine = true,
        )

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filtro.categoria == null,
                    onClick = { vm.cambiarFiltro(filtro.copy(categoria = null)) },
                    label = { Text("Todas") },
                )
            }
            items(Categoria.entries) { c ->
                FilterChip(
                    selected = filtro.categoria == c,
                    onClick = { vm.cambiarFiltro(filtro.copy(categoria = if (filtro.categoria == c) null else c)) },
                    label = { Text("${c.codigo} · ${c.etiqueta}") },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            item {
                FilterChip(selected = filtro.reino == null, onClick = { vm.cambiarReino(null) }, label = { Text("Todos los reinos") })
            }
            items(listOf("Animalia", "Plantae", "Fungi")) { reino ->
                FilterChip(
                    selected = filtro.reino == reino,
                    onClick = { vm.cambiarReino(if (filtro.reino == reino) null else reino) },
                    label = { Text(Nombres.reino(reino).substringBefore(" ·")) },
                )
            }
        }
        if (filtro.reino != null && vm.clasesDisponibles.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                items(vm.clasesDisponibles) { g ->
                    FilterChip(
                        selected = filtro.clase == g.nombre,
                        onClick = {
                            vm.cambiarFiltro(filtro.copy(clase = if (filtro.clase == g.nombre) null else g.nombre))
                        },
                        label = { Text("${Nombres.clase(g.nombre).substringBefore(" ·")} (${g.total})") },
                    )
                }
            }
        }

        val partes = listOfNotNull(filtro.orden?.let { "Orden $it" }, filtro.familia?.let { "Familia $it" })
        Text(
            text = (if (vm.recargandoLista) "Buscando…" else "${vm.totalLista} especies") +
                if (partes.isNotEmpty()) " · ${partes.joinToString(" · ")}" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Barra de progreso fina: aparece en cuanto cambia un filtro
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (vm.recargandoLista) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Box(Modifier.fillMaxSize().alpha(if (vm.recargandoLista && vm.lista.isNotEmpty()) 0.4f else 1f)) {
            when {
                vm.errorLista != null && vm.lista.isEmpty() ->
                    MensajeError(vm.errorLista ?: "", onReintentar = { vm.recargarLista() })

                vm.lista.isEmpty() && !vm.cargandoLista ->
                    Text(
                        "Ninguna especie coincide con los filtros.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                else -> LazyColumn(
                    state = estadoLista,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.lista, key = { (it.gbifKey ?: 0L) * 31 + it.nombreCientifico.hashCode() }) { especie ->
                        TarjetaEspecie(especie, onClick = { vm.abrirDetalle(especie) })
                    }
                    if (vm.cargandoLista) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
            if (vm.cargandoLista && vm.lista.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun TarjetaEspecie(especie: Especie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    especie.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (especie.nombreVernaculo != null) {
                    Text(
                        especie.nombreCientifico,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    listOf(Nombres.clase(especie.clase).substringBefore(" ·"), especie.familia)
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InsigniaCategoria(especie.categoria, Modifier.padding(start = 8.dp))
        }
    }
}
