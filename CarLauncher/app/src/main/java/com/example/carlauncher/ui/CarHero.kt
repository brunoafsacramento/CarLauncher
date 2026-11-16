package com.example.carlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.carlauncher.Prefs
import com.example.carlauncher.car.Zone
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Sedã genérico desenhado em código, na cor escolhida nos ajustes.
 * - xray: deixa a lataria translúcida e mostra motor, escapamento, tanque e bateria.
 * - faults: regiões com falha, marcadas com um ponto vermelho pulsando.
 * - flash: de 0 a 1, reforça os faróis (usado na animação de despertar).
 */
@Composable
fun CarHero(
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    xray: Boolean = false,
    faults: Set<Zone> = emptySet(),
    flash: () -> Float = { 0f }
) {
    val reduced = Prefs.reducedEffects.value
    val phase: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.3f) }
    } else {
        val transition = rememberInfiniteTransition(label = "car")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "carPhase"
        )
    }
    val paint = Ui.carColor
    val accent = Ui.Ice
    val art = remember(paint, accent) { CarArt(paint, accent) }
    Canvas(modifier) {
        val s = min(size.width / CarArt.W, size.height / CarArt.H)
        val dx = (size.width - CarArt.W * s) / 2f
        val dy = (size.height - CarArt.H * s) / 2f
        withTransform({
            translate(dx, dy)
            scale(s, s, Offset.Zero)
        }) {
            with(art) { drawCar(phase.value, moving, xray, faults, flash(), !reduced) }
        }
    }
}

private class CarArt(paint: Color, private val accent: Color) {
    companion object {
        const val W = 1000f
        const val H = 400f
        const val GROUND = 324f
    }

    private val rearWheel = Offset(235f, 262f)
    private val frontWheel = Offset(765f, 262f)

    private val body = Path().apply {
        moveTo(70f, 262f)
        cubicTo(55f, 240f, 52f, 205f, 70f, 180f)      // traseira
        cubicTo(110f, 165f, 170f, 158f, 215f, 154f)   // tampa do porta-malas
        cubicTo(290f, 95f, 360f, 72f, 470f, 70f)      // vidro traseiro até o teto
        cubicTo(560f, 68f, 620f, 85f, 690f, 138f)     // teto e para-brisa
        cubicTo(780f, 146f, 880f, 160f, 925f, 188f)   // capô
        cubicTo(950f, 200f, 955f, 235f, 945f, 262f)   // frente
        lineTo(837f, 262f)
        arcTo(Rect(693f, 190f, 837f, 334f), 0f, -180f, false) // caixa de roda dianteira
        lineTo(307f, 262f)
        arcTo(Rect(163f, 190f, 307f, 334f), 0f, -180f, false) // caixa de roda traseira
        close()
    }

    private val windows = Path().apply {
        moveTo(252f, 152f)
        cubicTo(305f, 110f, 368f, 88f, 468f, 85f)
        cubicTo(548f, 84f, 612f, 98f, 662f, 142f)
        close()
    }

    private val shoulder = Path().apply {
        moveTo(85f, 197f)
        cubicTo(300f, 186f, 700f, 186f, 925f, 201f)
    }

    private val headlight = Path().apply {
        moveTo(898f, 184f); lineTo(938f, 195f); lineTo(944f, 210f); lineTo(904f, 203f); close()
    }

    private val taillight = Path().apply {
        moveTo(61f, 185f); lineTo(98f, 180f); lineTo(102f, 194f); lineTo(59f, 199f); close()
    }

    /** Cano de escape: sai do motor, passa por baixo do carro e vai até a traseira. */
    private val exhaust = Path().apply {
        moveTo(800f, 244f)
        lineTo(800f, 270f)
        lineTo(150f, 270f)
    }

