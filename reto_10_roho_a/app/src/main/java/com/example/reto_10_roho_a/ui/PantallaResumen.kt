package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Filtro
import com.example.reto_10_roho_a.data.Grupo
import com.example.reto_10_roho_a.data.Nivel
import com.example.reto_10_roho_a.data.Nombres
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel

/** Estadísticas del listado: especies por categoría, por reino y por clase (barras). */
@Composable
fun PantallaResumen(vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    EstadoCarga(vm.resumen, onReintentar = { vm.cargarResumen() }) { resumen ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${resumen.total} especies amenazadas en Colombia", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Fuente: Listado oficial de especies silvestres amenazadas (datos.gov.co). Toca una barra para ver las especies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Seccion("Por categoría de amenaza") {
                val maximo = resumen.porCategoria.maxOfOrNull { it.second } ?: 1
                resumen.porCategoria.forEach { (categoria, total) ->
                    Barra(
                        etiqueta = "${categoria.codigo} · ${categoria.etiqueta}",
                        valor = total,
                        maximo = maximo,
                        color = categoria.color,
                        onClick = { vm.explorar(Filtro(categoria = categoria)) },
                    )
                }
            }
            Seccion("Por reino") {
                val maximo = resumen.porReino.maxOfOrNull { it.total } ?: 1
                resumen.porReino.forEach { g -> BarraGrupo(g, maximo, Nivel.REINO, vm) }
            }
            Seccion("Grupos con más especies") {
                val maximo = resumen.porClase.maxOfOrNull { it.total } ?: 1
                resumen.porClase.forEach { g -> BarraGrupo(g, maximo, Nivel.CLASE, vm) }
            }
        }
    }
}

@Composable
private fun BarraGrupo(g: Grupo, maximo: Int, nivel: Nivel, vm: EspeciesViewModel) {
    Barra(
        etiqueta = Nombres.de(nivel, g.nombre).substringBefore(" ·"),
        valor = g.total,
        maximo = maximo,
        color = MaterialTheme.colorScheme.primary,
        onClick = {
            vm.explorar(if (nivel == Nivel.REINO) Filtro(reino = g.nombre) else Filtro(clase = g.nombre))
        },
    )
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

@Composable
private fun Barra(etiqueta: String, valor: Int, maximo: Int, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$valor", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction = (valor.toFloat() / maximo).coerceIn(0.02f, 1f))
                    .background(color, RoundedCornerShape(5.dp))
            )
        }
    }
}
