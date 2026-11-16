package com.example.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.example.carlauncher.car.Bubble
import com.example.carlauncher.car.CarHub
import com.example.carlauncher.media.MediaInfo
import com.example.carlauncher.ui.AlertBanner
import com.example.carlauncher.ui.AppsScreen
import com.example.carlauncher.ui.DiagScreen
import com.example.carlauncher.ui.GlassRail
import com.example.carlauncher.ui.HomeScreen
import com.example.carlauncher.ui.ObdScreen
import com.example.carlauncher.ui.RestScreen
import com.example.carlauncher.ui.SettingsScreen
import com.example.carlauncher.ui.ShiftBar
import com.example.carlauncher.ui.SmokeBackground
import com.example.carlauncher.ui.TripScreen
import com.example.carlauncher.ui.Ui

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        CarHub.init(this)

        // Apertar o botão Home com o launcher aberto volta para a aba Início
        addOnNewIntentListener { vm.tab = 0 }

        // Retoma a música ao ligar a central, se o usuário pediu
        if (savedInstanceState == null && Prefs.autoplay.value) {
            Handler(Looper.getMainLooper()).postDelayed(
                { MediaInfo.key(this, KeyEvent.KEYCODE_MEDIA_PLAY) }, 4000
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ui.Ice,
                    surface = Color(0xFF141A27),
                    onSurface = Ui.Text
                )
            ) {
                CompositionLocalProvider(LocalContentColor provides Ui.Text) {
                    // Tela de descanso: aparece depois de um tempo sem ninguém tocar na tela
                    val lastTouch = remember { longArrayOf(System.currentTimeMillis()) }
                    var resting by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(5000)
                            val minutes = Prefs.restMinutes.value
                            val idle = System.currentTimeMillis() - lastTouch[0]
                            if (minutes > 0 && !resting && idle > minutes * 60_000L) resting = true
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    lastTouch[0] = System.currentTimeMillis()
                                }
                            }
                        }
                    ) {
                        SmokeBackground()
                        LauncherRoot(vm)
                        ShiftBar(Modifier.align(Alignment.TopCenter))
                        AlertBanner(Modifier.align(Alignment.TopCenter))
                        AnimatedVisibility(visible = resting, enter = fadeIn(tween(600)), exit = fadeOut(tween(300))) {
                            RestScreen(onWake = {
                                lastTouch[0] = System.currentTimeMillis()
                                resting = false
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.loadApps() // atualiza a lista caso algum app tenha sido instalado ou removido
    }

    // O mostrador flutuante só aparece enquanto o launcher está fora da tela
    override fun onStart() {
        super.onStart()
        Bubble.hide(this)
    }

    override fun onStop() {
        super.onStop()
        Bubble.show(this)
    }
}

private fun bluetoothAdapter(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
fun LauncherRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("obd", Context.MODE_PRIVATE) }
    var showDevices by remember { mutableStateOf(false) }

    // Um launcher não fecha com o botão voltar: ele retorna para a aba Início
    BackHandler { vm.tab = 0 }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showDevices = true }

    // Ao abrir: liga o carro simulado, ou reconecta sozinho ao último adaptador usado
    LaunchedEffect(Unit) {
        if (!CarHub.active) {
            if (Prefs.demo.value) {
                CarHub.connect(null)
            } else {
                val address = prefs.getString("address", null)
                val adapter = bluetoothAdapter(context)
                if (address != null && adapter != null && hasBluetoothPermission(context)) {
                    CarHub.connect(adapter.getRemoteDevice(address))
                }
            }
        }
    }

    val onConnectClick: () -> Unit = {
        if (CarHub.active) {
            Prefs.demo.value = false
            CarHub.disconnect()
        } else if (hasBluetoothPermission(context)) {
            showDevices = true
        } else {
            askPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Row(Modifier.fillMaxSize()) {
        GlassRail(tab = vm.tab, onTab = { vm.tab = it })
        AnimatedContent(
            targetState = vm.tab,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 14 }) togetherWith fadeOut(tween(140))
            },
            label = "page"
        ) { page ->
            when (page) {
                0 -> HomeScreen(vm, onOpenTab = { vm.tab = it })
                1 -> AppsScreen(vm)
                2 -> ObdScreen(onConnectClick)
                3 -> DiagScreen()
                4 -> TripScreen()
                else -> SettingsScreen()
            }
        }
    }

    if (showDevices) {
        val devices = remember {
            if (hasBluetoothPermission(context))
                bluetoothAdapter(context)?.bondedDevices?.toList().orEmpty()
            else emptyList()
        }
        DeviceDialog(
            devices = devices,
            onPick = { device ->
                prefs.edit().putString("address", device.address).apply()
                showDevices = false
                Prefs.demo.value = false
                CarHub.connect(device)
            },
            onDismiss = { showDevices = false }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceDialog(devices: List<BluetoothDevice>, onPick: (BluetoothDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolha o adaptador OBD") },
        text = {
            if (devices.isEmpty()) {
                Text("Nenhum dispositivo pareado. Pareie o ELM327 nas configurações de Bluetooth da central (PIN 1234 ou 0000) e volte aqui.")
            } else {
                LazyColumn {
                    items(devices) { device ->
                        Text(
                            "${device.name ?: "Sem nome"}\n${device.address}",
                            Modifier.fillMaxWidth().clickable { onPick(device) }.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
