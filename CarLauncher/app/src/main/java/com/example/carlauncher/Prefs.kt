package com.example.carlauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** Uma preferência que a tela observa: mudar o valor redesenha quem usa e já salva no aparelho. */
class Pref<T>(
    private val key: String,
    private val default: T,
    private val read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
    private val state = mutableStateOf(default)

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            Prefs.save { write(it, key, v) }
        }

    fun load(p: SharedPreferences) {
        state.value = read(p, key, default)
    }
}

object Prefs {
    private var prefs: SharedPreferences? = null
    private val all = ArrayList<Pref<*>>()

    private fun bool(key: String, def: Boolean): Pref<Boolean> =
        Pref(key, def, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) }).also { all.add(it) }

    private fun int(key: String, def: Int): Pref<Int> =
        Pref(key, def, { p, k, d -> p.getInt(k, d) }, { e, k, v -> e.putInt(k, v) }).also { all.add(it) }

    private fun float(key: String, def: Float): Pref<Float> =
        Pref(key, def, { p, k, d -> p.getFloat(k, d) }, { e, k, v -> e.putFloat(k, v) }).also { all.add(it) }

    private fun text(key: String, def: String): Pref<String> =
        Pref(key, def, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) }).also { all.add(it) }

    // Geral
    val demo = bool("demo", false)
    val reducedEffects = bool("reducedEffects", false)
    val accent = int("accent", 0)
    val carColor = int("carColor", 0)

    // Alertas
    val alerts = bool("alerts", true)
    val voice = bool("voice", true)
    val shiftRpm = int("shiftRpm", 5500) // 0 = desligada

    // Combustível
    val ethanol = bool("ethanol", false)
    val priceGas = float("priceGas", 6.00f)
    val priceEth = float("priceEth", 4.20f)

    // Destinos e música
    val home = text("home", "")
    val work = text("work", "")
    val autoplay = bool("autoplay", false)

    // Manutenção
    val oilInterval = int("oilInterval", 10000)

    // Lembretes de documentos: ano * 12 + mês (0 = desligado)
    val ipva = int("ipva", 0)
    val licensing = int("licensing", 0)
    val insurance = int("insurance", 0)

    // Tela de descanso (minutos sem toque; 0 = desligada) e mostrador flutuante
    val restMinutes = int("restMinutes", 5)
    val bubble = bool("bubble", false)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        all.forEach { it.load(p) }
    }

    fun save(block: (SharedPreferences.Editor) -> Unit) {
        val editor = prefs?.edit() ?: return
        block(editor)
        editor.apply()
    }
}
