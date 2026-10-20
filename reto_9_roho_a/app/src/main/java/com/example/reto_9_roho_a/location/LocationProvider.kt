package com.example.reto_9_roho_a.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Obtiene la posición actual con el proveedor fusionado de Google Play Services. */
class LocationProvider(context: Context) {
    private val appContext = context.applicationContext

    fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Devuelve (lat, lon) o null si no hay posición. Requiere el permiso de ubicación concedido. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val tokenSource = CancellationTokenSource()
            cont.invokeOnCancellation { tokenSource.cancel() }
            LocationServices.getFusedLocationProviderClient(appContext)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { location ->
                    cont.resume(location?.let { it.latitude to it.longitude })
                }
                .addOnFailureListener { cont.resume(null) }
        }
}
