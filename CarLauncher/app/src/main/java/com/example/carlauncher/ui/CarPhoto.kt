package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.Prefs
import com.example.carlauncher.R
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.car.Zone
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Um ângulo do carro, recortado de uma foto do dono.
 * As posições vão de 0 a 1 dentro da imagem (0,0 = canto de cima à esquerda).
 */
class CarView(
    val name: String,
    val image: Int,
    val grounded: Boolean,              // desenha sombra e brilho no chão
    val lights: List<Offset>,           // faróis ou lanternas, para a piscada do despertar
    val anchors: Map<Zone, Offset>,     // onde marcar a falha de cada região
)

object CarViews {
    val front34 = CarView(
        "Frente", R.drawable.car_front34, true,
        listOf(Offset(0.52f, 0.72f), Offset(0.09f, 0.63f)),
        mapOf(
            Zone.Engine to Offset(0.34f, 0.50f),
            Zone.Intake to Offset(0.20f, 0.50f),
            Zone.Cooling to Offset(0.27f, 0.67f),
            Zone.Electrical to Offset(0.47f, 0.52f),
            Zone.Transmission to Offset(0.33f, 0.40f),
        )
    )
    val front = CarView(
        "De frente", R.drawable.car_front, true,
        listOf(Offset(0.15f, 0.55f), Offset(0.85f, 0.55f)),
        mapOf(
            Zone.Engine to Offset(0.50f, 0.36f),
            Zone.Intake to Offset(0.33f, 0.36f),
            Zone.Cooling to Offset(0.50f, 0.58f),
            Zone.Electrical to Offset(0.68f, 0.36f),
            Zone.Transmission to Offset(0.50f, 0.26f),
        )
    )
    val rear34 = CarView(
        "Traseira", R.drawable.car_rear34, true,
        listOf(Offset(0.60f, 0.52f), Offset(0.95f, 0.47f)),
        mapOf(
            Zone.Exhaust to Offset(0.93f, 0.85f),
            Zone.Fuel to Offset(0.40f, 0.56f),
        )
    )
    val rear = CarView(
        "De trás", R.drawable.car_rear, true,
        listOf(Offset(0.11f, 0.50f), Offset(0.88f, 0.50f)),
        mapOf(
            Zone.Exhaust to Offset(0.775f, 0.925f),
            Zone.Fuel to Offset(0.08f, 0.66f),
        )
    )
    val night = CarView(
        "Noite", R.drawable.car_night, true,
        listOf(Offset(0.055f, 0.57f), Offset(0.58f, 0.63f)),
        mapOf(
            Zone.Engine to Offset(0.33f, 0.50f),
            Zone.Intake to Offset(0.20f, 0.52f),
            Zone.Cooling to Offset(0.24f, 0.68f),
            Zone.Electrical to Offset(0.46f, 0.52f),
            Zone.Transmission to Offset(0.32f, 0.42f),
        )
    )
    val interior = CarView("Por dentro", R.drawable.car_interior, false, emptyList(), emptyMap())

    /** Ordem em que os ângulos aparecem ao girar o carro. */
    val gallery = listOf(front34, front, rear34, rear, night, interior)

    /** Ângulo que mostra melhor as falhas: traseira para escapamento e tanque, frente para o resto. */
    fun indexFor(faults: Set<Zone>): Int {
        val rearOnly = faults.isNotEmpty() && faults.all { it == Zone.Exhaust || it == Zone.Fuel }
        return gallery.indexOf(if (rearOnly) rear34 else front34)
    }
}

private val DustColor = Color(0xFF9C8A6B)

