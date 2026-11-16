package com.example.carlauncher.car

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.example.carlauncher.MainActivity
import com.example.carlauncher.Prefs

/**
 * Mostrador flutuante: velocidade e temperatura por cima de qualquer app.
 * Aparece quando o launcher sai da tela com o carro conectado; tocar nele volta ao launcher.
 */
object Bubble {
    private var view: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (!CarHub.active) {
                hide(v.context)
                return
            }
            val d = CarHub.live
            v.text = if (CarHub.connected) "${d.speed ?: "--"} km/h   ${d.coolant ?: "--"} °C" else "Carro sem conexão"
            handler.postDelayed(this, 500)
        }
    }

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (view != null || !Prefs.bubble.value || !CarHub.active || !canShow(context)) return
        val app = context.applicationContext
        try {
            val density = app.resources.displayMetrics.density
            val text = TextView(app).apply {
                setTextColor(0xFFF1F5FC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                val padH = (16 * density).toInt()
                val padV = (9 * density).toInt()
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    cornerRadius = 22 * density
                    setColor(0xD9101622.toInt())
                    setStroke((1 * density).toInt(), 0x66FFFFFF)
                }
                setOnClickListener {
                    try {
                        app.startActivity(
                            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }
            }
            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (14 * density).toInt()
                y = (14 * density).toInt()
            }
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(text, params)
            view = text
            handler.post(tick)
        } catch (_: Exception) {
            view = null
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        view = null
        handler.removeCallbacks(tick)
        try {
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }
}
