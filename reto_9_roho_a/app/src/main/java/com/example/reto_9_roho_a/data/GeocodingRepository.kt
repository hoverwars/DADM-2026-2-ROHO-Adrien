package com.example.reto_9_roho_a.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/** Convierte una dirección en coordenadas: Geocoder de Android y, si falla, Nominatim (OSM). */
class GeocodingRepository(context: Context) {
    private val appContext = context.applicationContext

    /** Devuelve (lat, lon) o null si no se encontró la dirección. */
    suspend fun geocode(address: String): Pair<Double, Double>? {
        val query = address.trim()
        if (query.isEmpty()) return null
        return withGeocoder(query) ?: withNominatim(query)
    }

    private suspend fun withGeocoder(query: String): Pair<Double, Double>? = try {
        if (!Geocoder.isPresent()) null else {
            val geocoder = Geocoder(appContext, Locale("es"))
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull()?.let { it.latitude to it.longitude })
                        }

                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
            }
        }
    } catch (e: Exception) {
        null
    }

    private suspend fun withNominatim(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                URLEncoder.encode(query, "UTF-8")
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", HTTP_USER_AGENT)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                if (results.length() == 0) null else {
                    val first = results.getJSONObject(0)
                    first.getString("lat").toDouble() to first.getString("lon").toDouble()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
