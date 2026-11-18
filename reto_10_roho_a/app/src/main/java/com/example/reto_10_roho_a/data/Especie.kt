package com.example.reto_10_roho_a.data

import androidx.compose.ui.graphics.Color
import com.example.reto_10_roho_a.ui.theme.CriticoColor
import com.example.reto_10_roho_a.ui.theme.PeligroColor
import com.example.reto_10_roho_a.ui.theme.VulnerableColor
import org.json.JSONObject

/** Categorías de amenaza del listado oficial (UICN). */
enum class Categoria(val codigo: String, val etiqueta: String, val color: Color) {
    CR("CR", "En peligro crítico", CriticoColor),
    EN("EN", "En peligro", PeligroColor),
    VU("VU", "Vulnerable", VulnerableColor);

    companion object {
        fun desde(codigo: String): Categoria? = entries.firstOrNull { it.codigo == codigo }
    }
}

/** Una especie del listado oficial de especies silvestres amenazadas (dataset wizt-zh64). */
data class Especie(
    val gbifKey: Long?,
    val nombreCientifico: String,
    val autor: String,
    val nombreVernaculo: String?,
    val reino: String,
    val filo: String,
    val clase: String,
    val orden: String,
    val familia: String,
    val genero: String,
    val rango: String,
    val estadoTaxonomico: String,
    val observaciones: String?,
    val categoria: Categoria?,
) {
    /** Nombre principal para mostrar: el vernáculo si existe, si no el científico. */
    val titulo: String get() = vernaculos.firstOrNull() ?: nombreCientifico

    val vernaculos: List<String>
        get() = nombreVernaculo?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    companion object {
        /** Convierte un objeto JSON del servicio en una [Especie] (usa org.json del SDK de Android). */
        fun desdeJson(json: JSONObject): Especie {
            fun texto(campo: String): String? =
                json.optString(campo, "").trim().takeIf { it.isNotEmpty() && it != "NA" }

            return Especie(
                gbifKey = texto("identificacion")?.substringAfterLast('/')?.toLongOrNull(),
                nombreCientifico = texto("nombre_cientifico") ?: "Sin nombre",
                autor = texto("nombre_cientifico_1") ?: "",
                nombreVernaculo = texto("nombre_vernaculo"),
                reino = texto("reino") ?: "",
                filo = texto("filo") ?: "",
                clase = texto("clase") ?: "",
                orden = texto("orden") ?: "",
                familia = texto("familia") ?: "",
                genero = texto("genero") ?: "",
                rango = texto("rango_de_taxon") ?: "",
                estadoTaxonomico = texto("estado_taxonomico") ?: "",
                observaciones = texto("taxon_observaciones"),
                categoria = texto("estado_de_amenaza")?.let { Categoria.desde(it) },
            )
        }
    }
}

/** Niveles de la jerarquía taxonómica que se pueden explorar, de mayor a menor. */
enum class Nivel(val campo: String, val etiqueta: String, val plural: String) {
    REINO("reino", "Reino", "Reinos"),
    CLASE("clase", "Clase", "Clases"),
    ORDEN("orden", "Orden", "Órdenes"),
    FAMILIA("familia", "Familia", "Familias");

    fun siguiente(): Nivel? = entries.getOrNull(ordinal + 1)
}

/** Un valor de un nivel taxonómico con el número de especies que contiene. */
data class Grupo(val nombre: String, val total: Int)

/** Nombres en español de los reinos y clases más comunes. */
object Nombres {
    private val reinos = mapOf("Animalia" to "Animales", "Plantae" to "Plantas", "Fungi" to "Hongos")
    private val clases = mapOf(
        "Magnoliopsida" to "Plantas con flor", "Liliopsida" to "Plantas con flor (monocotiledóneas)",
        "Amphibia" to "Anfibios", "Aves" to "Aves", "Actinopterygii" to "Peces óseos",
        "Mammalia" to "Mamíferos", "Polypodiopsida" to "Helechos", "Lecanoromycetes" to "Líquenes",
        "Reptilia" to "Reptiles", "Malacostraca" to "Crustáceos", "Insecta" to "Insectos",
        "Agaricomycetes" to "Hongos con sombrero", "Jungermanniopsida" to "Hepáticas",
        "Elasmobranchii" to "Tiburones y rayas", "Sphagnopsida" to "Musgos de turbera",
        "Pinopsida" to "Coníferas", "Bryopsida" to "Musgos", "Anthozoa" to "Corales y anémonas",
        "Arachnida" to "Arácnidos", "Bivalvia" to "Bivalvos", "Gastropoda" to "Caracoles",
        "Holothuroidea" to "Pepinos de mar", "Ophiuroidea" to "Estrellas frágiles",
        "Marchantiopsida" to "Hepáticas", "Arthoniomycetes" to "Líquenes", "Dothideomycetes" to "Hongos",
    )

    fun reino(nombre: String) = reinos[nombre]?.let { "$it · $nombre" } ?: nombre
    fun clase(nombre: String) = clases[nombre]?.let { "$it · $nombre" } ?: nombre

    /** Etiqueta amigable de un valor según su nivel taxonómico. */
    fun de(nivel: Nivel, nombre: String) = when (nivel) {
        Nivel.REINO -> reino(nombre)
        Nivel.CLASE -> clase(nombre)
        else -> nombre
    }
}
