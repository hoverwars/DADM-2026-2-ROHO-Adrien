package com.example.reto_10_roho_a.data

import org.json.JSONArray

/** Parámetros que se envían al servicio web para filtrar el listado de especies. */
data class Filtro(
    val categoria: Categoria? = null,
    val reino: String? = null,
    val clase: String? = null,
    val orden: String? = null,
    val familia: String? = null,
    val texto: String = "",
) {
    /** Devuelve el valor del nivel taxonómico indicado. */
    fun valor(nivel: Nivel): String? = when (nivel) {
        Nivel.REINO -> reino
        Nivel.CLASE -> clase
        Nivel.ORDEN -> orden
        Nivel.FAMILIA -> familia
    }

    /** Copia del filtro con el nivel indicado fijado (y los niveles inferiores borrados). */
    fun conNivel(nivel: Nivel, valor: String?): Filtro {
        var f = this
        f = when (nivel) {
            Nivel.REINO -> f.copy(reino = valor, clase = null, orden = null, familia = null)
            Nivel.CLASE -> f.copy(clase = valor, orden = null, familia = null)
            Nivel.ORDEN -> f.copy(orden = valor, familia = null)
            Nivel.FAMILIA -> f.copy(familia = valor)
        }
        return f
    }
}

/**
 * Cliente del servicio web de datos.gov.co (Socrata SODA) para el dataset
 * "Listado oficial de las especies silvestres amenazadas de la diversidad" (wizt-zh64).
 */
class EspecieRepository {
    private val base = "https://www.datos.gov.co/resource/wizt-zh64.json"

    /** Construye la cláusula $where de SoQL a partir del filtro. */
    private fun condiciones(f: Filtro): String {
        val partes = mutableListOf<String>()
        f.categoria?.let { partes += "estado_de_amenaza='${it.codigo}'" }
        for (nivel in Nivel.entries) f.valor(nivel)?.let { partes += "${nivel.campo}='${it.soql()}'" }
        val texto = f.texto.trim().lowercase()
        if (texto.isNotEmpty()) {
            val t = texto.soql()
            partes += "(lower(nombre_cientifico) like '%$t%' OR lower(nombre_vernaculo) like '%$t%')"
        }
        return partes.joinToString(" AND ")
    }

    private fun String.soql() = replace("'", "''")

    private fun url(parametros: List<Pair<String, String>>): String =
        base + "?" + parametros.joinToString("&") { "${it.first}=${it.second.urlEncode()}" }

    /** Una página de especies que cumplen el filtro, ordenadas por nombre científico. */
    suspend fun listar(filtro: Filtro, desplazamiento: Int, limite: Int = PAGINA): List<Especie> {
        val params = mutableListOf(
            "\$order" to "nombre_cientifico",
            "\$limit" to limite.toString(),
            "\$offset" to desplazamiento.toString(),
        )
        condiciones(filtro).takeIf { it.isNotEmpty() }?.let { params += "\$where" to it }
        val json = JSONArray(httpGet(url(params)))
        return List(json.length()) { Especie.desdeJson(json.getJSONObject(it)) }
    }

    /** Número total de especies que cumplen el filtro. */
    suspend fun contar(filtro: Filtro): Int {
        val params = mutableListOf("\$select" to "count(*)")
        condiciones(filtro).takeIf { it.isNotEmpty() }?.let { params += "\$where" to it }
        val json = JSONArray(httpGet(url(params)))
        return json.getJSONObject(0).optString("count", "0").toIntOrNull() ?: 0
    }

    /** Valores distintos de un nivel taxonómico con su número de especies, de mayor a menor. */
    suspend fun grupos(nivel: Nivel, filtro: Filtro): List<Grupo> {
        val params = mutableListOf(
            "\$select" to "${nivel.campo},count(*)",
            "\$group" to nivel.campo,
            "\$order" to "count desc",
            "\$limit" to "500",
        )
        condiciones(filtro).takeIf { it.isNotEmpty() }?.let { params += "\$where" to it }
        val json = JSONArray(httpGet(url(params)))
        return List(json.length()) {
            val o = json.getJSONObject(it)
            Grupo(o.optString(nivel.campo, "Sin dato").ifBlank { "Sin dato" }, o.optString("count", "0").toIntOrNull() ?: 0)
        }.filter { it.nombre != "NA" && it.nombre != "Sin dato" }
    }

    /** Una especie al azar del listado (para el juego). */
    suspend fun aleatoria(total: Int): Especie? {
        if (total <= 0) return null
        val json = JSONArray(
            httpGet(
                url(
                    listOf(
                        "\$order" to "nombre_cientifico",
                        "\$limit" to "1",
                        "\$offset" to (0 until total).random().toString(),
                    )
                )
            )
        )
        return if (json.length() == 0) null else Especie.desdeJson(json.getJSONObject(0))
    }

    companion object {
        const val PAGINA = 40
    }
}
