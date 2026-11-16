package com.example.carlauncher.obd

/** Pequena tabela inicial. Amplie conforme precisar. */
object DtcDescriptions {
    private val map = mapOf(
        "P0101" to "Sensor de fluxo de ar (MAF): faixa/desempenho",
        "P0107" to "Sensor MAP: sinal baixo",
        "P0108" to "Sensor MAP: sinal alto",
        "P0113" to "Sensor de temperatura do ar: sinal alto",
        "P0117" to "Sensor de temperatura do motor: sinal baixo",
        "P0118" to "Sensor de temperatura do motor: sinal alto",
        "P0122" to "Sensor de posição da borboleta: sinal baixo",
        "P0131" to "Sonda lambda (antes do catalisador): tensão baixa",
        "P0135" to "Sonda lambda (antes do catalisador): circuito de aquecimento",
        "P0141" to "Sonda lambda (após o catalisador): circuito de aquecimento",
        "P0171" to "Mistura pobre (banco 1)",
        "P0172" to "Mistura rica (banco 1)",
        "P0300" to "Falhas de ignição aleatórias",
        "P0301" to "Falha de ignição no cilindro 1",
        "P0302" to "Falha de ignição no cilindro 2",
        "P0303" to "Falha de ignição no cilindro 3",
        "P0304" to "Falha de ignição no cilindro 4",
        "P0325" to "Sensor de detonação: circuito",
        "P0335" to "Sensor de rotação (virabrequim): circuito",
        "P0340" to "Sensor de fase (comando): circuito",
        "P0420" to "Eficiência do catalisador abaixo do limite",
        "P0497" to "Sistema de emissões evaporativas: fluxo de purga baixo",
        "P0505" to "Controle de marcha lenta: falha",
        "P0562" to "Tensão do sistema baixa",
        "P0700" to "Falha no sistema de controle da transmissão",
    )

    fun of(code: String): String = map[code] ?: "Descrição não cadastrada"
}
