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
import com.dallariva.carburanti.data.StatoImpianto

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
                station.giorniSenzaAggiornamento?.let { giorni ->
                    Text(
                        "Ultimo aggiornamento registro: $giorni giorni fa",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (station.stato == StatoImpianto.NON_AGGIORNATO) {
                    Text(
                        "Questo impianto potrebbe essere chiuso: non compare nel registro MIMIT " +
                            "degli impianti attivi da ${station.giorniSenzaAggiornamento} giorni. " +
                            "Dato indicativo, non garantito.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text("Andamento storico", style = MaterialTheme.typography.titleSmall)

            when {
                history.loading -> Text("Caricamento storico…")
                history.error != null -> Text("Errore: ${history.error}")
                else -> {
                    PriceChart(prezzi = history.prezzi, modifier = Modifier.fillMaxWidth())
                    if (history.prezzi.isNotEmpty()) {
                        val values = history.prezzi.map { it.prezzo }
                        Text(
                            "min %.3f € • max %.3f € • %d rilevazioni"
                                .format(values.min(), values.max(), values.size),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