    private val bodyBrush = Brush.verticalGradient(
        0.00f to lerp(paint, Color.White, 0.60f),
        0.38f to paint,
        0.66f to lerp(paint, Color.Black, 0.50f),
        1.00f to lerp(paint, Color.Black, 0.86f),
        startY = 68f, endY = 262f
    )
    private val reflectionBrush = Brush.verticalGradient(
        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f)),
        startY = 205f, endY = 262f
    )
    private val windowBrush = Brush.linearGradient(
        listOf(Color(0xFF0A111E), Color(0xFF2C4160), Color(0xFF0A111E)),
        start = Offset(250f, 85f), end = Offset(660f, 150f)
    )
    private val sweepBrush = Brush.horizontalGradient(
        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
        startX = -130f, endX = 130f
    )
    private val pillar = lerp(paint, Color.Black, 0.25f)
    private val underGlow = softBlob(accent, 0.30f)
    private val headGlow = softBlob(Color(0xFFCDEBFF), 0.75f)
    private val tailGlow = softBlob(Ui.Redline, 0.65f)
    private val faultGlow = softBlob(Ui.Redline, 0.85f)
    private val puff = softBlob(Color(0xFFC5CEDD), 1f)
    private val rearRim = rimBrush(rearWheel)
    private val frontRim = rimBrush(frontWheel)

    private fun rimBrush(c: Offset) = Brush.radialGradient(
        listOf(Color(0xFFE4EAF3), Color(0xFF8A96AB), Color(0xFF39424F)),
        center = c, radius = 42f
    )

    private fun anchor(zone: Zone): Offset = when (zone) {
        Zone.Engine -> Offset(817f, 208f)
        Zone.Intake -> Offset(892f, 189f)
        Zone.Cooling -> Offset(927f, 223f)
        Zone.Electrical -> Offset(732f, 181f)
        Zone.Transmission -> Offset(744f, 226f)
        Zone.Exhaust -> Offset(595f, 270f)
        Zone.Fuel -> Offset(375f, 241f)
    }

    fun DrawScope.drawCar(
        p: Float, moving: Boolean, xray: Boolean, faults: Set<Zone>, flash: Float, animated: Boolean
    ) {
        val wave = sin(2f * PI.toFloat() * p)

        // Brilho no chão e sombra
        blob(underGlow, Offset(500f, GROUND + 6f), 470f, 46f, 1f)
        drawOval(Color.Black.copy(alpha = 0.65f), Offset(80f, GROUND - 12f), Size(850f, 26f))

        // Reflexo da lataria no chão
        clipRect(0f, GROUND, W, H) {
            withTransform({ scale(1f, -1f, Offset(0f, GROUND)) }) {
                drawPath(body, reflectionBrush)
            }
        }

        // Fumaça do escapamento
        if (animated) {
            for (i in 0 until 4) {
                val q = (p * 2f + i / 4f) % 1f
                blob(puff, Offset(60f - q * 150f, 252f - q * 58f), 12f + q * 46f, 12f + q * 46f, (1f - q) * 0.20f)
            }
        }

        // Lataria (translúcida no raio-x), com uma faixa de luz passando por ela
        drawPath(body, bodyBrush, alpha = if (xray) 0.30f else 1f)
        if (animated && !xray) {
            clipPath(body) {
                translate(left = -250f + p * 1500f) {
                    drawRect(sweepBrush, Offset(-130f, 0f), Size(260f, H))
                }
            }
        }

        // Vidros, coluna e detalhes
        drawPath(windows, windowBrush, alpha = if (xray) 0.45f else 1f)
        if (!xray) {
            drawLine(pillar, Offset(472f, 85f), Offset(464f, 151f), strokeWidth = 12f)
            drawPath(shoulder, Color.White.copy(alpha = 0.40f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawLine(Color.Black.copy(alpha = 0.30f), Offset(466f, 152f), Offset(470f, 256f), strokeWidth = 2f)
            drawRoundRect(Color.Black.copy(alpha = 0.28f), Offset(392f, 170f), Size(46f, 7f), CornerRadius(4f))
            drawRoundRect(Color.Black.copy(alpha = 0.28f), Offset(596f, 172f), Size(46f, 7f), CornerRadius(4f))
        } else {
            drawInternals()
        }

        // Faróis e lanternas, com brilho pulsando de leve (e reforçado no despertar)
        val boost = flash.coerceIn(0f, 1f)
        blob(headGlow, Offset(940f, 200f), 95f + boost * 130f, 60f + boost * 70f, 0.55f + 0.20f * wave + boost * 0.25f)
        drawPath(headlight, Color(0xFFE8F6FF))
        blob(tailGlow, Offset(66f, 190f), 70f + boost * 60f, 46f + boost * 30f, 0.55f + 0.15f * wave + boost * 0.25f)
        drawPath(taillight, Ui.Redline)

        // Rodas
        val angle = if (moving && animated) p * 1080f else 18f
        wheel(rearWheel, rearRim, angle)
        wheel(frontWheel, frontRim, angle)

        // Pontos de falha
        for (zone in faults) {
            val c = anchor(zone)
            val pulse = 0.5f + 0.5f * wave
            blob(faultGlow, c, 34f + 16f * pulse, 34f + 16f * pulse, 0.9f)
            drawCircle(Ui.Redline, 8f, c)
            drawCircle(Color.White, 12f + 9f * pulse, c, alpha = 1f - pulse * 0.7f, style = Stroke(width = 2.5f))
        }
    }

    /** Peças por dentro do carro, em traço fino, para o modo raio-x. */
    private fun DrawScope.drawInternals() {
        val line = accent.copy(alpha = 0.80f)
        val thin = Stroke(width = 3f)
        drawRoundRect(line, Offset(770f, 172f), Size(95f, 72f), CornerRadius(10f), style = thin)   // motor
        drawRoundRect(line, Offset(872f, 178f), Size(40f, 22f), CornerRadius(6f), style = thin)    // filtro de ar
        drawRoundRect(line, Offset(922f, 196f), Size(10f, 54f), CornerRadius(4f), style = thin)    // radiador
        drawRoundRect(line, Offset(712f, 168f), Size(40f, 26f), CornerRadius(4f), style = thin)    // bateria
        drawRoundRect(line, Offset(722f, 206f), Size(44f, 40f), CornerRadius(8f), style = thin)    // câmbio
        drawPath(exhaust, line, style = Stroke(width = 5f, cap = StrokeCap.Round))                 // escapamento
        drawRoundRect(line, Offset(560f, 262f), Size(70f, 16f), CornerRadius(8f), style = thin)    // catalisador
        drawRoundRect(line, Offset(84f, 259f), Size(72f, 20f), CornerRadius(10f), style = thin)    // silencioso
        drawRoundRect(line, Offset(330f, 226f), Size(90f, 30f), CornerRadius(10f), style = thin)   // tanque
    }

    private fun DrawScope.blob(brush: Brush, center: Offset, rx: Float, ry: Float, alpha: Float) {
        withTransform({
            translate(center.x, center.y)
            scale(rx, ry, Offset.Zero)
        }) {
            drawCircle(brush, 1f, Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
        }
    }

    private fun DrawScope.wheel(c: Offset, rim: Brush, angle: Float) {
        drawCircle(Color(0xFF06080C), 62f, c)
        drawCircle(Color(0xFF1B212D), 62f, c, style = Stroke(width = 3f))
        drawCircle(rim, 42f, c)
        rotate(angle, c) {
            for (i in 0 until 5) {
                rotate(i * 72f, c) {
                    drawLine(Color(0xFF0B0E14), c, Offset(c.x, c.y - 40f), strokeWidth = 8f)
                }
            }
        }
        drawCircle(Color(0xFF0B0E14), 42f, c, style = Stroke(width = 4f))
        drawCircle(Color(0xFFD5DCE8), 9f, c)
    }
}
