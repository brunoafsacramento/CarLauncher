package com.example.carlauncher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Canal de comunicação com o adaptador. Para usar cabo USB depois, basta criar outra implementação. */
interface ObdTransport {
    val input: InputStream
    val output: OutputStream
    suspend fun connect()
    fun close()
}

/** ELM327 Bluetooth clássico (perfil serial SPP). */
class BluetoothTransport(private val device: BluetoothDevice) : ObdTransport {
    private var socket: BluetoothSocket? = null

    override val input: InputStream
        get() = socket?.inputStream ?: throw IOException("Não conectado")
    override val output: OutputStream
        get() = socket?.outputStream ?: throw IOException("Não conectado")

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
        }
    }

    override fun close() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
