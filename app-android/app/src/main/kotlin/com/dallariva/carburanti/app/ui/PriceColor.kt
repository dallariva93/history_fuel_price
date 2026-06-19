package com.dallariva.carburanti.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Interpola un colore verde (economico) -> rosso (caro) in base alla posizione del [prezzo]
 * nell'intervallo [min]..[max]. Usato sia dai marker mappa sia dalla lista.
 */
fun priceColor(prezzo: Double, min: Double, max: Double): Color {
    val t = if (max <= min) 0f else ((prezzo - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    // verde (0,0.7,0.2) -> giallo -> rosso (0.85,0.1,0.1)
    val r = lerp(0.10f, 0.85f, t)
    val g = lerp(0.70f, 0.10f, t)
    val b = 0.15f
    return Color(r, g, b)
}

/** Versione hex "#RRGGBB" del colore, richiesta dalle CircleOptions di MapLibre. */
fun priceColorHex(prezzo: Double, min: Double, max: Double): String {
    val c = priceColor(prezzo, min, max)
    val ri = (c.red * 255).toInt()
    val gi = (c.green * 255).toInt()
    val bi = (c.blue * 255).toInt()
    return String.format("#%02X%02X%02X", ri, gi, bi)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
