package com.dallariva.carburanti.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.dallariva.carburanti.app.FuelViewModel

/**
 * Grafo di navigazione: schermata principale (lista + mappa) e dettaglio impianto.
 * Il [FuelViewModel] e' scopato all'host di navigazione e condiviso tra le schermate.
 */
@Composable
fun CarburantiApp() {
    val navController = rememberNavController()
    val vm: FuelViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onStationClick = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(
            route = "detail/{impiantoId}",
            arguments = listOf(navArgument("impiantoId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("impiantoId") ?: return@composable
            DetailScreen(
                vm = vm,
                impiantoId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
