package com.dallariva.carburanti.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dallariva.carburanti.app.DEFAULT_LOCATION
import com.dallariva.carburanti.app.FuelViewModel
import com.dallariva.carburanti.app.effectiveSelf
import kotlinx.coroutines.launch

/**
 * Schermata principale: barra di selezione carburante + due tab (Lista / Mappa).
 * All'avvio richiede il permesso di posizione e carica i distributori vicini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FuelViewModel,
    onStationClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = vm.uiState

    // Carica usando la posizione reale (se permessa) o il fallback di default.
    fun loadAround() = scope.launch {
        val (lat, lon) = lastKnownLocation(context) ?: DEFAULT_LOCATION
        vm.refresh(lat, lon)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> loadAround() }

    // Primo avvio: chiedi il permesso se assente, altrimenti carica subito.
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) loadAround()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            FuelSelectorBar(
                carburante = state.carburante,
                self = effectiveSelf(state.carburante, state.self),
                sortBy = state.sortBy,
                onSelectFuel = { fuel -> vm.selectFuel(fuel, state.self) },
                onSelectMode = { selfMode -> vm.selectFuel(state.carburante, selfMode) },
                onSelectSort = { sort -> vm.setSort(sort) },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Lista") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mappa") })
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> StationList(
                        state = state,
                        onRetry = { loadAround() },
                        onStationClick = onStationClick,
                    )
                    1 -> FuelMap(
                        stazioni = state.stazioni,
                        center = state.lat?.let { la -> state.lon?.let { lo -> la to lo } },
                        onStationClick = onStationClick,
                    )
                }
                if (state.loading) {
                    Text(
                        "Caricamento…",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
