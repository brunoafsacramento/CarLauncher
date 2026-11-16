package com.example.carlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.carlauncher.Prefs
import com.example.carlauncher.car.CarHub
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class Blob(
    val x: Float, val y: Float, val radius: Float,
    val kx: Int, val ky: Int, val offset: Float, val brush: Brush
)

/**
 * Fundo com fumaça. Com o carro conectado, a fumaça acelera junto com a rotação
 * e o fundo vai avermelhando perto do limite de giro.
 */
@Composable
fun SmokeBackground(modifier: Modifier = Modifier) {
    val reduced = Prefs.reducedEffects.value
    val accent = Ui.Ice
    val phase = remember { mutableFloatStateOf(0.2f) }

    if (!reduced) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                        val energy = ((CarHub.live.rpm ?: 0) / 7000f).coerceIn(0f, 1f)
                        phase.floatValue = (phase.floatValue + dt / 48f * (1f + energy * 6f)) % 64f
                    }
                    last = now
                }
            }
        }
    }

    val base = remember { Brush.verticalGradient(listOf(Ui.NightTop, Ui.NightBottom)) }
    val blobs = remember(accent) {
        listOf(
            Blob(0.18f, 0.30f, 0.42f, 1, 2, 0.0f, softBlob(Ui.Smoke, 0.34f)),
            Blob(0.62f, 0.72f, 0.50f, 2, 1, 1.7f, softBlob(Ui.Smoke, 0.28f)),
            Blob(0.86f, 0.22f, 0.36f, 1, 3, 3.1f, softBlob(Color(0xFF2E5C8A), 0.30f)),
            Blob(0.40f, 0.88f, 0.34f, 3, 2, 4.4f, softBlob(Color(0xFF8E9BB5), 0.16f)),
            Blob(0.72f, 0.45f, 0.30f, 2, 3, 5.2f, softBlob(accent, 0.10f)),
        )
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val p = phase.floatValue * 2f * PI.toFloat()
        for (b in blobs) {
            val cx = (b.x + 0.11f * sin(p * b.kx + b.offset)) * size.width
            val cy = (b.y + 0.13f * cos(p * b.ky + b.offset)) * size.height
            val r = b.radius * size.width
            withTransform({
                translate(cx, cy)
                scale(r, r, Offset.Zero)
            }) {
                drawCircle(b.brush, 1f, Offset.Zero)
            }
        }
        // Perto do limite de giro o ambiente esquenta
        val rpm = CarHub.live.rpm ?: 0
        val danger = ((rpm / 6800f - 0.8f) / 0.2f).coerceIn(0f, 1f)
        if (danger > 0f) drawRect(Ui.Redline, alpha = danger * 0.16f)
    }
}
