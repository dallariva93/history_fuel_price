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
    /**
     * Data ISO (`YYYY-MM-DD`) dell'ultimo run della pipeline in cui l'impianto era presente nel
     * registro MIMIT degli impianti attivi. Se smette di avanzare, l'impianto e' uscito dal
     * registro (possibile chiusura). Nullable: puo' mancare per dati vecchi.
     */
    val updated: String? = null,
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
 * Stato di disponibilita' inferito dell'impianto.
 *
 * Euristica, non garanzia: MIMIT non espone uno stato esplicito. [NON_AGGIORNATO] indica che
 * l'impianto non compare nel registro attivo da almeno [STALE_DAYS] giorni (possibile chiusura).
 */
enum class StatoImpianto { ATTIVO, NON_AGGIORNATO }

/**
 * Composizione: distributore + ultimo prezzo noto + distanza in km dal punto di query.
 *
 * [giorniSenzaAggiornamento] e [stato] sono calcolati dal repository confrontando
 * `Distributore.updated` con l'ultimo run noto del dataset. Restituito da
 * [FuelRepository.cheapestNearby].
 */
data class DistributoreConPrezzo(
    val distributore: Distributore,
    val prezzo: Prezzo,
    val distanzaKm: Double,
    val giorniSenzaAggiornamento: Int? = null,
    val stato: StatoImpianto = StatoImpianto.ATTIVO,
)
