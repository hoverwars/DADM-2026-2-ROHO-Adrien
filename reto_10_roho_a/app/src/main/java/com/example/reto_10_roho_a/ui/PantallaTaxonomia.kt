package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Categoria
import com.example.reto_10_roho_a.data.Nivel
import com.example.reto_10_roho_a.data.Nombres
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel

/** Explorador jerárquico: Reino > Clase > Orden > Familia, con el número de especies de cada grupo. */
@Composable
fun PantallaTaxonomia(vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    val filtro = vm.filtroTaxonomia
    val nivel = vm.nivelActual

    Column(modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filtro.categoria == null,
                    onClick = { vm.filtrarTaxonomiaPorCategoria(null) },
                    label = { Text("Todas") },
                )
            }
            items(Categoria.entries) { c ->
                FilterChip(
                    selected = filtro.categoria == c,
                    onClick = { vm.filtrarTaxonomiaPorCategoria(if (filtro.categoria == c) null else c) },
                    label = { Text(c.codigo) },
                )
            }
        }

        // Ruta de navegación (migas de pan): permite volver a cualquier nivel anterior
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item { AssistChip(onClick = { vm.subirA(null) }, label = { Text("Inicio") }) }
            items(Nivel.entries.filter { filtro.valor(it) != null }) { n ->
                AssistChip(
                    onClick = { vm.subirA(n.siguiente()) },
                    label = { Text(Nombres.de(n, filtro.valor(n) ?: "").substringBefore(" ·")) },
                )
            }
        }

        Text(
            text = if (nivel != null) "Elige ${nivel.etiqueta.lowercase()}" else "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )

        EstadoCarga(vm.grupos, onReintentar = { vm.cargarGrupos() }) { grupos ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filtro.reino != null) {
                    item {
                        Button(
                            onClick = { vm.explorar(filtro) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Ver todas las especies de esta selección") }
                    }
                }
                items(grupos, key = { it.nombre }) { g ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { vm.bajarNivel(g.nombre) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                Nombres.de(nivel ?: Nivel.FAMILIA, g.nombre),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${g.total}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
