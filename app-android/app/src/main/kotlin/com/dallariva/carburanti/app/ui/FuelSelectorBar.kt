package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dallariva.carburanti.app.FUEL_OPTIONS

/**
 * Barra superiore: scelta del carburante (chip) + toggle self/servito.
 * Default applicato dal ViewModel: GPL self.
 */
@Composable
fun FuelSelectorBar(
    carburante: String,
    self: Boolean,
    onSelect: (carburante: String, self: Boolean) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FUEL_OPTIONS.forEach { fuel ->
                FilterChip(
                    selected = fuel == carburante,
                    onClick = { onSelect(fuel, self) },
                    label = { Text(fuel) },
                )
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = self,
                onClick = { onSelect(carburante, true) },
                label = { Text("Self") },
            )
            FilterChip(
                selected = !self,
                onClick = { onSelect(carburante, false) },
                label = { Text("Servito") },
            )
        }
    }
}
