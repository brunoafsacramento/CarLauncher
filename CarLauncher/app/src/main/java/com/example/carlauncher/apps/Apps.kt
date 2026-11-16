package com.example.carlauncher.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap)

object AppRepo {
    /** Apps que entram sozinhos na tela inicial quando estão instalados. */
    private val preferred = listOf(
        "com.google.android.apps.youtube.music",
        "com.spotify.music",
        "com.waze",
        "com.google.android.apps.maps",
        "com.google.android.youtube",
        "com.google.android.dialer",
    )

    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    pkg = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun defaultFavorites(apps: List<AppInfo>): List<String> {
        val radio = apps.firstOrNull {
            val l = it.label.lowercase()
            l.contains("rádio") || l.contains("radio") || l == "fm"
        }
        val known = preferred.filter { p -> apps.any { it.pkg == p } }
        return (listOfNotNull(radio?.pkg) + known).distinct().take(8)
    }
}

fun launchApp(context: Context, pkg: String) {
    try {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
    } catch (_: Exception) {
    }
}
