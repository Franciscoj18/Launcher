package com.calma.launcher.lock

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import com.calma.launcher.data.Colors

class NotifEntry(val pkg: String, val icon: Icon?, val count: Int)

/** Lo que está sonando y las notificaciones pendientes (para el tinte y la pantalla de bloqueo). */
object Live {
    var musicColor by mutableStateOf<Color?>(null)
        internal set
    var playing by mutableStateOf(false)
        internal set
    var notifs by mutableStateOf<List<NotifEntry>>(emptyList())
        internal set

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}

/**
 * Servicio de "acceso a notificaciones". Solo lee qué apps tienen avisos y
 * la portada de la música; no guarda ni envía nada.
 */
class CalmaListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var lastArt: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pickController(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMusic()
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateMusic()
        override fun onSessionDestroyed() {
            controller = null
            Live.playing = false
            Live.musicColor = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            msm = m
            val me = ComponentName(this, CalmaListener::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            pickController(m.getActiveSessions(me))
        } catch (_: Exception) {}
        refreshNotifs()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        try { controller?.unregisterCallback(callback) } catch (_: Exception) {}
        controller = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshNotifs()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshNotifs()

    private fun pickController(list: List<MediaController>) {
        val playingOne = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (playingOne?.sessionToken == controller?.sessionToken && controller != null) {
            updateMusic(); return
        }
        try { controller?.unregisterCallback(callback) } catch (_: Exception) {}
        controller = playingOne
        try { playingOne?.registerCallback(callback, main) } catch (_: Exception) {}
        updateMusic()
    }

    private fun updateMusic() {
        val c = controller
        if (c == null) {
            Live.playing = false
            Live.musicColor = null
            return
        }
        Live.playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val md = c.metadata
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== lastArt) {
            lastArt = art
            Live.musicColor = art?.let { bmp -> Colors.vivid(bmp)?.let { Color(Colors.readable(it)) } }
        }
    }

    private fun refreshNotifs() {
        val list: Array<StatusBarNotification> = try { activeNotifications ?: emptyArray() } catch (e: Exception) { emptyArray() }
        val byPkg = LinkedHashMap<String, MutableList<StatusBarNotification>>()
        for (sbn in list) {
            if (!sbn.isClearable || sbn.packageName == packageName) continue
            val n = sbn.notification
            if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) continue
            byPkg.getOrPut(sbn.packageName) { mutableListOf() }.add(sbn)
        }
        val entries = byPkg.map { (pkg, items) ->
            NotifEntry(pkg, items.last().notification.smallIcon, items.size)
        }.take(6)
        main.post { Live.notifs = entries }
    }
}
