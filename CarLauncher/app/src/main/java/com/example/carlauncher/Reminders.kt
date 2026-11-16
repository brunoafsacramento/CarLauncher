package com.example.carlauncher

import java.util.Calendar

/** Lembretes de documentos do carro. Cada data é guardada como ano * 12 + mês (0 = desligado). */
object Reminders {
    private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    val items: List<Pair<String, Pref<Int>>>
        get() = listOf("IPVA" to Prefs.ipva, "Licenciamento" to Prefs.licensing, "Seguro" to Prefs.insurance)

    fun nowIndex(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    fun label(index: Int): String =
        if (index <= 0) "Desligado" else "${months[index % 12]} de ${index / 12}"

    /** Avisos para mostrar na tela inicial: vencidos, vencendo neste mês ou no próximo. */
    fun due(): List<String> {
        val now = nowIndex()
        return items.mapNotNull { (name, pref) ->
            val at = pref.value
            when {
                at <= 0 || at > now + 1 -> null
                at < now -> "$name vencido"
                at == now -> "$name vence este mês"
                else -> "$name vence mês que vem"
            }
        }
    }
}
