package com.example.reto_9_roho_a.data

import android.content.Context
import androidx.core.content.edit

/** Preferencias de la app (SharedPreferences): radio de búsqueda y categorías activas. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var radiusKm: Int
        get() = prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS_KM).coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
        set(value) = prefs.edit { putInt(KEY_RADIUS, value.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)) }

    var categories: Set<PoiCategory>
        get() {
            val saved = prefs.getStringSet(KEY_CATEGORIES, null)
                ?: return PoiCategory.entries.toSet()
            return saved.mapNotNull { PoiCategory.fromKey(it) }.toSet()
        }
        set(value) = prefs.edit { putStringSet(KEY_CATEGORIES, value.map { it.name }.toSet()) }

    companion object {
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 50
        const val DEFAULT_RADIUS_KM = 5
        private const val PREFS_NAME = "reto9_settings"
        private const val KEY_RADIUS = "radius_km"
        private const val KEY_CATEGORIES = "categories"
    }
}
