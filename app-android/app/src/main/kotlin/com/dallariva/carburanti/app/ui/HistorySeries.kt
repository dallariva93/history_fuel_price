package com.dallariva.carburanti.app.ui

import com.dallariva.carburanti.data.Prezzo
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Punto giornaliero della serie storica visualizzata nel dettaglio.
 *
 * A differenza di [Prezzo], che modella un evento di variazione reale comunicato dal gestore,
 * [PricePoint] e' un campione sintetico (uno per giorno) generato via forward-fill: il prezzo
 * dell'ultimo evento noto resta valido fino all'evento successivo.
 */
data class PricePoint(
    val data: LocalDate,
    val prezzo: Double,
)

/**
 * Genera la serie giornaliera degli ultimi [days] giorni terminanti in [today] applicando
 * forward-fill agli eventi [events].
 *
 * Per ogni giorno della finestra il prezzo e' quello dell'ultimo evento con `dt_comu <= giorno`.
 * I giorni precedenti al primo evento utile vengono omessi (non si inventano dati senza ancora).
 *
 * Gli eventi con `dtComu` non parsabile come ISO date vengono ignorati: la pipeline di ingest
 * scrive sempre ISO 8601, quindi e' un puro fallback difensivo.
 */
fun forwardFill(
    events: List<Prezzo>,
    days: Int,
    today: LocalDate,
): List<PricePoint> {
    require(days > 0) { "days must be positive, was $days" }
    if (events.isEmpty()) return emptyList()

    val dated = events.mapNotNull { e ->
        val iso = e.dtComu.take(10)
        try {
            LocalDate.parse(iso) to e.prezzo
        } catch (_: DateTimeParseException) {
            null
        }
    }.sortedBy { it.first }
    if (dated.isEmpty()) return emptyList()

    val windowStart = today.minusDays((days - 1).toLong())
    val out = ArrayList<PricePoint>(days)
    var idx = 0
    var current: Double? = null

    // Consuma in anticipo tutti gli eventi precedenti all'inizio finestra: serve solo l'ultimo
    // come "ancora" per il primo giorno.
    while (idx < dated.size && dated[idx].first.isBefore(windowStart)) {
        current = dated[idx].second
        idx++
    }

    var day = windowStart
    while (!day.isAfter(today)) {
        while (idx < dated.size && !dated[idx].first.isAfter(day)) {
            current = dated[idx].second
            idx++
        }
        if (current != null) out.add(PricePoint(day, current))
        day = day.plusDays(1)
    }
    return out
}
