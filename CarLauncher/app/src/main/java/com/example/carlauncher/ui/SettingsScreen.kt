package com.example.carlauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.Prefs
import com.example.carlauncher.Reminders
import com.example.carlauncher.car.Bubble
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.media.MediaInfo
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Ajustes", "Tudo aqui é salvo na hora") }

        item {
            Section("Testar sem o carro") {
                ToggleRow(
                    "Modo demonstração",
                    "Simula um carro andando, com dois erros de exemplo",
                    Prefs.demo.value
                ) { on ->
                    Prefs.demo.value = on
                    if (on) CarHub.connect(null) else CarHub.disconnect()
                }
            }
        }

        item {
            Section("Aparência") {
                ChoiceRow("Cor de destaque", Ui.accentNames, Prefs.accent.value) { Prefs.accent.value = it }
                ChoiceRow("Cor do carro", Ui.carColorNames, Prefs.carColor.value) { Prefs.carColor.value = it }
                ToggleRow(
                    "Efeitos reduzidos",
                    "Para a fumaça e as animações contínuas, se a central ficar lenta",
                    Prefs.reducedEffects.value
                ) { Prefs.reducedEffects.value = it }
            }
        }

        item {
            Section("Alertas") {
                ToggleRow(
                    "Alertas do carro",
                    "Motor quente, bateria sem carregar e erro novo",
                    Prefs.alerts.value
                ) { Prefs.alerts.value = it }
                ToggleRow("Avisar por voz", "Fala o alerta em voz alta", Prefs.voice.value) { Prefs.voice.value = it }
                val shift = Prefs.shiftRpm.value
                Stepper(
                    "Luz de troca de marcha",
                    if (shift <= 0) "Desligada" else "$shift rpm",
                    onMinus = { Prefs.shiftRpm.value = if (shift <= 2500) 0 else shift - 250 },
                    onPlus = { Prefs.shiftRpm.value = if (shift <= 0) 2500 else (shift + 250).coerceAtMost(7000) }
                )
            }
        }

        item {
            Section("Tela") {
                val rest = Prefs.restMinutes.value
                Stepper(
                    "Tela de descanso",
                    if (rest <= 0) "Desligada" else "após $rest min",
                    onMinus = { Prefs.restMinutes.value = (rest - 1).coerceAtLeast(0) },
                    onPlus = { Prefs.restMinutes.value = (rest + 1).coerceAtMost(30) }
                )
                ToggleRow(
                    "Mostrador flutuante",
                    "Velocidade e temperatura por cima de outros apps, com o carro conectado",
                    Prefs.bubble.value
                ) { on ->
                    Prefs.bubble.value = on
                    if (on && !Bubble.canShow(context)) {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        item {
            Section("Lembretes de documentos") {
                val now = Reminders.nowIndex()
                for ((name, pref) in Reminders.items) {
                    val at = pref.value
                    Stepper(
                        name,
                        Reminders.label(at),
                        onMinus = { pref.value = if (at <= now) 0 else at - 1 },
                        onPlus = { pref.value = if (at <= 0) now else at + 1 }
                    )
                }
            }
        }

        item {
            Section("Destinos") {
                GlassField(Prefs.home.value, { Prefs.home.value = it }, "Endereço de casa", Modifier.fillMaxWidth())
                GlassField(Prefs.work.value, { Prefs.work.value = it }, "Endereço do trabalho", Modifier.fillMaxWidth())
            }
        }

        item {
            Section("Música") {
                ToggleRow(
                    "Tocar ao ligar a central",
                    "Retoma a última música quando o launcher abre",
                    Prefs.autoplay.value
                ) { Prefs.autoplay.value = it }
                GlassButton("Mostrar o nome da música") { MediaInfo.openAccessSettings(context) }
            }
        }

        item {
            Section("Manutenção") {
                val interval = Prefs.oilInterval.value
                Stepper(
                    "Troca de óleo a cada",
                    "$interval km",
                    onMinus = { Prefs.oilInterval.value = (interval - 1000).coerceAtLeast(3000) },
                    onPlus = { Prefs.oilInterval.value = (interval + 1000).coerceAtMost(20000) }
                )
                OdometerRow()
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Abrir ajustes do Android") {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

/** Acerta a quilometragem do app com a do painel do carro. */
@Composable
private fun OdometerRow() {
    var text by remember { mutableStateOf(CarHub.odometerKm.roundToInt().toString()) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Quilometragem do painel", Modifier.weight(1f), fontSize = 15.sp, color = Ui.Text)
        GlassField(
            text, { typed -> text = typed.filter { it.isDigit() }.take(7) },
            "km", Modifier.width(130.dp), numeric = true
        )
        GlassButton("Salvar") {
            val km = text.toDoubleOrNull()
            if (km != null) CarHub.setOdometer(km)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        content()
    }
}
