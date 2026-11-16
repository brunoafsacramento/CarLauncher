package com.example.carlauncher.apps

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Abre o app de navegação padrão (Waze, Maps...) já procurando o endereço. */
fun navigateTo(context: Context, address: String) {
    try {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun openVoiceAssistant(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun shareText(context: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Enviar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}
