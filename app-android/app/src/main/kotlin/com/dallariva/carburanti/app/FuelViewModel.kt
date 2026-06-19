package com.dallariva.carburanti.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallariva.carburanti.data.DistributoreConPrezzo
import com.dallariva.carburanti.data.Prezzo
import kotlinx.coroutines.launch

/** Coordinate di fallback quando la posizione reale non e' disponibile (centro di Trento). */
val DEFAULT_LOCATION = 46.0664 to 11.1257

/** Carburanti selezionabili nella UI (sottostringa passata al repository con LIKE %x%). */
val FUEL_OPTIONS = listOf("GPL", "Benzina", "Gasolio", "Metano")

/** Carburanti che in Italia esistono solo self-service: per questi forziamo self=true. */
val SOLO_SELF = setOf("GPL", "Metano")

/** Criterio di ordinamento della lista/mappa. */
enum class SortBy { PREZZO, DISTANZA }

/** Stato della schermata principale (lista + mappa). */
data class HomeUiState(
    val carburante: String = "GPL",
    val self: Boolean = true,
    val raggioKm: Double = 10.0,
    val sortBy: SortBy = SortBy.PREZZO,
    val lat: Double? = null,
    val lon: Double? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val stazioni: List<DistributoreConPrezzo> = emptyList(),
)

/** Stato della schermata di dettaglio (serie storica di un impianto). */
data class HistoryUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val prezzi: List<Prezzo> = emptyList(),
)

/**
 * ViewModel condiviso tra lista, mappa e dettaglio. Tutto l'accesso ai dati passa dal
 * [RepositoryProvider]: la UI non parla mai direttamente con Turso.
 */
class FuelViewModel : ViewModel() {

    private val repo = RepositoryProvider.repository

    var uiState by mutableStateOf(HomeUiState())
        private set

    var historyState by mutableStateOf(HistoryUiState())
        private set

    /**
     * Cambia carburante/modalita e ricarica con l'ultima posizione nota, se presente.
     * Per i carburanti solo-self (GPL, Metano) la modalita viene forzata a self.
     */
    fun selectFuel(carburante: String, self: Boolean) {
        val effectiveSelf = if (carburante in SOLO_SELF) true else self
        uiState = uiState.copy(carburante = carburante, self = effectiveSelf)
        val la = uiState.lat
        val lo = uiState.lon
        if (la != null && lo != null) refresh(la, lo)
    }

    /** Cambia il criterio di ordinamento riordinando la lista gia' caricata (niente refetch). */
    fun setSort(sort: SortBy) {
        uiState = uiState.copy(sortBy = sort, stazioni = sortList(uiState.stazioni, sort))
    }

    private fun sortList(list: List<DistributoreConPrezzo>, sort: SortBy) = when (sort) {
        SortBy.PREZZO -> list.sortedBy { it.prezzo.prezzo }
        SortBy.DISTANZA -> list.sortedBy { it.distanzaKm }
    }

    /** Carica i distributori piu' economici intorno a ([lat], [lon]). */
    fun refresh(lat: Double, lon: Double) {
        uiState = uiState.copy(lat = lat, lon = lon, loading = true, error = null)
        viewModelScope.launch {
            try {
                val list = repo.cheapestNearby(
                    lat = lat,
                    lon = lon,
                    raggioKm = uiState.raggioKm,
                    carburante = uiState.carburante,
                    self = uiState.self,
                )
                uiState = uiState.copy(loading = false, stazioni = sortList(list, uiState.sortBy))
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "Errore di rete")
            }
        }
    }

    /** Distributore gia' caricato in lista/mappa, per popolare l'header del dettaglio. */
    fun stazione(id: Long): DistributoreConPrezzo? =
        uiState.stazioni.firstOrNull { it.distributore.id == id }

    /** Carica la serie storica per un impianto+carburante (schermata dettaglio). */
    fun loadHistory(impiantoId: Long, carburante: String, self: Boolean) {
        historyState = HistoryUiState(loading = true)
        viewModelScope.launch {
            try {
                val h = repo.history(impiantoId, carburante, self)
                historyState = HistoryUiState(prezzi = h)
            } catch (e: Exception) {
                historyState = HistoryUiState(error = e.message ?: "Errore di rete")
            }
        }
    }
}
