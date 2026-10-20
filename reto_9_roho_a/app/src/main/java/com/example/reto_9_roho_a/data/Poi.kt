package com.example.reto_9_roho_a.data

import java.util.Locale

/** Punto de interés devuelto por Overpass. [distanceMeters] se calcula respecto al centro de búsqueda. */
data class Poi(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: PoiCategory,
    val distanceMeters: Float = 0f
)

/** Formatea una distancia: "850 m" o "1,2 km". */
fun Float.toDistanceLabel(): String =
    if (this < 1000f) "${toInt()} m" else String.format(Locale("es"), "%.1f km", this / 1000f)
