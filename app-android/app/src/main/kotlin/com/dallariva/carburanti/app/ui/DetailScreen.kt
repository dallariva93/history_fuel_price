package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dallariva.carburanti.app.FuelViewModel
import java.time.LocalDate

private const val HISTORY_WINDOW_DAYS = 30

/**
 * Dettaglio impianto: header (nome, comune, ultimo prezzo) + grafico dello storico.
 * Lo storico viene caricato dal [FuelViewModel] (mai accesso diretto ai dati qui).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: FuelViewModel,
    impiantoId: Long,
    onBack: () -> Unit,
) {
    val station = vm.stazione(impiantoId)
    val carburante = station?.prezzo?.carburante ?: vm.uiState.carburante
    val self = station?.prezzo?.self ?: vm.uiState.self
    val history = vm.historyState

    LaunchedEffect(impiantoId, carburante, self) {
        vm.loadHistory(impiantoId, carburante, self)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station?.distributore?.nome?.ifBlank { "Distributore $impiantoId" } ?: "Dettaglio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (station != null) {
                Text(
                    "${station.distributore.comune} (${station.distributore.provincia})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "$carburante ${if (self) "self" else "servito"} — %.3f € (agg. ${station.prezzo.dtComu})"
                        .format(station.prezzo.prezzo),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                "Andamento storico (ultimi $HISTORY_WINDOW_DAYS giorni)",
                style = MaterialTheme.typography.titleSmall,
            )

            when {
                history.loading -> Text("Caricamento storico…")
                history.error != null -> Text("Errore: ${history.error}")
                else -> {
                    val today = LocalDate.now()
                    val serie = forwardFill(history.prezzi, HISTORY_WINDOW_DAYS, today)
                    PriceChart(punti = serie, modifier = Modifier.fillMaxWidth())
                    if (serie.isNotEmpty()) {
                        val values = serie.map { it.prezzo }
                        val windowStart = today.minusDays((HISTORY_WINDOW_DAYS - 1).toLong())
                        val variazioniInFinestra = history.prezzi.count { p ->
                            val iso = p.dtComu.take(10)
                            runCatching { LocalDate.parse(iso) }
                                .map { !it.isBefore(windowStart) && !it.isAfter(today) }
                                .getOrDefault(false)
                        }
                        Text(
                            "min %.3f € • max %.3f € • %d variazioni in finestra"
                                .format(values.min(), values.max(), variazioniInFinestra),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
