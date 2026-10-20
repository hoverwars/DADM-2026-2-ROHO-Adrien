package com.example.reto_8_roho_a.data

import android.content.Context
import android.database.Cursor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "CompanyRepository"

/**
 * Operaciones CRUD sobre la tabla de empresas. Todas las funciones son `suspend`
 * y se ejecutan en [Dispatchers.IO] para no bloquear el hilo de la interfaz.
 */
class CompanyRepository(context: Context) {
    private val dbHelper = CompanyDbHelper.getInstance(context)

    /** CREATE: inserta la empresa y devuelve su nuevo id (o -1 si falló). */
    suspend fun insert(company: Company): Long = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase
            .insert(CompanyContract.TABLE_COMPANIES, null, company.toContentValues())
            .also { Log.i(TAG, "Empresa insertada: id=$it, nombre=${company.name}") }
    }

    /** RETRIEVE: una empresa por su id, o null si ya no existe. */
    suspend fun getById(id: Long): Company? = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.query(
            CompanyContract.TABLE_COMPANIES,
            null,
            "${CompanyContract.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toCompany() else null }
    }

    /**
     * RETRIEVE + filtros: lista las empresas ordenadas por nombre.
     * - [nameQuery] vacío = sin filtro; si no, el nombre debe contener el texto (sin distinguir mayúsculas).
     * - [classifications] vacío = sin filtro; si no, la empresa debe tener AL MENOS UNA de ellas.
     */
    suspend fun search(
        nameQuery: String = "",
        classifications: Set<Classification> = emptySet(),
    ): List<Company> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        val query = nameQuery.trim()
        if (query.isNotEmpty()) {
            conditions += "${CompanyContract.COLUMN_NAME} LIKE ? ESCAPE '\\'"
            args += "%${escapeLike(query)}%"
        }
        if (classifications.isNotEmpty()) {
            conditions += classifications.joinToString(" OR ", "(", ")") { "${it.column} = 1" }
        }

        dbHelper.readableDatabase.query(
            CompanyContract.TABLE_COMPANIES,
            null,
            conditions.joinToString(" AND ").ifEmpty { null },
            args.toTypedArray().ifEmpty { null },
            null, null,
            "${CompanyContract.COLUMN_NAME} COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toCompany()) }
        }
    }

    /** Total de empresas (clave null) y número de empresas por clasificación, para la pantalla de inicio. */
    suspend fun countByClassification(): Map<Classification?, Int> = withContext(Dispatchers.IO) {
        val sums = Classification.entries.joinToString(", ") { "IFNULL(SUM(${it.column}), 0)" }
        dbHelper.readableDatabase
            .rawQuery("SELECT COUNT(*), $sums FROM ${CompanyContract.TABLE_COMPANIES}", null)
            .use { cursor ->
                cursor.moveToFirst()
                buildMap {
                    put(null, cursor.getInt(0))
                    Classification.entries.forEachIndexed { i, c -> put(c, cursor.getInt(i + 1)) }
                }
            }
    }

    /** UPDATE: reemplaza los datos de la empresa con el mismo id. Devuelve true si se modificó. */
    suspend fun update(company: Company): Boolean = withContext(Dispatchers.IO) {
        val rows = dbHelper.writableDatabase.update(
            CompanyContract.TABLE_COMPANIES,
            company.toContentValues(),
            "${CompanyContract.COLUMN_ID} = ?",
            arrayOf(company.id.toString())
        )
        Log.i(TAG, "Empresa actualizada: id=${company.id}, filas=$rows")
        rows > 0
    }

    /** DELETE: borra la empresa. Devuelve true si existía. */
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rows = dbHelper.writableDatabase.delete(
            CompanyContract.TABLE_COMPANIES,
            "${CompanyContract.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )
        Log.i(TAG, "Empresa eliminada: id=$id, filas=$rows")
        rows > 0
    }

    /** Escapa los comodines de LIKE (% y _) para que se busquen literalmente. */
    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun Cursor.toCompany(): Company = Company(
        id = getLong(getColumnIndexOrThrow(CompanyContract.COLUMN_ID)),
        name = getString(getColumnIndexOrThrow(CompanyContract.COLUMN_NAME)),
        url = getString(getColumnIndexOrThrow(CompanyContract.COLUMN_URL)),
        phone = getString(getColumnIndexOrThrow(CompanyContract.COLUMN_PHONE)),
        email = getString(getColumnIndexOrThrow(CompanyContract.COLUMN_EMAIL)),
        productsServices = getString(getColumnIndexOrThrow(CompanyContract.COLUMN_PRODUCTS_SERVICES)),
        classifications = Classification.entries
            .filter { getInt(getColumnIndexOrThrow(it.column)) == 1 }
            .toSet(),
    )
}
