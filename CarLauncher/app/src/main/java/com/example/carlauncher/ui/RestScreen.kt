package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.car.CarHub
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tela de descanso: relógio grande e o carro, em tela quase toda preta. Qualquer toque volta ao launcher. */
@Composable
fun RestScreen(onWake: () -> Unit) {
    val locale = remember { Locale("pt", "BR") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", locale) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d 'de' MMMM", locale) }
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xF5020308))
            .pressable(onClick = onWake)
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(timeFormat.format(now), fontSize = 110.sp, fontWeight = FontWeight.Light, color = Ui.Text, maxLines = 1)
            Text(
                dateFormat.format(now).replaceFirstChar { it.uppercase() },
                fontSize = 20.sp, color = Ui.TextDim, maxLines = 1
            )
            if (CarHub.connected) {
                val d = CarHub.live
                Text(
                    "${d.speed ?: "--"} km/h, motor a ${d.coolant ?: "--"} °C",
                    fontSize = 20.sp, color = Ui.Ice, maxLines = 1,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            CarPhoto(CarViews.night, Modifier.fillMaxSize().padding(vertical = 30.dp))
        }
    }
}
