package com.dallariva.carburanti.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Servizio Android Auto (categoria POI). Punto di ingresso della superficie auto: l'host crea una
 * [Session] che apre la [NearbyFuelScreen].
 */
class FuelCarAppService : CarAppService() {

    // Uso personale: accetta qualunque host. In produzione andrebbe ristretto (spec §5).
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = NearbyFuelScreen(carContext)
    }
}
