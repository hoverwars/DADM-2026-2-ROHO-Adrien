package com.example.reto_10_roho_a.data

import org.json.JSONObject

/** Foto de una especie con su crédito. */
data class Foto(val url: String, val credito: String, val fuente: String)

/**
 * Busca una foto fiable de la especie. Fuentes, por orden:
 * 1. iNaturalist (taxa): foto representativa elegida por la comunidad. Solo se acepta si el nombre
 *    científico coincide EXACTAMENTE (así se evitan sinónimos o especies parecidas).
 * 2. GBIF: observaciones de iNaturalist de grado de investigación (identificación confirmada por
 *    la comunidad) de esa especie en Colombia.
 * Si ninguna fuente cumple, no se muestra foto.
 */
class FotoRepository {
    suspend fun buscar(nombreCientifico: String, gbifKey: Long?): Foto? =
        deINaturalist(nombreCientifico) ?: gbifKey?.let { deGbif(it) }

    private suspend fun deINaturalist(nombre: String): Foto? = try {
        val json = JSONObject(
            httpGet("https://api.inaturalist.org/v1/taxa?rank=species&per_page=5&q=${nombre.urlEncode()}")
        )
        val resultados = json.optJSONArray("results")
        var foto: Foto? = null
        if (resultados != null) {
            for (i in 0 until resultados.length()) {
                val taxon = resultados.getJSONObject(i)
                if (!taxon.optString("name").equals(nombre, ignoreCase = true)) continue
                val f = taxon.optJSONObject("default_photo") ?: continue
                val url = f.optString("medium_url")
                if (url.startsWith("http")) {
                    foto = Foto(url, f.optString("attribution").ifBlank { "iNaturalist" }, "iNaturalist")
                    break
                }
            }
        }
        foto
    } catch (e: ServicioException) {
        null
    }

    private suspend fun deGbif(key: Long): Foto? = try {
        val json = JSONObject(
            httpGet(
                "https://api.gbif.org/v1/occurrence/search?taxonKey=$key&country=CO&mediaType=StillImage" +
                    "&datasetKey=$INATURALIST_DATASET&limit=5"
            )
        )
        val resultados = json.optJSONArray("results")
        var foto: Foto? = null
        if (resultados != null) {
            loop@ for (i in 0 until resultados.length()) {
                val media = resultados.getJSONObject(i).optJSONArray("media") ?: continue
                for (j in 0 until media.length()) {
                    val m = media.getJSONObject(j)
                    val id = m.optString("identifier")
                    if (m.optString("type") == "StillImage" && id.startsWith("http")) {
                        val autor = m.optString("rightsHolder").ifBlank { m.optString("creator") }
                        foto = Foto(id, if (autor.isBlank()) "iNaturalist" else "© $autor", "GBIF / iNaturalist")
                        break@loop
                    }
                }
            }
        }
        foto
    } catch (e: ServicioException) {
        null
    }

    private companion object {
        const val INATURALIST_DATASET = "50c9509d-22c7-4a22-a47d-8c48425ef4a7"
    }
}
