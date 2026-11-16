package com.example.carlauncher.obd

/** Qualquer fonte de dados do carro: o ELM327 de verdade ou o carro simulado. */
interface CarLink {
    val protocol: String
    suspend fun connect()
    suspend fun readLive(): LiveData
    suspend fun readDtcs(): List<Dtc>
    suspend fun clearDtcs()
    fun close()
}
