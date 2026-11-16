package com.example.carlauncher.car

import kotlin.math.roundToInt

/** Regiões do carro onde uma falha pode ser apontada no desenho. */
enum class Zone(val label: String) {
    Engine("ignição e motor"),
    Intake("admissão de ar"),
    Fuel("combustível e tanque"),
    Exhaust("escapamento e sondas"),
    Cooling("arrefecimento"),
    Electrical("bateria e parte elétrica"),
    Transmission("câmbio"),
}

/** Diz em que região do carro fica a peça ligada ao código. Retorna null quando não há lugar definido. */
fun zoneOf(code: String): Zone? {
    if (code.length != 5 || code[0] != 'P') return null
    val n = code.substring(1).toIntOrNull(16) ?: return null
    return when (n) {
        in 0x0100..0x0114 -> Zone.Intake
        in 0x0115..0x0119, 0x0125, 0x0128 -> Zone.Cooling
        in 0x0120..0x0124 -> Zone.Intake
        in 0x0130..0x0167 -> Zone.Exhaust
        in 0x0170..0x0175 -> Zone.Fuel
        in 0x0200..0x0299 -> Zone.Fuel
        in 0x0300..0x0399 -> Zone.Engine
        in 0x0400..0x0439 -> Zone.Exhaust
        in 0x0440..0x0459 -> Zone.Fuel
        in 0x0496..0x0499 -> Zone.Fuel
        in 0x0500..0x0509 -> Zone.Intake
        in 0x0560..0x0563 -> Zone.Electrical
        in 0x0620..0x0629 -> Zone.Electrical
        in 0x0700..0x0999 -> Zone.Transmission
        else -> null
    }
}

/** Números da viagem em andamento. */
data class Trip(
    val seconds: Float = 0f,
    val km: Float = 0f,
    val liters: Float = 0f,
    val idleSeconds: Float = 0f,
    val idleLiters: Float = 0f,
    val maxCoolant: Int = 0,
    val maxSpeed: Int = 0,
    val harsh: Int = 0,             // acelerações e freadas bruscas
    val highRevSeconds: Float = 0f, // tempo acima de 4500 rpm
) {
    val kmPerLiter: Float?
        get() = if (liters > 0.02f && km > 0.1f) km / liters else null

    /** Nota de condução de 0 a 100: perde pontos por manobras bruscas e por giro alto. */
    val score: Int
        get() {
            val highRevShare = if (seconds > 1f) highRevSeconds / seconds else 0f
            return (100f - harsh * 4f - highRevShare * 200f).roundToInt().coerceIn(0, 100)
        }
}

data class TripRecord(
    val date: Long, val km: Float, val minutes: Int,
    val kmPerLiter: Float, val cost: Float, val score: Int
)

data class DtcSeen(val code: String, val first: Long, val last: Long)
