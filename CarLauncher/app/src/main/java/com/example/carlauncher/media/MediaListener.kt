package com.example.carlauncher.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

/** Serviço vazio: existe só para o Android liberar a leitura da música que está tocando. */
class MediaListener : NotificationListenerService()

data class NowPlaying(
    val title: String?, val artist: String?, val playing: Boolean,
    val pkg: String?, val art: ImageBitmap?
)

object MediaInfo {
    /** Música atual. Fica null enquanto o usuário não autorizar o acesso. */
    var now by mutableStateOf<NowPlaying?>(null); private set

    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Confere qual app está tocando. A troca de música e de capa chega sozinha, pelo callback. */
    fun refresh(context: Context) {
        if (!hasAccess(context)) {
            now = null
            return
        }
        val first = try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.getActiveSessions(ComponentName(context, MediaListener::class.java)).firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (first?.sessionToken != controller?.sessionToken) {
            try {
                controller?.unregisterCallback(callback)
            } catch (_: Exception) {
            }
            controller = first
            try {
                first?.registerCallback(callback)
            } catch (_: Exception) {
            }
            publish()
        } else if (now == null) {
            publish()
        }
    }

    private fun publish() {
        val c = controller
        now = if (c == null) {
            NowPlaying(null, null, false, null, null)
        } else {
            try {
                val meta = c.metadata
                val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                NowPlaying(
                    title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                    pkg = c.packageName,
                    art = art?.asImageBitmap()
                )
            } catch (_: Exception) {
                NowPlaying(null, null, false, null, null)
            }
        }
    }

    /** Envia um botão de mídia (tocar/pausar, próxima, anterior) para o app que estiver tocando. */
    fun key(context: Context, code: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    fun openAccessSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
        }
    }
}