/** Desenha a foto do carro com sombra no chão, luz passando, poeira, faróis e pontos de falha. */
@Composable
fun CarPhoto(
    view: CarView,
    modifier: Modifier = Modifier,
    faults: Set<Zone> = emptySet(),
    flash: () -> Float = { 0f }
) {
    val image = ImageBitmap.imageResource(view.image)
    val reduced = Prefs.reducedEffects.value
    val accent = Ui.Ice
    val dust = CarHub.dust
    val phase: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        val transition = rememberInfiniteTransition(label = "photo")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
            label = "photoPhase"
        )
    }
    val layer = remember { Paint() }
    val groundGlow = remember(accent) { softBlob(accent, 0.26f) }
    val shadow = remember { softBlob(Color.Black, 0.85f) }
    val lightGlow = remember { softBlob(Color(0xFFDDF0FF), 0.9f) }
    val faultGlow = remember { softBlob(Ui.Redline, 0.85f) }
    val sweep = remember {
        Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.26f), Color.Transparent),
            startX = 0f, endX = 1f
        )
    }

    Canvas(modifier) {
        val scale = min(size.width / image.width, size.height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val p = phase.value
        val pulse = 0.5f + 0.5f * sin(2f * PI.toFloat() * p * 4f)

        if (view.grounded) {
            spot(groundGlow, Offset(left + w / 2f, top + h * 0.94f), w * 0.58f, h * 0.17f, 1f)
            spot(shadow, Offset(left + w / 2f, top + h * 0.96f), w * 0.50f, h * 0.10f, 1f)
        }

        // A foto vai numa camada própria, para a luz e a poeira pintarem só em cima do carro
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(left, top, left + w, top + h), layer)
        drawImage(
            image,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt())
        )
        if (dust > 0f) {
            drawRect(DustColor, Offset(left, top), Size(w, h), alpha = dust * 0.32f, blendMode = BlendMode.SrcAtop)
        }
        if (!reduced && view.grounded && p < 0.16f) {
            val band = w * 0.30f
            val x = left - band + (w + band) * (p / 0.16f)
            withTransform({
                translate(x, top)
                scale(band, h, Offset.Zero)
            }) {
                drawRect(sweep, Offset.Zero, Size(1f, 1f), blendMode = BlendMode.SrcAtop)
            }
        }
        canvas.restore()

        // Despertar: os faróis dão uma piscada forte quando o carro conecta
        val boost = flash().coerceIn(0f, 1f)
        if (boost > 0.01f) {
            for (light in view.lights) {
                spot(
                    lightGlow, Offset(left + light.x * w, top + light.y * h),
                    w * 0.20f * (0.5f + boost), w * 0.13f * (0.5f + boost), boost
                )
            }
        }

        // Pontos de falha
        for (zone in faults) {
            val a = view.anchors[zone] ?: continue
            val c = Offset(left + a.x * w, top + a.y * h)
            val r = w * 0.022f
            spot(faultGlow, c, r * (3.4f + 1.6f * pulse), r * (3.4f + 1.6f * pulse), 0.9f)
            drawCircle(Ui.Redline, r, c)
            drawCircle(Color.White, r * (1.5f + 1.1f * pulse), c, alpha = 1f - pulse * 0.7f, style = Stroke(width = r * 0.28f))
        }
    }
}

private fun DrawScope.spot(brush: Brush, center: Offset, rx: Float, ry: Float, alpha: Float) {
    withTransform({
        translate(center.x, center.y)
        scale(rx, ry, Offset.Zero)
    }) {
        drawCircle(brush, 1f, Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** O carro com troca de ângulo: arraste para os lados ou use as setas. */
@Composable
fun CarGallery(
    modifier: Modifier = Modifier,
    start: Int = 0,
    faults: Set<Zone> = emptySet(),
    flash: () -> Float = { 0f }
) {
    val views = CarViews.gallery
    var index by remember(start) { mutableIntStateOf(start.coerceIn(0, views.lastIndex)) }
    var forward by remember { mutableStateOf(true) }
    val step: (Int) -> Unit = { delta ->
        forward = delta > 0
        index = (index + delta + views.size) % views.size
    }

    Box(
        modifier.pointerInput(start) {
            var drag = 0f
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    if (drag < -60f) step(1) else if (drag > 60f) step(-1)
                },
                onHorizontalDrag = { _, amount -> drag += amount }
            )
        }
    ) {
        AnimatedContent(
            targetState = index,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { it / 3 * direction } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 * direction } + fadeOut(tween(200)))
            },
            label = "carView"
        ) { i ->
            CarPhoto(views[i], Modifier.fillMaxSize(), faults, flash)
        }
        Row(
            Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RoundButton("‹") { step(-1) }
            Text(
                views[index].name, Modifier.widthIn(min = 84.dp),
                fontSize = 13.sp, color = Ui.TextDim, textAlign = TextAlign.Center, maxLines = 1
            )
            RoundButton("›") { step(1) }
        }
    }
}
