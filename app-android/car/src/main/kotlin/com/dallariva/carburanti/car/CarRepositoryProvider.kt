package com.dallariva.carburanti.car

import com.dallariva.carburanti.data.FuelRepository
import com.dallariva.carburanti.data.HttpFuelRepository

/**
 * Accesso al [FuelRepository] di :data per la superficie Android Auto.
 *
 * Specchia [com.dallariva.carburanti.app.RepositoryProvider] del telefono: e' solo glue di
 * configurazione (URL + token di SOLA LETTURA da BuildConfig), non duplica il data layer, che
 * resta unico in :data.
 */
object CarRepositoryProvider {
    val repository: FuelRepository by lazy {
        HttpFuelRepository(
            databaseUrl = BuildConfig.TURSO_DATABASE_URL,
            authToken = BuildConfig.TURSO_RO_TOKEN,
        )
    }
}
