package com.example.reto_8_roho_a.data

/** Esquema de la base SQLite: nombre de la tabla, columnas y sentencias SQL de creación. */
object CompanyContract {
    const val DATABASE_NAME = "directorio_empresas.db"
    const val DATABASE_VERSION = 1

    const val TABLE_COMPANIES = "companies"

    const val COLUMN_ID = "_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_URL = "url"
    const val COLUMN_PHONE = "phone"
    const val COLUMN_EMAIL = "email"
    const val COLUMN_PRODUCTS_SERVICES = "products_services"
    const val COLUMN_IS_CONSULTORIA = "is_consultoria"
    const val COLUMN_IS_DESARROLLO_MEDIDA = "is_desarrollo_medida"
    const val COLUMN_IS_FABRICA_SOFTWARE = "is_fabrica_software"

    const val SQL_CREATE_COMPANIES = """
        CREATE TABLE $TABLE_COMPANIES (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_NAME TEXT NOT NULL,
            $COLUMN_URL TEXT NOT NULL DEFAULT '',
            $COLUMN_PHONE TEXT NOT NULL DEFAULT '',
            $COLUMN_EMAIL TEXT NOT NULL DEFAULT '',
            $COLUMN_PRODUCTS_SERVICES TEXT NOT NULL DEFAULT '',
            $COLUMN_IS_CONSULTORIA INTEGER NOT NULL DEFAULT 0,
            $COLUMN_IS_DESARROLLO_MEDIDA INTEGER NOT NULL DEFAULT 0,
            $COLUMN_IS_FABRICA_SOFTWARE INTEGER NOT NULL DEFAULT 0
        )
    """

    const val SQL_DROP_COMPANIES = "DROP TABLE IF EXISTS $TABLE_COMPANIES"
}
