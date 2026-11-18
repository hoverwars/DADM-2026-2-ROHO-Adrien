package com.example.reto_10_roho_a.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** Un registro de observación de una especie en Colombia (GBIF). */
data class Observacion(
    val lat: Double,
    val lon: Double,
    val fecha: String?,
    val departamento: String?,
    val municipio: String?,
    val fuente: String?,
    val tipo: String?,
)

/** Observaciones para dibujar en el mapa y su total real en GBIF. */
data class Observaciones(val puntos: List<Observacion>, val total: Int)

/** Información complementaria de una especie obtenida de GBIF. */
data class InfoGbif(
    val nombreIngles: String?,
    val estadoTaxonomico: String?,
    val descripciones: List<Pair<String, String>>,
    val totalObservaciones: Int,
    val departamentos: List<Pair<String, Int>>,
)

/**
 * Cliente de la API pública de GBIF (api.gbif.org), segundo servicio web de la app.
 * Se consulta con el identificador GBIF que trae el dataset de datos.gov.co.
 */
class GbifRepository {
    private val base = "https://api.gbif.org/v1"

    private fun filtroColombia(key: Long) =
        "taxonKey=$key&country=CO&hasCoordinate=true&hasGeospatialIssue=false"

    /** Descarga hasta [maximo] observaciones georreferenciadas en Colombia (páginas de 300). */
    suspend fun observaciones(key: Long, maximo: Int = 1200): Observaciones {
        val puntos = mutableListOf<Observacion>()
        var total = 0
        var desplazamiento = 0
        while (desplazamiento < maximo) {
            val json = JSONObject(
                httpGet("$base/occurrence/search?${filtroColombia(key)}&limit=300&offset=$desplazamiento")
            )
            total = json.optInt("count", total)
            val resultados = json.optJSONArray("results") ?: break
            for (i in 0 until resultados.length()) {
                val o = resultados.getJSONObject(i)
                if (!o.has("decimalLatitude") || !o.has("decimalLongitude")) continue
                puntos += Observacion(
                    lat = o.getDouble("decimalLatitude"),
                    lon = o.getDouble("decimalLongitude"),
                    fecha = o.optString("eventDate").takeIf { it.isNotBlank() }?.take(10),
                    departamento = o.optString("stateProvince").takeIf { it.isNotBlank() },
                    municipio = o.optString("county").takeIf { it.isNotBlank() },
                    fuente = o.optString("datasetName").takeIf { it.isNotBlank() },
                    tipo = o.optString("basisOfRecord").takeIf { it.isNotBlank() },
                )
            }
            desplazamiento += 300
            if (json.optBoolean("endOfRecords", true) || resultados.length() == 0) break
        }
        return Observaciones(puntos, total)
    }

    /** Ficha ampliada: nombre en inglés, descripciones y departamentos con más registros. */
    suspend fun info(key: Long): InfoGbif = coroutineScope {
        val especie = async { runCatching { JSONObject(httpGet("$base/species/$key")) }.getOrNull() }
        val descripciones = async { runCatching { JSONObject(httpGet("$base/species/$key/descriptions?limit=20")) }.getOrNull() }
        val resumen = async {
            JSONObject(httpGet("$base/occurrence/search?taxonKey=$key&country=CO&facet=stateProvince&facetLimit=5&limit=0"))
        }
        val resumenJson = resumen.await()
        val departamentos = resumenJson.optJSONArray("facets")?.optJSONObject(0)?.optJSONArray("counts")
            ?.let { arr -> List(arr.length()) { arr.getJSONObject(it).getString("name") to arr.getJSONObject(it).getInt("count") } }
            ?: emptyList()

        val textos = descripciones.await()?.optJSONArray("results")?.let { arr ->
            List(arr.length()) { arr.getJSONObject(it) }
                .mapNotNull { d ->
                    val texto = d.optString("description").trim()
                    val tipo = d.optString("type").ifBlank { "Información" }
                    if (texto.isBlank()) null else tipo to texto
                }
                .distinct()
        } ?: emptyList()

        InfoGbif(
            nombreIngles = especie.await()?.optString("vernacularName")?.takeIf { it.isNotBlank() },
            estadoTaxonomico = especie.await()?.optString("taxonomicStatus")?.takeIf { it.isNotBlank() },
            descripciones = textos,
            totalObservaciones = resumenJson.optInt("count", 0),
            departamentos = departamentos,
        )
    }
}
