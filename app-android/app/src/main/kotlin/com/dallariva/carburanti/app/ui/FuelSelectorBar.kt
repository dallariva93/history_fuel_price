package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dallariva.carburanti.app.FUEL_OPTIONS
import com.dallariva.carburanti.app.SOLO_SERVITO
import com.dallariva.carburanti.app.SortBy

/**
 * Barra filtri su due righe:
 *  1) scelta carburante (chip);
 *  2) modalita self/servito + ordinamento (prezzo / distanza).
 *
 * [self] e' la modalita EFFETTIVA da mostrare. Per i carburanti solo-servito (GPL, Metano)
 * il chip "Self" e' disabilitato e resta selezionato "Servito". Default: self per tutti gli
 * altri carburanti, ordinamento per prezzo.
 */
@Composable
fun FuelSelectorBar(
    carburante: String,
    self: Boolean,
    sortBy: SortBy,
    onSelectFuel: (String) -> Unit,
    onSelectMode: (Boolean) -> Unit,
    onSelectSort: (SortBy) -> Unit,
) {
    val soloServito = carburante in SOLO_SERVITO

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Riga 1: carburante
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FUEL_OPTIONS.forEach { fuel ->
                    FilterChip(
                        selected = fuel == carburante,
                        onClick = { onSelectFuel(fuel) },
                        label = { Text(fuel) },
                    )
                }
            }

            // Riga 2: modalita + ordinamento
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = self,
                    enabled = !soloServito,
                    onClick = { onSelectMode(true) },
                    label = { Text("Self") },
                )
                FilterChip(
                    selected = !self,
                    onClick = { onSelectMode(false) },
                    label = { Text("Servito") },
                )

                Spacer(Modifier.width(8.dp))
                Text("Ordina:", style = MaterialTheme.typography.labelLarge)
                FilterChip(
                    selected = sortBy == SortBy.PREZZO,
                    onClick = { onSelectSort(SortBy.PREZZO) },
                    label = { Text("Prezzo") },
                )
                FilterChip(
                    selected = sortBy == SortBy.DISTANZA,
                    onClick = { onSelectSort(SortBy.DISTANZA) },
                    label = { Text("Distanza") },
                )
            }
        }
    }
}
