package com.example.carlauncher.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.MainViewModel
import com.example.carlauncher.Prefs
import com.example.carlauncher.Reminders
import com.example.carlauncher.apps.AppInfo
import com.example.carlauncher.apps.launchApp
import com.example.carlauncher.apps.navigateTo
import com.example.carlauncher.apps.openVoiceAssistant
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.car.zoneOf
import com.example.carlauncher.media.MediaInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun HomeScreen(vm: MainViewModel, onOpenTab: (Int) -> Unit) {
    // Despertar: quando o carro conecta, os faróis dão uma piscada forte
    val flash = remember { Animatable(0f) }
    LaunchedEffect(CarHub.wakeUps) {
        if (CarHub.wakeUps > 0) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(500))
            flash.animateTo(0f, tween(1600))
        }
    }
    val dtcs = CarHub.dtcs
    val faults = remember(dtcs) { dtcs.orEmpty().mapNotNull { zoneOf(it.code) }.toSet() }
    // À noite o carro já aparece com os faróis acesos
    val startView = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 18 || hour < 6) CarViews.gallery.indexOf(CarViews.night) else 0
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClockBlock()
                QuickRow(onNeedAddress = { onOpenTab(5) })
                MediaCard(Modifier.weight(1f).fillMaxWidth())
            }
            Box(Modifier.weight(1.4f).fillMaxHeight()) {
                CarGallery(
                    Modifier.fillMaxSize(),
                    start = startView,
                    faults = faults,
                    flash = { flash.value }
                )
                CarChips(Modifier.align(Alignment.TopEnd), onOpenTab)
            }
        }
        Dock(vm)
    }
}

@Composable
private fun ClockBlock() {
    val locale = remember { Locale("pt", "BR") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", locale) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", locale) }
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    Column(Modifier.padding(start = 6.dp)) {
        Text(
            timeFormat.format(now),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            color = Ui.Text,
            maxLines = 1
        )
        Text(
            dateFormat.format(now).replaceFirstChar { it.uppercase() },
            fontSize = 15.sp,
            color = Ui.TextDim,
            maxLines = 1
        )
    }
}

/** Atalhos de um toque: navegar para casa, para o trabalho, ou falar com o assistente. */
@Composable
private fun QuickRow(onNeedAddress: () -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickButton("Casa", Icons.Default.Home) {
            val address = Prefs.home.value
            if (address.isBlank()) onNeedAddress() else navigateTo(context, address)
        }
        QuickButton("Trabalho", Icons.Default.Place) {
            val address = Prefs.work.value
            if (address.isBlank()) onNeedAddress() else navigateTo(context, address)
        }
        QuickButton("Falar", null) { openVoiceAssistant(context) }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Ui.Ice, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, color = Ui.Text, maxLines = 1)
    }
}

