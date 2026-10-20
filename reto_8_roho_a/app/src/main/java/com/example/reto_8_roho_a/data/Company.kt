package com.example.reto_8_roho_a.data

/** Una empresa del directorio. [id] vale [NO_ID] mientras no se haya guardado en la base. */
data class Company(
    val id: Long = NO_ID,
    val name: String,
    val url: String,
    val phone: String,
    val email: String,
    val productsServices: String,
    val classifications: Set<Classification>,
) {
    companion object {
        const val NO_ID = -1L
    }
}
