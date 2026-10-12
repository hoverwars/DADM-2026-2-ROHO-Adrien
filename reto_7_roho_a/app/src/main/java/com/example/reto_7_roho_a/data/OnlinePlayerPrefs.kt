package com.example.reto_7_roho_a.data

import android.content.Context
import androidx.core.content.edit
import java.util.UUID
import kotlin.random.Random

private const val PREFS_NAME = "online_prefs"
private const val KEY_PLAYER_ID = "player_id"
private const val KEY_PLAYER_NAME = "player_name"

const val MAX_PLAYER_NAME_LENGTH = 20

/** Identité locale du joueur en ligne (sans compte : un identifiant aléatoire par installation). */
class OnlinePlayerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val playerId: String
        get() = prefs.getString(KEY_PLAYER_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_PLAYER_ID, it) }
        }

    var playerName: String
        get() = prefs.getString(KEY_PLAYER_NAME, null) ?: "Jugador ${Random.nextInt(100, 1000)}".also {
            prefs.edit { putString(KEY_PLAYER_NAME, it) }
        }
        set(value) = prefs.edit { putString(KEY_PLAYER_NAME, value.trim().take(MAX_PLAYER_NAME_LENGTH)) }
}
