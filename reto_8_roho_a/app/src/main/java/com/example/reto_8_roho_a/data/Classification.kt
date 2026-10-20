package com.example.reto_8_roho_a.data

/**
 * Clasificación de una empresa. Una empresa puede tener varias a la vez
 * ("consultoría, desarrollo a la medida y/o fábrica de software").
 *
 * [column] es el nombre de la columna booleana (INTEGER 0/1) que la representa en SQLite.
 */
enum class Classification(val label: String, val column: String) {
    CONSULTORIA("Consultoría", CompanyContract.COLUMN_IS_CONSULTORIA),
    DESARROLLO_MEDIDA("Desarrollo a la medida", CompanyContract.COLUMN_IS_DESARROLLO_MEDIDA),
    FABRICA_SOFTWARE("Fábrica de software", CompanyContract.COLUMN_IS_FABRICA_SOFTWARE),
}
