package com.example.reto_8_roho_a.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

private const val TAG = "CompanyDbHelper"

/**
 * Crea y versiona la base SQLite (patrón clásico de SQLiteOpenHelper, como en el tutorial del reto).
 * Se usa una sola instancia para toda la app (ver [getInstance]).
 */
class CompanyDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, CompanyContract.DATABASE_NAME, null, CompanyContract.DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        Log.i(TAG, "Creando la base de datos")
        db.execSQL(CompanyContract.SQL_CREATE_COMPANIES)
        insertSampleData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Aún no hay migraciones: se recrea la tabla.
        Log.w(TAG, "Actualizando la base de la versión $oldVersion a $newVersion")
        db.execSQL(CompanyContract.SQL_DROP_COMPANIES)
        onCreate(db)
    }

    /** Empresas ficticias insertadas solo la primera vez, para tener datos con qué probar la app. */
    private fun insertSampleData(db: SQLiteDatabase) {
        SAMPLE_COMPANIES.forEach { company ->
            db.insert(CompanyContract.TABLE_COMPANIES, null, company.toContentValues())
        }
        Log.i(TAG, "${SAMPLE_COMPANIES.size} empresas de ejemplo insertadas")
    }

    companion object {
        @Volatile
        private var instance: CompanyDbHelper? = null

        fun getInstance(context: Context): CompanyDbHelper =
            instance ?: synchronized(this) {
                instance ?: CompanyDbHelper(context.applicationContext).also { instance = it }
            }

        private val SAMPLE_COMPANIES = listOf(
            Company(
                name = "Andes Software Factory",
                url = "https://www.andessoftware.example",
                phone = "+57 601 555 0101",
                email = "contacto@andessoftware.example",
                productsServices = "Desarrollo de aplicaciones web y móviles a gran escala, QA y DevOps.",
                classifications = setOf(Classification.FABRICA_SOFTWARE, Classification.DESARROLLO_MEDIDA),
            ),
            Company(
                name = "Cóndor Consultores TI",
                url = "https://www.condorti.example",
                phone = "+57 604 555 0202",
                email = "info@condorti.example",
                productsServices = "Arquitectura empresarial, transformación digital y auditoría de software.",
                classifications = setOf(Classification.CONSULTORIA),
            ),
            Company(
                name = "Pixel Caribe",
                url = "https://www.pixelcaribe.example",
                phone = "+57 605 555 0303",
                email = "hola@pixelcaribe.example",
                productsServices = "Apps móviles a la medida, diseño UX/UI y prototipado.",
                classifications = setOf(Classification.DESARROLLO_MEDIDA),
            ),
            Company(
                name = "Nube Andina",
                url = "https://www.nubeandina.example",
                phone = "+57 601 555 0404",
                email = "ventas@nubeandina.example",
                productsServices = "Migración a la nube, consultoría en seguridad y desarrollo de microservicios.",
                classifications = setOf(Classification.CONSULTORIA, Classification.DESARROLLO_MEDIDA),
            ),
            Company(
                name = "Código Café",
                url = "https://www.codigocafe.example",
                phone = "+57 602 555 0505",
                email = "equipo@codigocafe.example",
                productsServices = "ERP para el sector agrícola, integración de sistemas y soporte.",
                classifications = setOf(
                    Classification.CONSULTORIA,
                    Classification.DESARROLLO_MEDIDA,
                    Classification.FABRICA_SOFTWARE,
                ),
            ),
        )
    }
}

/** Convierte una empresa en los valores de una fila (sin el id, que lo asigna SQLite). */
internal fun Company.toContentValues(): ContentValues = ContentValues().apply {
    put(CompanyContract.COLUMN_NAME, name)
    put(CompanyContract.COLUMN_URL, url)
    put(CompanyContract.COLUMN_PHONE, phone)
    put(CompanyContract.COLUMN_EMAIL, email)
    put(CompanyContract.COLUMN_PRODUCTS_SERVICES, productsServices)
    Classification.entries.forEach { put(it.column, if (it in classifications) 1 else 0) }
}
