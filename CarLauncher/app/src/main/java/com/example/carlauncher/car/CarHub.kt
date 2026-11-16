package com.example.carlauncher.car

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.carlauncher.Prefs
import com.example.carlauncher.obd.BluetoothTransport
import com.example.carlauncher.obd.CarLink
import com.example.carlauncher.obd.Dtc
import com.example.carlauncher.obd.ElmClient
import com.example.carlauncher.obd.LiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Central do carro: mantém a conexão, calcula viagem e consumo, dispara alertas e guarda o histórico.
 * Vive fora das telas, então continua trabalhando com outro app aberto.
 */
object CarHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var app: Context? = null
    private var stats: SharedPreferences? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // ---------- Estado observado pelas telas ----------
    var status by mutableStateOf("Sem conexão com o carro"); private set
    var connected by mutableStateOf(false); private set
    var active by mutableStateOf(false); private set
    var live by mutableStateOf(LiveData()); private set
    var dtcs by mutableStateOf<List<Dtc>?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var alert by mutableStateOf<String?>(null); private set
    var wakeUps by mutableIntStateOf(0); private set
    var trip by mutableStateOf(Trip()); private set
    var trips by mutableStateOf<List<TripRecord>>(emptyList()); private set
    var history by mutableStateOf<List<DtcSeen>>(emptyList()); private set
    var odometerKm by mutableDoubleStateOf(0.0); private set
    var oilAtKm by mutableDoubleStateOf(0.0); private set
    var lastWash by mutableLongStateOf(0L); private set
    var recent by mutableStateOf<List<LiveData>>(emptyList()); private set
    var crankVolts by mutableFloatStateOf(0f); private set
    var lastSprint by mutableFloatStateOf(0f); private set
    var bestSprint by mutableFloatStateOf(0f); private set
    var chargingProblem by mutableStateOf(false); private set

    private var link: CarLink? = null
    private var job: Job? = null
    private var alertJob: Job? = null
    private val known = HashSet<String>()

    // ---------- Apoio aos cálculos ----------
    private var tripOpen = false
    private var tripStart = 0L
    private var accelWindow = 0f
    private var accelRefSpeed = 0
    private var wasHot = false
    private var lowVoltSeconds = 0f
    private var sprintRunning = false
    private var sprintArmed = false
    private var sprintStart = 0L
    private var engineOffSeen = false
    private var crankMin = Float.MAX_VALUE
    private var sinceSave = 0f
    private var sampleClock = 0f

    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        val s = c.getSharedPreferences("stats", Context.MODE_PRIVATE)
        stats = s
        // A quilometragem é guardada em dupla precisão: com valores altos, um Float perderia os metros somados
        odometerKm = if (s.contains("odometerBits")) Double.fromBits(s.getLong("odometerBits", 0L))
        else s.getFloat("odometer", 0f).toDouble()
        oilAtKm = if (s.contains("oilAtBits")) Double.fromBits(s.getLong("oilAtBits", 0L))
        else s.getFloat("oilAt", 0f).toDouble()
        lastWash = s.getLong("lastWash", 0L)
        crankVolts = s.getFloat("crank", 0f)
        bestSprint = s.getFloat("bestSprint", 0f)
        trips = parseTrips(s.getString("trips", null))
        history = parseHistory(s.getString("history", null))
        try {
            tts = TextToSpeech(c) { result ->
                ttsReady = result == TextToSpeech.SUCCESS
                if (ttsReady) {
                    try {
                        tts?.setLanguage(Locale("pt", "BR"))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------- Conexão ----------

    /** Conecta ao adaptador; com device nulo, usa o carro simulado. Se cair, tenta de novo a cada 5 s. */
    fun connect(device: BluetoothDevice?) {
        disconnect()
        active = true
        if (device != null) startService()
        job = scope.launch {
            while (isActive) {
                val client: CarLink = if (device == null) DemoLink() else ElmClient(BluetoothTransport(device))
                try {
                    status = "Conectando…"
                    client.connect()
                    link = client
                    connected = true
                    status = if (device == null) "Modo demonstração: carro simulado" else "Conectado: ${client.protocol}"
                    wakeUps++
                    beginTrip()
                    refreshDtcs(client, firstCheck = true)
                    var lastNanos = System.nanoTime()
                    var sinceCheck = 0f
                    while (isActive) {
                        val raw = client.readLive()
                        val now = System.nanoTime()
                        val dt = ((now - lastNanos) / 1e9f).coerceIn(0f, 2f)
                        lastNanos = now
                        val data = raw.copy(fuelRate = fuelRate(raw))
                        live = data
                        onSample(data, dt)
                        sinceCheck += dt
                        if (sinceCheck > 120f) {
                            sinceCheck = 0f
                            refreshDtcs(client, firstCheck = false)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = "Sem resposta: ${e.message ?: e.javaClass.simpleName}. Tentando de novo…"
                } finally {
                    client.close()
                    link = null
                    connected = false
                    live = LiveData()
                    recent = emptyList()
                    endTrip()
                }
                delay(5000)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        active = false
        status = "Sem conexão com o carro"
        stopService()
    }

    private fun startService() {
        val c = app ?: return
        try {
            c.startService(Intent(c, CarService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun stopService() {
        val c = app ?: return
        try {
            c.stopService(Intent(c, CarService::class.java))
        } catch (_: Exception) {
        }
    }

    // ---------- Erros ----------

    fun readDtcs() = op {
        val c = link ?: error("Conecte ao carro primeiro")
        val found = c.readDtcs()
        known.addAll(found.map { it.code })
        dtcs = found
        remember(found)
    }

    fun clearDtcs() = op {
        val c = link ?: error("Conecte ao carro primeiro")
        c.clearDtcs()
        val found = c.readDtcs()
        known.clear()
        known.addAll(found.map { it.code })
        dtcs = found
        message = "Erros apagados"
    }

    /** Consulta os erros sozinho: ao conectar e depois a cada dois minutos, para avisar antes da luz acender. */
    private suspend fun refreshDtcs(client: CarLink, firstCheck: Boolean) {
        val found = try {
            client.readDtcs()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val fresh = found.filter { it.code !in known }
        known.addAll(found.map { it.code })
        dtcs = found
        remember(found)
        if (!Prefs.alerts.value || found.isEmpty()) return
        if (firstCheck) {
            raise(if (found.size == 1) "O carro tem 1 erro registrado" else "O carro tem ${found.size} erros registrados")
        } else if (fresh.isNotEmpty()) {
            raise("Novo erro registrado: " + fresh.joinToString(", ") { it.code })
        }
    }

    private fun remember(found: List<Dtc>) {
        if (found.isEmpty()) return
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, DtcSeen>()
        history.forEach { map[it.code] = it }
        found.forEach { d ->
            val old = map[d.code]
            map[d.code] = if (old == null) DtcSeen(d.code, now, now) else old.copy(last = now)
        }
        history = map.values.sortedByDescending { it.last }.take(40)
        saveStats()
    }

    private fun op(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Falha na comunicação"
            } finally {
                busy = false
            }
        }
    }

    // ---------- Consumo ----------

    /**
     * Consumo estimado em L/h. Usa o sensor de fluxo de ar quando o carro informa; senão,
     * calcula o ar pela pressão do coletor (motor 1.8, enchimento de 85%).
     */
    private fun fuelRate(d: LiveData): Float? {
        val rpm = d.rpm ?: return null
        if (rpm < 300) return 0f
        val air = d.maf ?: run {
            val map = d.map ?: return null
            val kelvin = (d.intakeTemp ?: 30) + 273.15f
            rpm * map / kelvin * (0.85f * 1.8f * 28.97f) / (120f * 8.314f)
        }
        val ethanol = Prefs.ethanol.value
        val airPerFuel = if (ethanol) 9.0f else 13.2f      // proporção ar/combustível
        val gramsPerLiter = if (ethanol) 789f else 745f
        return air / airPerFuel / gramsPerLiter * 3600f
    }

    val fuelPrice: Float
        get() = if (Prefs.ethanol.value) Prefs.priceEth.value else Prefs.priceGas.value

    // ---------- A cada leitura ----------

    private fun onSample(d: LiveData, dt: Float) {
        val speed = d.speed ?: 0
        val rpm = d.rpm ?: 0
        val km = speed * dt / 3600f
        val liters = (d.fuelRate ?: 0f) * dt / 3600f
        val idle = rpm > 300 && speed == 0

        // Manobras bruscas: variação de velocidade medida em janelas de 1 s
        var harsh = 0
        accelWindow += dt
        if (accelWindow >= 1f) {
            if (abs(speed - accelRefSpeed) / accelWindow > 11f) harsh = 1
            accelRefSpeed = speed
            accelWindow = 0f
        }

        val t = trip
        trip = t.copy(
            seconds = t.seconds + dt,
            km = t.km + km,
            liters = t.liters + liters,
            idleSeconds = t.idleSeconds + if (idle) dt else 0f,
            idleLiters = t.idleLiters + if (idle) liters else 0f,
            maxCoolant = max(t.maxCoolant, d.coolant ?: 0),
            maxSpeed = max(t.maxSpeed, speed),
            harsh = t.harsh + harsh,
            highRevSeconds = t.highRevSeconds + if (rpm > 4500) dt else 0f,
        )
        odometerKm += km

        // A cada duas horas de viagem, sugere uma pausa
        if ((t.seconds / 7200f).toInt() < (trip.seconds / 7200f).toInt() && Prefs.alerts.value) {
            raise("Duas horas de direção. Hora de fazer uma pausa.")
        }

        // Guarda uma leitura por segundo (dois minutos) para os gráficos
        sampleClock += dt
        if (sampleClock >= 1f) {
            sampleClock = 0f
            recent = (recent + d).takeLast(120)
        }

        // Cronômetro de 0 a 100 km/h
        val nowMs = System.currentTimeMillis()
        if (speed == 0) {
            sprintArmed = true
            sprintRunning = false
        } else if (sprintArmed && !sprintRunning) {
            sprintArmed = false
            sprintRunning = true
            sprintStart = nowMs
        }
        if (sprintRunning && speed >= 100) {
            sprintRunning = false
            val seconds = (nowMs - sprintStart) / 1000f
            lastSprint = seconds
            if (bestSprint == 0f || seconds < bestSprint) bestSprint = seconds
            saveStats()
        }
        if (sprintRunning && nowMs - sprintStart > 45_000) sprintRunning = false

        // Menor tensão durante a partida: indica a saúde da bateria
        val volts = d.voltage
        if (rpm == 0 && volts != null) {
            engineOffSeen = true
            crankMin = min(crankMin, volts)
        } else if (rpm > 500 && engineOffSeen) {
            engineOffSeen = false
            if (crankMin < 13f) {
                crankVolts = crankMin
                saveStats()
            }
            crankMin = Float.MAX_VALUE
        }

        checkAlerts(d, dt)

        sinceSave += dt
        if (sinceSave > 60f) {
            sinceSave = 0f
            saveStats()
        }
    }

    private fun checkAlerts(d: LiveData, dt: Float) {
        val coolant = d.coolant ?: 0
        if (coolant >= 105 && !wasHot) {
            wasHot = true
            if (Prefs.alerts.value) raise("Motor quente: $coolant graus")
        } else if (coolant < 100) {
            wasHot = false
        }

        val volts = d.voltage ?: return
        if ((d.rpm ?: 0) > 1000 && volts < 12.8f) {
            lowVoltSeconds += dt
            if (lowVoltSeconds > 15f && !chargingProblem) {
                chargingProblem = true
                if (Prefs.alerts.value) raise("A bateria não está carregando")
            }
        } else if (volts >= 13.2f) {
            lowVoltSeconds = 0f
            chargingProblem = false
        }
    }

    // ---------- Alertas ----------

    private fun raise(text: String) {
        alert = text
        if (Prefs.voice.value && ttsReady) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "alert")
            } catch (_: Exception) {
            }
        }
        alertJob?.cancel()
        alertJob = scope.launch {
            delay(9000)
            alert = null
        }
    }

    fun dismissAlert() {
        alertJob?.cancel()
        alert = null
    }

    // ---------- Viagem e manutenção ----------

    private fun beginTrip() {
        trip = Trip()
        tripOpen = true
        tripStart = System.currentTimeMillis()
        accelWindow = 0f
        accelRefSpeed = 0
        sprintArmed = false
        sprintRunning = false
    }

    private fun endTrip() {
        if (!tripOpen) return
        tripOpen = false
        val t = trip
        if (t.km >= 0.3f) {
            val record = TripRecord(
                date = tripStart,
                km = t.km,
                minutes = (t.seconds / 60f).toInt(),
                kmPerLiter = t.kmPerLiter ?: 0f,
                cost = t.liters * fuelPrice,
                score = t.score
            )
            trips = (listOf(record) + trips).take(200)
        }
        saveStats()
    }

    val oilRemainingKm: Float
        get() = (Prefs.oilInterval.value - (odometerKm - oilAtKm)).toFloat()

    fun oilChanged() {
        oilAtKm = odometerKm
        saveStats()
    }

    /** Acerta a quilometragem com a do painel, sem mexer na contagem da troca de óleo. */
    fun setOdometer(km: Double) {
        oilAtKm += km - odometerKm
        odometerKm = km
        saveStats()
    }

    fun washed() {
        lastWash = System.currentTimeMillis()
        saveStats()
    }

    /** Dias desde a última lavagem, ou null se nunca foi marcada. */
    val daysSinceWash: Int?
        get() = if (lastWash == 0L) null else ((System.currentTimeMillis() - lastWash) / 86_400_000L).toInt()

    /** De 0 a 1: o carro do app começa a empoeirar depois de uma semana sem lavar. */
    val dust: Float
        get() = (((daysSinceWash ?: 0) - 7) / 23f).coerceIn(0f, 1f)

    /** Quilômetros, gasto e número de viagens do mês atual. */
    fun monthTotals(): Triple<Float, Float, Int> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val month = trips.filter { it.date >= start }
        return Triple(month.sumOf { it.km.toDouble() }.toFloat(), month.sumOf { it.cost.toDouble() }.toFloat(), month.size)
    }

    /** Nota de 0 a 100 que resume erros, bateria e manutenção. */
    val health: Int
        get() {
            val list = dtcs.orEmpty()
            var score = 100
            score -= (list.count { !it.pending } * 25).coerceAtMost(60)
            score -= (list.count { it.pending } * 8).coerceAtMost(24)
            if (crankVolts > 0f && crankVolts < 9.6f) score -= 10
            if (chargingProblem) score -= 15
            if (oilRemainingKm < 0f) score -= 15
            return score.coerceIn(0, 100)
        }

    /** Texto para mandar ao mecânico. */
    fun report(): String {
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val d = live
        val sb = StringBuilder()
        sb.appendLine("Relatório do carro, $date")
        sb.appendLine("Saúde geral: $health de 100")
        sb.appendLine()
        val list = dtcs
        when {
            list == null -> sb.appendLine("Erros: ainda não consultados")
            list.isEmpty() -> sb.appendLine("Erros: nenhum registrado")
            else -> {
                sb.appendLine("Erros registrados:")
                list.forEach {
                    sb.appendLine("- ${it.code} (${if (it.pending) "pendente" else "confirmado"}): ${it.description}")
                }
            }
        }
        if (history.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Histórico de erros:")
            val short = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            history.forEach { sb.appendLine("- ${it.code}, visto pela última vez em ${short.format(Date(it.last))}") }
        }
        if (connected) {
            sb.appendLine()
            sb.appendLine("Leituras de agora:")
            sb.appendLine("- Rotação: ${d.rpm ?: "--"} rpm")
            sb.appendLine("- Temperatura do motor: ${d.coolant ?: "--"} °C")
            sb.appendLine("- Correção de mistura imediata: ${d.shortTrim?.let { "%.1f".format(it) } ?: "--"} %")
            sb.appendLine("- Correção de mistura aprendida: ${d.longTrim?.let { "%.1f".format(it) } ?: "--"} %")
            sb.appendLine("- Tensão: ${d.voltage?.let { "%.1f".format(it) } ?: "--"} V")
        }
        if (crankVolts > 0f) {
            sb.appendLine()
            sb.appendLine("Menor tensão na última partida: ${"%.1f".format(crankVolts)} V")
        }
        return sb.toString()
    }

    // ---------- Gravação ----------

    private fun saveStats() {
        val s = stats ?: return
        s.edit()
            .putLong("odometerBits", odometerKm.toRawBits())
            .putLong("oilAtBits", oilAtKm.toRawBits())
            .putLong("lastWash", lastWash)
            .putFloat("crank", crankVolts)
            .putFloat("bestSprint", bestSprint)
            .putString("trips", tripsJson())
            .putString("history", historyJson())
            .apply()
    }

    private fun tripsJson(): String {
        val array = JSONArray()
        trips.forEach {
            array.put(
                JSONObject()
                    .put("d", it.date)
                    .put("km", it.km.toDouble())
                    .put("min", it.minutes)
                    .put("kml", it.kmPerLiter.toDouble())
                    .put("cost", it.cost.toDouble())
                    .put("score", it.score)
            )
        }
        return array.toString()
    }

    private fun parseTrips(text: String?): List<TripRecord> = try {
        val array = JSONArray(text ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TripRecord(
                o.getLong("d"), o.getDouble("km").toFloat(), o.getInt("min"),
                o.getDouble("kml").toFloat(), o.getDouble("cost").toFloat(), o.getInt("score")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun historyJson(): String {
        val array = JSONArray()
        history.forEach { array.put(JSONObject().put("c", it.code).put("f", it.first).put("l", it.last)) }
        return array.toString()
    }

    private fun parseHistory(text: String?): List<DtcSeen> = try {
        val array = JSONArray(text ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DtcSeen(o.getString("c"), o.getLong("f"), o.getLong("l"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}
