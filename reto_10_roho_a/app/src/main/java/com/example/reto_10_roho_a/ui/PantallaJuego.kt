package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.reto_10_roho_a.data.Categoria
import com.example.reto_10_roho_a.viewmodel.EspeciesViewModel

/**
 * Juego: se muestra la ficha de una especie al azar, SIN su categoría de amenaza, y hay que adivinarla.
 * Al responder se revela la categoría.
 */
@Composable
fun PantallaJuego(vm: EspeciesViewModel, modifier: Modifier = Modifier) {
    EstadoCarga(vm.pregunta, onReintentar = { vm.nuevaPregunta() }) { pregunta ->
        val especie = pregunta.especie
        val respondida = pregunta.respondida
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Aciertos: ${vm.aciertos} de ${vm.jugadas}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("¿Qué categoría de amenaza tiene esta especie?", style = MaterialTheme.typography.titleMedium)

            FichaEspecie(
                especie = especie,
                ficha = vm.fichaJuego,
                mostrarCategoria = respondida != null,
                mostrarDescripciones = respondida != null,
                vm = vm,
                onReintentar = { vm.reintentarFichaJuego() },
                debajoDelEncabezado = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Categoria.entries.forEach { categoria ->
                            val esCorrecta = categoria == especie.categoria
                            val textoSobre = if (categoria == Categoria.VU) Color.Black else Color.White
                            Button(
                                onClick = { vm.responder(categoria) },
                                enabled = respondida == null,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = categoria.color,
                                    contentColor = textoSobre,
                                    disabledContainerColor = if (respondida != null && esCorrecta) categoria.color else categoria.color.copy(alpha = 0.25f),
                                    disabledContentColor = if (respondida != null && esCorrecta) textoSobre else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) { Text("${categoria.codigo} · ${categoria.etiqueta}") }
                        }
                        if (respondida != null) {
                            val acierto = respondida == especie.categoria
                            Text(
                                if (acierto) "¡Correcto!" else "No era esa: es ${especie.categoria?.codigo} · ${especie.categoria?.etiqueta}.",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (acierto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.nuevaPregunta() }) { Text("Siguiente") }
                            }
                        }
                    }
                },
            )
        }
    }
}
