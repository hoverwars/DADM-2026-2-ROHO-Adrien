package com.example.reto_8_roho_a.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri

private const val TAG = "ContactIntents"

/** Esquema explícito al inicio de la URL ("http://", "ftp://", "HTTPS://"...). */
private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/** Abre la página web en el navegador (agrega https:// si el usuario no escribió el esquema). */
fun openWebsite(context: Context, url: String) {
    val fullUrl = if (SCHEME_REGEX.containsMatchIn(url)) url else "https://$url"
    // normalizeScheme: "HTTPS://..." -> "https://..." (los intent-filter de los navegadores distinguen mayúsculas)
    launch(context, Intent(Intent.ACTION_VIEW, fullUrl.toUri().normalizeScheme()), "No hay navegador disponible")
}

/** Abre el marcador telefónico con el número ya escrito (no llama directamente: no requiere permiso). */
fun dialPhone(context: Context, phone: String) {
    launch(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)), "No hay app de teléfono disponible")
}

/** Abre la app de correo con el destinatario ya escrito. */
fun sendEmail(context: Context, email: String) {
    launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null)), "No hay app de correo disponible")
}

private fun launch(context: Context, intent: Intent, errorMessage: String) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Ninguna app puede abrir $intent", e)
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}
