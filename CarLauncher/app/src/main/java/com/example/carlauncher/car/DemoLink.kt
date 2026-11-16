package com.example.carlauncher.car

import com.example.carlauncher.obd.CarLink
import com.example.carlauncher.obd.Dtc
import com.example.carlauncher.obd.LiveData
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sin

/** Carro simulado: repete um trajeto de um minuto, para testar o app sem estar no carro. */
class DemoLink : CarLink {
    override val protocol = "demonstração"
    private val start = System.currentTimeMillis()
    private var codes = listOf(Dtc("P0171", false), Dtc("P0135", true))

    override suspend fun connect() {
        delay(1200)
    }

    override suspend fun readLive(): LiveData {
        delay(110)
        val t = (System.currentTimeMillis() - start) / 1000f
        val c = t % 60f
        val speed = when {
            c < 6f -> 0f
            c < 18f -> (c - 6f) / 12f * 70f
            c < 30f -> 70f
            c < 40f -> 70f + (c - 30f) / 10f * 40f
            c < 50f -> 110f
            else -> 110f * (60f - c) / 10f
        }
        val accelerating = (c >= 6f && c < 18f) || (c >= 30f && c < 40f)
        val inGear = (speed % 30f) / 30f
        val rpm = when {
            speed < 1f -> 780f + 25f * sin(t * 3f)
            accelerating -> 1900f + inGear * 4300f
            else -> 1600f + inGear * 1400f
        }
        val throttle = when {
            speed < 1f -> 0f
            accelerating -> 62f
            c >= 50f -> 0f
            else -> 18f
        }
        val load = if (speed < 1f) 22f else if (accelerating) 78f else 35f
        val map = if (speed < 1f) 32f else if (accelerating) 88f else 48f
        return LiveData(
            rpm = rpm.toInt(),
            speed = speed.toInt(),
            coolant = min(91f, 28f + t * 1.1f).toInt(),
            intakeTemp = 34,
            load = load.toInt(),
            throttle = throttle.toInt(),
            map = map.toInt(),
            shortTrim = 2.5f * sin(t),
            longTrim = 4.7f,
            voltage = 14.1f + 0.1f * sin(t / 2f),
        )
    }

    override suspend fun readDtcs(): List<Dtc> {
        delay(600)
        return codes
    }

    override suspend fun clearDtcs() {
        delay(800)
        codes = emptyList()
    }

    override fun close() {}
}
