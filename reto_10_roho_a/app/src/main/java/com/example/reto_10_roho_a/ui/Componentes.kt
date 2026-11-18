package com.example.reto_10_roho_a.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reto_10_roho_a.data.Categoria
import com.example.reto_10_roho_a.viewmodel.Carga

/** Insignia de color con el código de la categoría de amenaza (CR, EN, VU). */
@Composable
fun InsigniaCategoria(categoria: Categoria?, modifier: Modifier = Modifier, conTexto: Boolean = false) {
    if (categoria == null) return
    Box(
        modifier = modifier
            .background(categoria.color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = if (conTexto) "${categoria.codigo} · ${categoria.etiqueta}" else categoria.codigo,
            color = if (categoria == Categoria.VU) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Muestra el estado de una carga: indicador, error con reintento, o el contenido. */
@Composable
fun <T> EstadoCarga(carga: Carga<T>, onReintentar: () -> Unit, contenido: @Composable (T) -> Unit) {
    when (carga) {
        is Carga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is Carga.Error -> MensajeError(carga.mensaje, onReintentar)
        is Carga.Listo -> contenido(carga.datos)
    }
}

@Composable
fun MensajeError(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(mensaje, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            Button(onClick = onReintentar) { Text("Reintentar") }
        }
    }
}
