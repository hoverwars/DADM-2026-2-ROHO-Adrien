package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Especie
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel

/** Mapa con las observaciones de la especie en Colombia (servicio GBIF). */
@Composable
fun PantallaMapa(especie: Especie, vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = vm.mapaDeCalor, onClick = { vm.mapaDeCalor = true }, label = { Text("Mapa de calor") })
            FilterChip(selected = !vm.mapaDeCalor, onClick = { vm.mapaDeCalor = false }, label = { Text("Puntos") })
        }
        Box(Modifier.fillMaxSize()) {
            EstadoCarga(vm.observaciones, onReintentar = { vm.reintentarMapa() }) { obs ->
                if (obs.puntos.isEmpty()) {
                    Text(
                        "GBIF no tiene observaciones georreferenciadas de ${especie.titulo} en Colombia.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MapaObservaciones(obs.puntos, vm.mapaDeCalor, especie.titulo, Modifier.fillMaxSize())
                    Text(
                        text = if (obs.total > obs.puntos.size)
                            "Mostrando ${obs.puntos.size} de %,d observaciones (GBIF)".format(obs.total)
                        else "%,d observaciones (GBIF)".format(obs.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
