package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dallariva.carburanti.app.HomeUiState
import com.dallariva.carburanti.app.effectiveSelf
import com.dallariva.carburanti.data.StatoImpianto

/**
 * Lista dei distributori piu' economici. Ogni riga: pallino colorato per prezzo, nome/comune,
 * prezzo, distanza, data ultimo aggiornamento. Tap -> dettaglio.
 */
@Composable
fun StationList(
    state: HomeUiState,
    onRetry: () -> Unit,
    onStationClick: (Long) -> Unit,
) {
    if (state.error != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Errore: ${state.error}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Riprova") }
        }
        return
    }

    if (!state.loading && state.stazioni.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Nessun distributore con ${state.carburante} (${if (effectiveSelf(state.carburante, state.self)) "self" else "servito"}) nei dintorni.")
        }
        return
    }

    val prezzi = state.stazioni.map { it.prezzo.prezzo }
    val min = remember(prezzi) { prezzi.minOrNull() ?: 0.0 }
    val max = remember(prezzi) { prezzi.maxOrNull() ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.stazioni, key = { it.distributore.id }) { item ->
            val nonAggiornato = item.stato == StatoImpianto.NON_AGGIORNATO
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStationClick(item.distributore.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .alpha(if (nonAggiornato) 0.6f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(priceColor(item.prezzo.prezzo, min, max))
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.distributore.nome.ifBlank { "Distributore ${item.distributore.id}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${item.distributore.comune} • ${"%.1f".format(item.distanzaKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "agg. ${item.prezzo.dtComu}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (nonAggiornato) {
                            Text(
                                "Possibilmente chiuso · registro fermo da ${item.giorniSenzaAggiornamento}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        "%.3f €".format(item.prezzo.prezzo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
