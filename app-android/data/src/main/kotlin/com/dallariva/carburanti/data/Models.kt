package com.dallariva.carburanti.data

/**
 * Distributore di carburante (impianto).
 *
 * Specchia la tabella `impianti` su Turso. Le coordinate sono sempre presenti per
 * gli impianti locali raccolti dal Pi.
 */
data class Distributore(
    val id: Long,
    val nome: String,
    val gestore: String?,
    val bandiera: String?,
    val tipo: String?,
    val indirizzo: String?,
    val comune: String,
    val provincia: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Singolo evento prezzo per un (impianto, carburante, modalita self).
 *
 * `dtComu` e' la timestamp di comunicazione MIMIT, in formato ISO 8601.
 */
data class Prezzo(
    val carburante: String,
    val self: Boolean,
    val prezzo: Double,
    val dtComu: String,
)

/**
 * Composizione: distributore + ultimo prezzo noto + distanza in km dal punto di query.
 *
 * Restituito da [FuelRepository.cheapestNearby].
 */
data class DistributoreConPrezzo(
    val distributore: Distributore,
    val prezzo: Prezzo,
    val distanzaKm: Double,
)
