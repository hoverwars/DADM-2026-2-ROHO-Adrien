package com.example.reto_9_roho_a.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PoiRepository {
    /** Puntos de interés a menos de [radiusKm] km de (lat, lon), ordenados por distancia. */
    suspend fun nearby(
        lat: Double,
        lon: Double,
        radiusKm: Int,
        categories: Collection<PoiCategory>
    ): List<Poi> = withContext(Dispatchers.IO) {
        OverpassClient.fetch(lat, lon, radiusKm * 1000, categories)
            .filter { it.distanceMeters <= radiusKm * 1000 }
            // Tope por categoría (y no global) para que los más cercanos de una categoría
            // abundante, como los restaurantes, no tapen a las demás ni al resto del radio.
            .groupBy { it.category }
            .flatMap { (_, pois) -> pois.take(MAX_POIS_PER_CATEGORY) }
            .sortedBy { it.distanceMeters }
    }

    private companion object {
        const val MAX_POIS_PER_CATEGORY = 40
    }
}
