package com.example.reto_9_roho_a.data

/**
 * Categorías de puntos de interés. Cada una conoce su etiqueta en español,
 * su color de marcador y los filtros Overpass ([overpassFilters]) que la definen.
 */
enum class PoiCategory(
    val label: String,
    val colorArgb: Int,
    val overpassFilters: List<String>
) {
    HOSPITAL("Hospital", 0xFFD32F2F.toInt(), listOf("[amenity=hospital]")),
    FARMACIA("Farmacia", 0xFF2E7D32.toInt(), listOf("[amenity=pharmacy]")),
    TURISTICO(
        "Lugar turístico", 0xFFF57C00.toInt(),
        listOf("[tourism~\"^(attraction|museum|viewpoint|artwork)$\"]")
    ),
    RESTAURANTE("Restaurante", 0xFF6A1B9A.toInt(), listOf("[amenity=restaurant]")),
    PARQUE("Parque", 0xFF00897B.toInt(), listOf("[leisure=park]"));

    companion object {
        fun fromKey(key: String): PoiCategory? = entries.firstOrNull { it.name == key }
    }
}
