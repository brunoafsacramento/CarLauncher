package com.example.carlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.Prefs
import com.example.carlauncher.car.CarHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Computador de bordo, diário de viagens e ferramentas do dia a dia. */
@Composable
fun TripScreen() {
    val t = CarHub.trip
    val d = CarHub.live
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Esquerda: viagem atual e anteriores
        Column(
            Modifier.weight(1.3f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenTitle(
                "Viagem atual",
                if (CarHub.connected) "Consumo estimado pelos sensores do motor"
                else "Conecte ao carro para registrar a viagem"
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Consumo agora", instantConsumption(d), Modifier.weight(1f))
                Stat("Média", t.kmPerLiter?.let { "${it.fmt()} km/l" }, Modifier.weight(1f))
                Stat("Distância", "${t.km.fmt()} km", Modifier.weight(1f))
                Stat("Tempo", "${(t.seconds / 60f).toInt()} min", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Custo", "R$ ${(t.liters * CarHub.fuelPrice).fmt(2)}", Modifier.weight(1f))
                Stat("Gasto parado", "${t.idleLiters.fmt(2)} L", Modifier.weight(1f))
                Stat("Nota de condução", "${t.score} de 100", Modifier.weight(1f))
                Stat(
                    "0 a 100 km/h",
                    if (CarHub.lastSprint > 0f) "${CarHub.lastSprint.fmt()} s" else null,
                    Modifier.weight(1f)
                )
            }
            Text(
                "Viagens anteriores", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = Ui.Text, modifier = Modifier.padding(start = 6.dp, top = 4.dp)
            )
            if (CarHub.trips.isEmpty()) {
                Text(
                    "As viagens aparecem aqui quando o carro é desligado.",
                    fontSize = 13.sp, color = Ui.TextDim, modifier = Modifier.padding(start = 6.dp)
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CarHub.trips) { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(dateFormat.format(Date(r.date)), Modifier.weight(1.2f), fontSize = 13.sp, color = Ui.TextDim)
                        Text("${r.km.fmt()} km", Modifier.weight(1f), fontSize = 13.sp, color = Ui.Text)
                        Text("${r.minutes} min", Modifier.weight(0.8f), fontSize = 13.sp, color = Ui.Text)
                        Text(
                            if (r.kmPerLiter > 0f) "${r.kmPerLiter.fmt()} km/l" else "--",
                            Modifier.weight(1f), fontSize = 13.sp, color = Ui.Text
                        )
                        Text("R$ ${r.cost.fmt(2)}", Modifier.weight(1f), fontSize = 13.sp, color = Ui.Text)
                        Text("nota ${r.score}", Modifier.weight(0.9f), fontSize = 13.sp, color = Ui.TextDim)
                    }
                }
            }
        }

        // Direita: ferramentas
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonthCard()
            FuelCalculator()
            OilCard()
            WashCard()
            BatteryCard()
        }
    }
}

/** Etanol ou gasolina: o etanol compensa quando custa até 70% do preço da gasolina. */
@Composable
private fun FuelCalculator() {
    val gas = Prefs.priceGas.value
    val eth = Prefs.priceEth.value
    val ratio = if (gas > 0f) eth / gas else 1f
    val ethanolWins = ratio <= 0.70f
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Etanol ou gasolina", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        Stepper(
            "Gasolina", "R$ ${gas.fmt(2)}",
            onMinus = { Prefs.priceGas.value = (gas - 0.05f).coerceAtLeast(0.05f) },
            onPlus = { Prefs.priceGas.value = gas + 0.05f }
        )
        Stepper(
            "Etanol", "R$ ${eth.fmt(2)}",
            onMinus = { Prefs.priceEth.value = (eth - 0.05f).coerceAtLeast(0.05f) },
            onPlus = { Prefs.priceEth.value = eth + 0.05f }
        )
        Text(
            if (ethanolWins) "Abasteça com etanol" else "Abasteça com gasolina",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ui.Ice
        )
        Text(
            "O etanol está custando ${(ratio * 100f).roundToInt()}% da gasolina. Ele compensa até 70%.",
            fontSize = 13.sp, color = Ui.TextDim
        )
        ChoiceRow(
            "No tanque agora", listOf("Gasolina", "Etanol"),
            selected = if (Prefs.ethanol.value) 1 else 0,
            onSelect = { Prefs.ethanol.value = it == 1 }
        )
    }
}

@Composable
private fun OilCard() {
    val remaining = CarHub.oilRemainingKm
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Troca de óleo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        Text(
            if (remaining >= 0f) "Faltam ${remaining.roundToInt()} km" else "Passou ${(-remaining).roundToInt()} km do prazo",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (remaining >= 500f) Ui.Text else Ui.Redline
        )
        Text(
            "Conta os quilômetros rodados com o app conectado: ${CarHub.odometerKm.roundToInt()} km até agora.",
            fontSize = 13.sp, color = Ui.TextDim
        )
        GlassButton("Troquei o óleo") { CarHub.oilChanged() }
    }
}

/** Resumo do mês, somando as viagens registradas. */
@Composable
private fun MonthCard() {
    val (km, cost, count) = CarHub.monthTotals()
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Este mês", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Rodou", "${km.fmt()} km", Modifier.weight(1f))
            Stat("Gastou", "R$ ${cost.fmt(2)}", Modifier.weight(1f))
            Stat("Viagens", "$count", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WashCard() {
    val days = CarHub.daysSinceWash
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lavagem", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        Text(
            when {
                days == null -> "Marque quando lavar o carro"
                days == 0 -> "Lavado hoje"
                days == 1 -> "Lavado ontem"
                else -> "Lavado há $days dias"
            },
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (days != null && days >= 21) Ui.Amber else Ui.Text
        )
        Text(
            "Depois de uma semana, o carro do app começa a ficar empoeirado.",
            fontSize = 13.sp, color = Ui.TextDim
        )
        GlassButton("Lavei o carro") { CarHub.washed() }
    }
}

@Composable
private fun BatteryCard() {
    val volts = CarHub.crankVolts
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bateria na partida", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Text)
        if (volts <= 0f) {
            Text(
                "Conecte o app com a ignição ligada e depois dê a partida para medir.",
                fontSize = 13.sp, color = Ui.TextDim
            )
        } else {
            Text(
                "${volts.fmt()} V na última partida",
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = if (volts >= 9.6f) Ui.Text else Ui.Redline
            )
            Text(
                if (volts >= 9.6f) "A bateria segurou bem a partida." else "Queda forte: a bateria pode estar fraca.",
                fontSize = 13.sp, color = Ui.TextDim
            )
        }
        if (CarHub.bestSprint > 0f) {
            Text("Melhor 0 a 100 km/h: ${CarHub.bestSprint.fmt()} s", fontSize = 13.sp, color = Ui.TextDim)
        }
    }
}
