package com.example.carlauncher.obd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class LiveData(
    val rpm: Int? = null,
    val speed: Int? = null,
    val coolant: Int? = null,
    val intakeTemp: Int? = null,
    val load: Int? = null,
    val throttle: Int? = null,
    val map: Int? = null,          // pressão do coletor, kPa
    val maf: Float? = null,        // fluxo de ar, g/s
    val shortTrim: Float? = null,  // correção de mistura imediata, %
    val longTrim: Float? = null,   // correção de mistura aprendida, %
    val voltage: Float? = null,
    val fuelRate: Float? = null,   // consumo estimado, L/h (calculado pelo app)
)

data class Dtc(val code: String, val pending: Boolean) {
    val description: String get() = DtcDescriptions.of(code)
}

/** Fala o protocolo do ELM327: envia um comando e lê até o prompt '>'. */
class ElmClient(private val transport: ObdTransport) : CarLink {
    private val mutex = Mutex()
    private var isCan = true
    private var last = LiveData()
    private var slot = 0
    private val misses = HashMap<String, Int>()

    private var protocolName = ""
    override val protocol: String get() = protocolName

    override suspend fun connect() {
        transport.connect()
        send("ATZ", 8000)   // reset
        send("ATE0")        // sem eco
        send("ATL0")        // sem quebra de linha extra
        send("ATS0")        // sem espaços
        send("ATH0")        // sem cabeçalhos
        send("ATSP0")       // protocolo automático
        val first = send("0100", 20000) // força a busca do protocolo
        if (messages(first).none { it.startsWith("4100") })
            throw IOException("a central não respondeu; a ignição está ligada?")
        isCan = messages(send("ATDPN")).lastOrNull()?.lastOrNull()?.let { it in "6789ABC" } ?: true
        protocolName = send("ATDP").replace("\r", "").replace("\n", "").trim()
    }

    override fun close() = transport.close()

    /**
     * Rotação e velocidade são lidas em todo ciclo, para os mostradores ficarem fluidos.
     * Os outros sensores mudam devagar, então é lido um por ciclo, em rodízio.
     * Sensor que o carro não informa é abandonado depois de três tentativas.
     */
    override suspend fun readLive(): LiveData {
        var d = last.copy(
            rpm = bytes("0C", 2, optional = false)?.let { (it[0] * 256 + it[1]) / 4 },
            speed = bytes("0D", 1, optional = false)?.let { it[0] },
        )
        when (slot) {
            0 -> d = d.copy(coolant = bytes("05")?.let { it[0] - 40 } ?: d.coolant)
            1 -> d = d.copy(intakeTemp = bytes("0F")?.let { it[0] - 40 } ?: d.intakeTemp)
            2 -> d = d.copy(load = bytes("04")?.let { it[0] * 100 / 255 } ?: d.load)
            3 -> d = d.copy(throttle = bytes("11")?.let { it[0] * 100 / 255 } ?: d.throttle)
            4 -> d = d.copy(map = bytes("0B")?.let { it[0] } ?: d.map)
            5 -> d = d.copy(maf = bytes("10", 2)?.let { (it[0] * 256 + it[1]) / 100f } ?: d.maf)
            6 -> d = d.copy(shortTrim = bytes("06")?.let { (it[0] - 128) * 100f / 128f } ?: d.shortTrim)
            7 -> d = d.copy(longTrim = bytes("07")?.let { (it[0] - 128) * 100f / 128f } ?: d.longTrim)
        }
        // Com o motor desligado a tensão é lida sempre, para medir a queda na partida
        if (slot == 8 || d.rpm == 0) d = d.copy(voltage = readVoltage() ?: d.voltage)
        slot = (slot + 1) % 9
        last = d
        return d
    }

    /** Erros confirmados (modo 03) + pendentes (modo 07). */
    override suspend fun readDtcs(): List<Dtc> =
        (readMode("03", "43", false) + readMode("07", "47", true)).distinctBy { it.code }

    /** Apaga os erros e desliga a luz da injeção (modo 04). */
    override suspend fun clearDtcs() {
        if (messages(send("04", 10000)).none { it.startsWith("44") })
            throw IOException("A central não confirmou. Deixe a ignição ligada e o motor desligado.")
    }

    private suspend fun readMode(cmd: String, prefix: String, pending: Boolean): List<Dtc> =
        messages(send(cmd, 10000)).filter { it.startsWith(prefix) }.flatMap { msg ->
            // Em CAN, o byte após o prefixo é a quantidade de códigos
            val body = if (isCan) msg.drop(4) else msg.drop(2)
            body.chunked(4).filter { it.length == 4 && it != "0000" }.map { Dtc(decodeDtc(it), pending) }
        }

    private suspend fun readVoltage(): Float? =
        send("ATRV").filter { it.isDigit() || it == '.' }.toFloatOrNull()

    private suspend fun bytes(pid: String, count: Int = 1, optional: Boolean = true): List<Int>? {
        if (optional && (misses[pid] ?: 0) >= 3) return null
        val msg = messages(send("01$pid")).firstOrNull { it.startsWith("41$pid") }
        val data = msg?.drop(4)?.chunked(2)?.filter { it.length == 2 }?.map { it.toInt(16) }
        if (data == null || data.size < count) {
            misses[pid] = (misses[pid] ?: 0) + 1
            return null
        }
        misses[pid] = 0
        return data
    }

    private suspend fun send(cmd: String, timeoutMs: Long = 5000): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val inp = transport.input
            while (inp.available() > 0) inp.read() // descarta sobras
            transport.output.write("$cmd\r".toByteArray())
            transport.output.flush()
            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                if (inp.available() > 0) {
                    val c = inp.read()
                    if (c == -1) throw IOException("conexão encerrada")
                    if (c.toChar() == '>') break
                    sb.append(c.toChar())
                } else {
                    if (System.currentTimeMillis() > deadline) throw IOException("o adaptador não respondeu")
                    delay(10)
                }
            }
            sb.toString()
        }
    }

    companion object {
        private const val HEX = "0123456789ABCDEF"

        /** Extrai as respostas em hexadecimal, juntando respostas CAN de várias linhas ("0:", "1:"...). */
        fun messages(raw: String): List<String> {
            val out = mutableListOf<String>()
            var multi: StringBuilder? = null
            for (line in raw.split('\r', '\n')) {
                val l = line.replace(" ", "").uppercase()
                if (l.isEmpty()) continue
                if (l.length > 2 && l[1] == ':') {
                    if (l[0] == '0' || multi == null) {
                        multi?.let { out.add(it.toString()) }
                        multi = StringBuilder()
                    }
                    multi?.append(l.substring(2))
                } else if (l.length != 3 && l.all { it in HEX }) {
                    out.add(l)
                }
            }
            multi?.let { out.add(it.toString()) }
            return out
        }

        /** "0171" -> "P0171", "4123" -> "C0123" */
        fun decodeDtc(hex: String): String {
            val n = hex[0].digitToInt(16)
            return "PCBU"[n shr 2].toString() + (n and 3) + hex.substring(1)
        }
    }
}
