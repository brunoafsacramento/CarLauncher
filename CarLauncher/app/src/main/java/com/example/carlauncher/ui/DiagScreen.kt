package com.example.carlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.apps.shareText
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.car.zoneOf
import com.example.carlauncher.obd.DtcDescriptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saúde do carro: nota geral, raio-x apontando onde está a falha e a lista de erros. */
@Composable
fun DiagScreen() {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val list = CarHub.dtcs
    val faults = remember(list) { list.orEmpty().mapNotNull { zoneOf(it.code) }.toSet() }
    val health = CarHub.health
    var xray by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Esquerda: nota e carro em raio-x
        Column(
            Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val color = when {
                    health >= 80 -> Ui.Ice
                    health >= 50 -> Ui.Amber
                    else -> Ui.Redline
                }
                Box(
                    Modifier.size(66.dp).glass(CircleShape, tint = color, strength = 2f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$health", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
                ScreenTitle(
                    "Saúde do carro",
                    when {
                        list == null -> "Conecte ao carro para avaliar"
                        health >= 80 -> "Tudo em ordem"
                        health >= 50 -> "Precisa de atenção"
                        else -> "Leve ao mecânico"
                    }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (xray) {
                    CarHero(Modifier.fillMaxSize(), xray = true, faults = faults)
                } else {
                    CarGallery(Modifier.fillMaxSize(), start = CarViews.indexFor(faults), faults = faults)
                }
                GlassButton(
                    if (xray) "Ver a foto" else "Ver por dentro",
                    Modifier.align(Alignment.TopEnd)
                ) { xray = !xray }
            }
            Text(
                if (faults.isEmpty()) "Nenhuma região com falha apontada"
                else "Verifique: " + faults.joinToString(", ") { it.label },
                fontSize = 14.sp,
                color = if (faults.isEmpty()) Ui.TextDim else Ui.Redline,
                maxLines = 2,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        // Direita: erros
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Ler erros", enabled = CarHub.connected && !CarHub.busy) { CarHub.readDtcs() }
                GlassButton("Apagar erros", enabled = CarHub.connected && !CarHub.busy) { confirm = true }
                GlassButton("Enviar relatório") { shareText(context, CarHub.report()) }
            }
            Text(
                CarHub.message ?: when {
                    CarHub.busy -> "Consultando a central…"
                    !CarHub.connected -> "Conecte ao carro na aba Motor para ler os erros"
                    list == null -> "Toque em “Ler erros” para consultar a central"
                    list.isEmpty() -> "Nenhum erro registrado"
                    else -> "O app confere os erros sozinho a cada dois minutos"
                },
                fontSize = 13.sp, color = Ui.TextDim, maxLines = 2,
                modifier = Modifier.padding(start = 6.dp)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list.orEmpty()) { dtc ->
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            dtc.code, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = if (dtc.pending) Ui.Amber else Ui.Redline
                        )
                        Column(Modifier.weight(1f)) {
                            Text(dtc.description, color = Ui.Text, fontSize = 14.sp)
                            Text(
                                (if (dtc.pending) "Pendente" else "Confirmado") +
                                    (zoneOf(dtc.code)?.let { ", na região de ${it.label}" } ?: ""),
                                color = Ui.TextDim, fontSize = 12.sp
                            )
                        }
                    }
                }
                if (CarHub.history.isNotEmpty()) {
                    item {
                        Text(
                            "Histórico", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = Ui.Text, modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                        )
                    }
                    items(CarHub.history) { seen ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(seen.code, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ui.Text)
                            Text(
                                DtcDescriptions.of(seen.code), Modifier.weight(1f),
                                fontSize = 13.sp, color = Ui.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(dateFormat.format(Date(seen.last)), fontSize = 13.sp, color = Ui.TextDim)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Apagar os erros?") },
            text = { Text("Deixe a ignição ligada e o motor desligado. Se o defeito for real, o erro volta. O histórico do app continua guardado.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    CarHub.clearDtcs()
                }) { Text("Apagar erros") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}
