package com.example.reto_9_roho_a.data

import android.location.Location
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cliente mínimo de la API Overpass de OpenStreetMap (HttpURLConnection + org.json). */
object OverpassClient {
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    fun buildQuery(lat: Double, lon: Double, radiusMeters: Int, categories: Collection<PoiCategory>): String {
        val around = "(around:$radiusMeters,$lat,$lon)"
        val statements = categories.flatMap { it.overpassFilters }
            .joinToString("") { "nwr$around$it;" }
        return "[out:json][timeout:25];($statements);out center tags;"
    }

    /** Consulta Overpass probando cada servidor; lanza [IOException] si todos fallan. */
    fun fetch(lat: Double, lon: Double, radiusMeters: Int, categories: Collection<PoiCategory>): List<Poi> {
        if (categories.isEmpty()) return emptyList()
        val query = buildQuery(lat, lon, radiusMeters, categories)
        var lastError: IOException? = null
        for (endpoint in ENDPOINTS) {
            try {
                return parse(post(endpoint, query), lat, lon)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Sin respuesta de Overpass")
    }

    private fun post(endpoint: String, query: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", HTTP_USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use {
                it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Convierte la respuesta JSON en POIs con nombre, ordenados por distancia al centro. */
    fun parse(json: String, centerLat: Double, centerLon: Double): List<Poi> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val result = mutableListOf<Poi>()
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isEmpty()) continue
            // Los nodos traen lat/lon; las vías y relaciones traen "center".
            val position = if (element.has("lat")) element else element.optJSONObject("center") ?: continue
            val lat = position.getDouble("lat")
            val lon = position.getDouble("lon")
            val category = categoryOf(tags) ?: continue
            val distance = FloatArray(1)
            Location.distanceBetween(centerLat, centerLon, lat, lon, distance)
            result += Poi(element.getLong("id"), name, lat, lon, category, distance[0])
        }
        return result.distinctBy { it.id }.sortedBy { it.distanceMeters }
    }

    private fun categoryOf(tags: JSONObject): PoiCategory? = when {
        tags.optString("amenity") == "hospital" -> PoiCategory.HOSPITAL
        tags.optString("amenity") == "pharmacy" -> PoiCategory.FARMACIA
        tags.optString("amenity") == "restaurant" -> PoiCategory.RESTAURANTE
        tags.optString("leisure") == "park" -> PoiCategory.PARQUE
        tags.optString("tourism") in setOf("attraction", "museum", "viewpoint", "artwork") -> PoiCategory.TURISTICO
        else -> null
    }
}
