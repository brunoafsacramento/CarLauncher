package com.example.carlauncher.car

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.carlauncher.MainActivity

/**
 * Mantém o app vivo enquanto o carro está conectado, para os alertas e o diário de viagem
 * continuarem funcionando com outro app na tela (Waze, YouTube Music...).
 */
class CarService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel("car", "Conexão com o carro", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, "car")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Car Launcher")
                .setContentText("Lendo os dados do carro")
                .setOngoing(true)
                .setContentIntent(open)
                .build()
            startForeground(1, notification)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
