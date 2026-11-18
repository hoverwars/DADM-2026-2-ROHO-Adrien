package com.example.reto_10_roho_a.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Identificación de la app ante los servicios web (GBIF pide un User-Agent descriptivo). */
const val HTTP_USER_AGENT = "EspeciesAmenazadas-DADM-Reto10/1.0 (Android; proyecto academico)"

/** Error de red o de servicio con un mensaje legible para el usuario. */
class ServicioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Codifica un valor para usarlo dentro de una URL. */
fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

/** Descarga una URL como texto (GET). Lanza [ServicioException] si falla. */
suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", HTTP_USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw ServicioException("El servicio respondió con el código $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    } catch (e: ServicioException) {
        throw e
    } catch (e: Exception) {
        throw ServicioException("No se pudo conectar con el servicio. Revisa tu conexión a internet.", e)
    }
}
