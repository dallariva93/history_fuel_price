package com.dallariva.carburanti.data

/**
 * Interfaccia pubblica per leggere dati carburante.
 *
 * Una sola interfaccia, due implementazioni intercambiabili:
 * - [HttpFuelRepository]: legge da Turso via HTTP /v2/pipeline (fallback / desktop CLI / iniziale).
 * - LibsqlFuelRepository (futuro): embedded replica via tech.turso.libsql, solo Android.
 *
 * Le funzioni sono `suspend` per essere usabili da coroutine (Android) e dal CLI via runBlocking.
 */
interface FuelRepository {

    /**
     * Distributori entro [raggioKm] dalle coordinate ([lat], [lon]) con l'ultimo prezzo
     * noto per il [carburante] e la modalita [self], ordinati per prezzo crescente.
     *
     * @param carburante substring del nome carburante, es. "GPL", "Benzina" (LIKE %x%).
     * @param self true = self service, false = servito.
     */
    suspend fun cheapestNearby(
        lat: Double,
        lon: Double,
        raggioKm: Double,
        carburante: String,
        self: Boolean = true,
    ): List<DistributoreConPrezzo>

    /**
     * Serie storica dei prezzi per un dato [impiantoId] e [carburante]/[self], in ordine
     * cronologico crescente.
     */
    suspend fun history(
        impiantoId: Long,
        carburante: String,
        self: Boolean = true,
    ): List<Prezzo>
}
