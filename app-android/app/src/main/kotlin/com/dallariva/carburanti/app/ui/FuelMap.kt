package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dallariva.carburanti.app.BuildConfig
import com.dallariva.carburanti.data.DistributoreConPrezzo
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import kotlin.coroutines.resume

/**
 * Mappa MapLibre con un cerchio per distributore, colorato in base al prezzo
 * (verde = economico, rosso = caro). Tap su un cerchio -> dettaglio impianto.
 */
@Composable
fun FuelMap(
    stazioni: List<DistributoreConPrezzo>,
    center: Pair<Double, Double>?,
    onStationClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapView e' una View Android: la creiamo una volta e ne gestiamo il ciclo di vita.
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val circleManagerRef = remember { mutableStateOf<CircleManager?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            circleManagerRef.value?.onDestroy()
            mapView.onDestroy()
        }
    }

    // Inizializza mappa + stile una volta sola.
    LaunchedEffect(mapView) {
        val map = mapView.awaitMap()
        mapRef.value = map
        map.setStyle(Style.Builder().fromUri(BuildConfig.MAP_STYLE_URL)) { style ->
            styleRef.value = style
        }
    }

    // (Ri)disegna i marker quando cambiano i dati, lo stile o il centro.
    LaunchedEffect(styleRef.value, stazioni, center) {
        val map = mapRef.value ?: return@LaunchedEffect
        val style = styleRef.value ?: return@LaunchedEffect

        circleManagerRef.value?.let { it.deleteAll(); it.onDestroy() }
        val cm = CircleManager(mapView, map, style)

        val prezzi = stazioni.map { it.prezzo.prezzo }
        val min = prezzi.minOrNull() ?: 0.0
        val max = prezzi.maxOrNull() ?: 0.0

        stazioni.forEach { s ->
            cm.create(
                CircleOptions()
                    .withLatLng(LatLng(s.distributore.lat, s.distributore.lon))
                    .withCircleColor(priceColorHex(s.prezzo.prezzo, min, max))
                    .withCircleRadius(9f)
                    .withCircleStrokeColor("#FFFFFF")
                    .withCircleStrokeWidth(1.5f)
                    .withData(JsonPrimitive(s.distributore.id))
            )
        }
        cm.addClickListener { circle ->
            val id = (circle.data as? JsonPrimitive)?.asLong ?: return@addClickListener false
            onStationClick(id)
            true
        }
        circleManagerRef.value = cm

        center?.let { (lat, lon) ->
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 11.0))
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/** Adatta il callback `getMapAsync` di MapLibre a una funzione suspend. */
private suspend fun MapView.awaitMap(): MapLibreMap =
    suspendCancellableCoroutine { cont ->
        getMapAsync { map -> cont.resume(map) }
    }
