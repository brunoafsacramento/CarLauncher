package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.Prefs
import com.example.carlauncher.car.CarHub

/** Luz de troca de marcha: duas faixas crescem das bordas e ficam vermelhas na rotação escolhida. */
@Composable
fun ShiftBar(modifier: Modifier = Modifier) {
    val shift = Prefs.shiftRpm.value
    if (shift <= 0) return
    val accent = Ui.Ice
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val rpm = CarHub.live.rpm ?: 0
        val f = (rpm.toFloat() / shift - 0.6f) / 0.4f
        if (f > 0f) {
            val color = if (f >= 1f) Ui.Redline else accent
            val w = size.width / 2f * f.coerceAtMost(1f)
            drawRect(color, Offset.Zero, Size(w, size.height))
            drawRect(color, Offset(size.width - w, 0f), Size(w, size.height))
        }
    }
}

/** Aviso que desce do topo da tela; tocar nele dispensa. */
@Composable
fun AlertBanner(modifier: Modifier = Modifier) {
    val text = CarHub.alert
    val last = remember { arrayOf("") }
    if (text != null) last[0] = text
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Row(
            Modifier
                .padding(top = 16.dp)
                .pressable { CarHub.dismissAlert() }
                .glass(RoundedCornerShape(50), tint = Ui.Redline, strength = 3.2f)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = Ui.Redline, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(last[0], fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text, maxLines = 1)
        }
    }
}
