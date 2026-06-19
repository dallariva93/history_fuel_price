package com.dallariva.carburanti.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.dallariva.carburanti.app.ui.CarburantiApp
import org.maplibre.android.MapLibre

/**
 * Unica Activity dell'app telefono. Inizializza MapLibre (richiesto prima di gonfiare una
 * MapView) e monta la UI Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme {
                CarburantiApp()
            }
        }
    }
}
