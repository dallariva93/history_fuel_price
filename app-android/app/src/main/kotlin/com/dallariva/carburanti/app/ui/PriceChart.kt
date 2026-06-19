package com.dallariva.carburanti.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Grafico a linea spezzata dell'andamento prezzo. Disegnato a mano su Canvas: niente librerie
 * esterne (MVP). I punti sono campioni giornalieri gia' forward-fillati (vedi [forwardFill]).
 */
@Composable
fun PriceChart(
    punti: List<PricePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (punti.size < 2) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("Dati storici insufficienti per il grafico.")
        }
        return
    }

    val values = punti.map { it.prezzo }
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width
        val h = size.height
        val n = punti.size

        val points = punti.mapIndexed { i, p ->
            val x = if (n == 1) 0f else w * i / (n - 1)
            val y = (h - h * ((p.prezzo - minV) / range)).toFloat()
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4f,
            )
        }
    }
}
