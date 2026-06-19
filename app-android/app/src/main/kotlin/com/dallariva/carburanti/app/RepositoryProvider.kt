package com.dallariva.carburanti.app

import com.dallariva.carburanti.data.FuelRepository
import com.dallariva.carburanti.data.HttpFuelRepository

/**
 * Punto unico di accesso al [FuelRepository] del modulo :data.
 *
 * Costruisce l'implementazione HTTP (opzione B) usando URL e token di SOLA LETTURA letti da
 * [BuildConfig] (alimentato da local.properties, non versionato). Quando l'embedded replica
 * (opzione A) sara' pronta, basta cambiare questa factory: l'interfaccia non cambia.
 */
object RepositoryProvider {
    val repository: FuelRepository by lazy {
        HttpFuelRepository(
            databaseUrl = BuildConfig.TURSO_DATABASE_URL,
            authToken = BuildConfig.TURSO_RO_TOKEN,
        )
    }
}
