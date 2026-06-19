package com.dallariva.carburanti.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.dallariva.carburanti.data.DistributoreConPrezzo
import kotlinx.coroutines.launch

/** Posizione di fallback (centro di Trento) quando la posizione reale non e' disponibile. */
private val DEFAULT_LOCATION = 46.0664 to 11.1257

/**
 * Schermata Android Auto: lista dei distributori piu' economici vicini su [PlaceListMapTemplate].
 *
 * Niente UI custom ne' grafici (vincoli categoria POI, spec §5). Carica i dati in modo asincrono
 * dal [FuelRepository][com.dallariva.carburanti.data.FuelRepository] di :data e ridisegna via
 * [invalidate]. Il refresh (OnContentRefreshListener) ricarica.
 */
class NearbyFuelScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = CarRepositoryProvider.repository

    // null = non ancora caricato; lista (anche vuota) = caricamento completato.
    private var stazioni: List<DistributoreConPrezzo>? = null
    private var loading = false

    override fun onGetTemplate(): Template {
        val data = stazioni
        if (data == null) {
            if (!loading) {
                loading = true
                lifecycleScope.launch {
                    stazioni = load()
                    loading = false
                    invalidate()
                }
            }
            return PlaceListMapTemplate.Builder()
                .setTitle("Carburante vicino")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)

        val listBuilder = ItemList.Builder()
        if (data.isEmpty()) {
            listBuilder.addItem(
                Row.Builder().setTitle("Nessun distributore nei dintorni").build()
            )
        }
        data.take(limit).forEach { s ->
            val place = Place.Builder(CarLocation.create(s.distributore.lat, s.distributore.lon))
                .setMarker(PlaceMarker.Builder().build())
                .build()
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${s.distributore.nome} — ${"%.3f".format(s.prezzo.prezzo)} €")
                    .addText("${s.distributore.comune} • ${"%.1f".format(s.distanzaKm)} km")
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .build()
            )
        }

        val template = PlaceListMapTemplate.Builder()
            .setTitle("Carburante vicino")
            .setHeaderAction(Action.APP_ICON)
            .setItemList(listBuilder.build())
            .setOnContentRefreshListener {
                stazioni = null
                invalidate()
            }

        // Il pallino "dove sono io" richiede il permesso posizione: evita crash se assente.
        if (hasLocationPermission(carContext)) {
            template.setCurrentLocationEnabled(true)
        }

        return template.build()
    }

    /** Carica i distributori piu' economici: default GPL (in Italia servito), 10 km. */
    private suspend fun load(): List<DistributoreConPrezzo> {
        val (lat, lon) = lastKnownLocation(carContext) ?: DEFAULT_LOCATION
        return repository.cheapestNearby(
            lat = lat,
            lon = lon,
            raggioKm = 10.0,
            carburante = "GPL",
            self = false,
        )
    }
}
