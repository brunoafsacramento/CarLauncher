package com.example.carlauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.obd.LiveData
import kotlin.math.max

/** Consumo do momento: km/l andando, litros por hora parado. */
fun instantConsumption(d: LiveData): String? {
    val rate = d.fuelRate ?: return null
    val speed = d.speed ?: 0
    return if (speed >= 5 && rate > 0.05f) "${(speed / rate).fmt()} km/l" else "${rate.fmt()} L/h"
}

@Composable
fun ObdScreen(onConnectClick: () -> Unit) {
    val d = CarHub.live

    // Despertar: ao conectar, os mostradores varrem até o fim e voltam
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(CarHub.wakeUps) {
        if (CarHub.wakeUps > 0) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700))
            sweep.animateTo(0f, tween(900))
        }
    }

    var charts by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle("Motor", CarHub.status, Modifier.weight(1f))
            GlassButton(if (charts) "Ver mostradores" else "Ver gráficos") { charts = !charts }
            GlassButton(if (CarHub.active) "Desconectar" else "Conectar ao carro", onClick = onConnectClick)
        }
        if (charts) {
            // Os últimos dois minutos de cada leitura
            val recent = CarHub.recent
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sparkline("Rotação", "rpm", recent.map { it.rpm?.toFloat() }, Modifier.weight(1f).fillMaxHeight())
                Sparkline("Velocidade", "km/h", recent.map { it.speed?.toFloat() }, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sparkline("Temperatura", "°C", recent.map { it.coolant?.toFloat() }, Modifier.weight(1f).fillMaxHeight())
                Sparkline(
                    "Correção de mistura", "%",
                    recent.map { s -> s.shortTrim?.let { it + (s.longTrim ?: 0f) } },
                    Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArcGauge("Rotação", d.rpm?.toFloat(), 8000f, "rpm", sweep.value, Modifier.weight(1f).fillMaxHeight(), warnFrom = 6500f)
                ArcGauge("Velocidade", d.speed?.toFloat(), 220f, "km/h", sweep.value, Modifier.weight(1f).fillMaxHeight())
                ArcGauge("Temperatura", d.coolant?.toFloat(), 130f, "°C", sweep.value, Modifier.weight(1f).fillMaxHeight(), warnFrom = 105f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val trim = d.shortTrim?.let { it + (d.longTrim ?: 0f) }
            Stat("Consumo agora", instantConsumption(d), Modifier.weight(1f))
            Stat("Carga do motor", d.load?.let { "$it %" }, Modifier.weight(1f))
            Stat("Borboleta", d.throttle?.let { "$it %" }, Modifier.weight(1f))
            // Correção acima de 10% para qualquer lado indica mistura fora do normal
            Stat(
                "Correção de mistura", trim?.let { "${it.fmt()} %" }, Modifier.weight(1f),
                valueColor = if (trim != null && (trim > 10f || trim < -10f)) Ui.Redline else Ui.Text
            )
            Stat(
                "Bateria", d.voltage?.let { "${it.fmt()} V" }, Modifier.weight(1f),
                valueColor = if (CarHub.chargingProblem) Ui.Redline else Ui.Text
            )
        }
    }
}

/** Gráfico de linha simples com o valor atual, o mínimo e o máximo do período. */
@Composable
private fun Sparkline(label: String, unit: String, values: List<Float?>, modifier: Modifier = Modifier) {
    val points = values.filterNotNull()
    val low = points.minOrNull()
    val high = points.maxOrNull()
    val color = Ui.Ice
    Column(modifier.glass().padding(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Ui.TextDim, maxLines = 1)
            Text(
                points.lastOrNull()?.let { "${it.fmt(if (unit == "%") 1 else 0)} $unit" } ?: "--",
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ui.Text, maxLines = 1
            )
        }
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            if (points.size >= 2 && low != null && high != null) {
                val span = (high - low).coerceAtLeast(1f)
                val stepX = size.width / (points.size - 1)
                val path = Path()
                points.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v - low) / span * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 9f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
        Text(
            if (low != null && high != null) "de ${low.fmt(0)} a ${high.fmt(0)} $unit nos últimos dois minutos"
            else "Sem leituras ainda",
            fontSize = 12.sp, color = Ui.TextDim, maxLines = 1
        )
    }
}

/** Mostrador em arco; o ponteiro desliza até o novo valor e fica vermelho na faixa de alerta. */
@Composable
private fun ArcGauge(
    label: String, value: Float?, limit: Float, unit: String, sweep: Float,
    modifier: Modifier = Modifier, warnFrom: Float? = null
) {
    val fraction by animateFloatAsState(
        targetValue = ((value ?: 0f) / limit).coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "gauge"
    )
    val shown = max(fraction, sweep)
    val warn = value != null && warnFrom != null && value >= warnFrom
    val color by animateColorAsState(if (warn) Ui.Redline else Ui.Ice, label = "gaugeColor")

    Box(modifier.glass(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val d = minOf(size.width, size.height)
            val stroke = d * 0.07f
            val topLeft = Offset((size.width - d) / 2f + stroke / 2f, (size.height - d) / 2f + stroke / 2f)
            val arcSize = Size(d - stroke, d - stroke)
            drawArc(
                Color.White.copy(alpha = 0.09f), 135f, 270f, false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // Faixa de alerta marcada no fim do arco
            if (warnFrom != null) {
                val from = (warnFrom / limit).coerceIn(0f, 1f)
                drawArc(
                    Ui.Redline.copy(alpha = 0.35f), 135f + 270f * from, 270f * (1f - from), false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke * 0.35f)
                )
            }
            if (shown > 0.001f) {
                drawArc(
                    color.copy(alpha = 0.20f), 135f, 270f * shown, false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round)
                )
                drawArc(
                    color, 135f, 270f * shown, false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value?.toInt()?.toString() ?: "--",
                fontSize = 40.sp, fontWeight = FontWeight.Light, color = Ui.Text, maxLines = 1
            )
            Text(unit, fontSize = 14.sp, color = Ui.TextDim)
            Text(label, fontSize = 13.sp, color = Ui.TextDim)
        }
    }
}