@Composable
private fun MediaCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            MediaInfo.refresh(context)
            delay(2000)
        }
    }
    val info = MediaInfo.now
    val title = when {
        info == null -> "Controle de música"
        info.title.isNullOrBlank() -> "Nada tocando"
        else -> info.title.orEmpty()
    }
    val subtitle = when {
        info == null -> "Toque aqui para mostrar o nome da música"
        info.title.isNullOrBlank() -> "Abra o rádio ou um app de música"
        else -> info.artist ?: ""
    }
    val art = info?.art
    Box(modifier.glass()) {
        // A capa do álbum vira o fundo do cartão, escurecida para o texto continuar legível
        if (art != null) {
            Image(art, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.38f)
        }
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth().pressable {
                    if (info == null) MediaInfo.openAccessSettings(context)
                    else info.pkg?.let { launchApp(context, it) }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle, fontSize = 14.sp, color = Ui.TextDim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (info?.playing == true && !Prefs.reducedEffects.value) {
                    Spacer(Modifier.width(10.dp))
                    EqualizerBars()
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaButton(Glyph.Previous) { MediaInfo.key(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                MediaButton(if (info?.playing == true) Glyph.Pause else Glyph.Play, big = true) {
                    MediaInfo.key(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                MediaButton(Glyph.Next) { MediaInfo.key(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
            }
        }
    }
}

private enum class Glyph { Previous, Play, Pause, Next }

@Composable
private fun MediaButton(glyph: Glyph, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (big) 58.dp else 48.dp)
            .pressable(onClick = onClick)
            .glass(CircleShape, tint = if (big) Ui.Ice else Color.White, strength = if (big) 1.8f else 1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(if (big) 22.dp else 18.dp)) { drawGlyph(glyph, Ui.Text) }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    fun triangle(base: Float, tip: Float) = Path().apply {
        moveTo(base, 0f)
        lineTo(tip, h / 2f)
        lineTo(base, h)
        close()
    }
    when (glyph) {
        Glyph.Play -> drawPath(triangle(w * 0.18f, w * 0.98f), color)
        Glyph.Pause -> {
            drawRect(color, Offset(w * 0.12f, 0f), Size(w * 0.26f, h))
            drawRect(color, Offset(w * 0.62f, 0f), Size(w * 0.26f, h))
        }
        Glyph.Next -> {
            drawPath(triangle(0f, w * 0.72f), color)
            drawRect(color, Offset(w * 0.80f, 0f), Size(w * 0.16f, h))
        }
        Glyph.Previous -> {
            drawPath(triangle(w, w * 0.28f), color)
            drawRect(color, Offset(w * 0.04f, 0f), Size(w * 0.16f, h))
        }
    }
}

/** Barrinhas que dançam enquanto há música tocando. */
@Composable
private fun EqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "eqPhase"
    )
    val color = Ui.Ice
    Canvas(Modifier.size(24.dp, 18.dp)) {
        val bar = size.width / 7f
        for (i in 0 until 4) {
            val level = 0.30f + 0.70f * abs(sin(2f * PI.toFloat() * (phase.value + i * 0.23f)))
            val barHeight = size.height * level
            drawRoundRect(
                color,
                Offset(i * bar * 2f, size.height - barHeight),
                Size(bar, barHeight),
                CornerRadius(bar / 2f)
            )
        }
    }
}

/** Etiquetas sobre o carro: dados principais quando conectado, ou o estado da conexão. */
@Composable
private fun CarChips(modifier: Modifier, onOpenTab: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (CarHub.connected) {
                val d = CarHub.live
                Chip("${d.speed ?: "--"} km/h", Ui.Ice) { onOpenTab(2) }
                Chip("${d.rpm ?: "--"} rpm", Ui.Ice) { onOpenTab(2) }
                Chip("${d.coolant ?: "--"} °C", if ((d.coolant ?: 0) >= 105) Ui.Redline else Ui.Ice) { onOpenTab(2) }
            } else {
                Chip(if (CarHub.active) "Conectando ao carro…" else "Carro desconectado", Ui.TextDim) { onOpenTab(2) }
            }
        }
        if (CarHub.connected) {
            val coolant = CarHub.live.coolant
            val count = CarHub.dtcs.orEmpty().size
            if (coolant != null && coolant < 60) Chip("Motor frio: evite giro alto", Ui.Amber) { onOpenTab(2) }
            if (count > 0) Chip(if (count == 1) "1 erro registrado" else "$count erros registrados", Ui.Redline) { onOpenTab(3) }
        }
        // Documentos vencendo e lavagem atrasada
        for (warning in Reminders.due()) Chip(warning, Ui.Amber) { onOpenTab(5) }
        val washDays = CarHub.daysSinceWash
        if (washDays != null && washDays >= 21) Chip("Sem lavar há $washDays dias", Ui.Amber) { onOpenTab(4) }
    }
}

@Composable
private fun Chip(text: String, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = Ui.Text, maxLines = 1)
    }
}

/** Barra de atalhos: os apps fixados pelo usuário (ou os sugeridos, se ele ainda não escolheu). */
@Composable
private fun Dock(vm: MainViewModel) {
    val context = LocalContext.current
    val favorites = vm.favoriteApps
    Box(
        Modifier.fillMaxWidth().height(92.dp).glass(RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (favorites.isEmpty()) {
            Text(
                "Segure um app na aba Apps para fixá-lo aqui",
                color = Ui.TextDim, fontSize = 14.sp, modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(favorites, key = { it.pkg }) { app ->
                    DockItem(app) { launchApp(context, app.pkg) }
                }
            }
        }
    }
}

@Composable
private fun DockItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).pressable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(app.icon, null, Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(app.label, fontSize = 12.sp, color = Ui.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
