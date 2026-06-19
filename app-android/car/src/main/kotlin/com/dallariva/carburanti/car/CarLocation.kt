package com.dallariva.carburanti.car

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** True se almeno un permesso di posizione (fine o coarse) e' concesso. */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/**
 * Ultima posizione nota via FusedLocationProvider, o null se non disponibile / permesso assente.
 * Analogo dell'helper di :app (non riusabile perche' in un altro modulo Android).
 */
suspend fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        try {
            client.lastLocation
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) loc.latitude to loc.longitude else null)
                }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: SecurityException) {
            cont.resume(null)
        }
    }
}
