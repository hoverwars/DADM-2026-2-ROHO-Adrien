package com.example.reto_10_roho_a.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Descarga una imagen reduciéndola para no gastar memoria (las fotos originales son enormes). */
suspend fun cargarImagen(url: String, anchoMax: Int = 900): Bitmap? = withContext(Dispatchers.IO) {
    try {
        // iNaturalist ofrece una versión mediana de cada foto
        val destino = url.replace("/original.", "/medium.")
        val connection = URL(destino).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", HTTP_USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val bytes = connection.inputStream.use { it.readBytes() }
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
            var muestreo = 1
            while (limites.outWidth / (muestreo * 2) >= anchoMax) muestreo *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = muestreo })
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
