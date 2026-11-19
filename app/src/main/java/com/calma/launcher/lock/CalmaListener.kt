package com.calma.launcher.lock

import android.app.Notification
import android.app.PendingIntent
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

/** Una notificación con su contenido, para la lista de la pantalla de bloqueo. */
class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val smallIcon: Icon?,
    val largeIcon: Icon?,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/** Lo que está sonando y las notificaciones pendientes (para el tinte y la pantalla de bloqueo). */
object Live {
    var musicColor by mutableStateOf<Color?>(null)
        internal set
    var playing by mutableStateOf(false)
        internal set
    var notifs by mutableStateOf<List<NotifEntry>>(emptyList())
        internal set
    var items by mutableStateOf<List<NotifItem>>(emptyList())
        internal set

    // Reproductor
    var hasMedia by mutableStateOf(false)
        internal set
    var title by mutableStateOf("")
        internal set
    var artist by mutableStateOf("")
        internal set
    var art by mutableStateOf<Bitmap?>(null)
        internal set
    internal var controller: MediaController? = null
    internal var service: CalmaListener? = null

    fun playPause() {
        val c = controller ?: return
        try { if (playing) c.transportControls.pause() else c.transportControls.play() } catch (_: Exception) {}
    }
    fun next() { try { controller?.transportControls?.skipToNext() } catch (_: Exception) {} }
    fun previous() { try { controller?.transportControls?.skipToPrevious() } catch (_: Exception) {} }
    fun playerIntent(): PendingIntent? = try { controller?.sessionActivity } catch (e: Exception) { null }
    fun playerPackage(): String = controller?.packageName ?: ""

    /** Quita una notificación (como deslizarla en el panel). */
    fun dismiss(key: String) {
        try { service?.cancelNotification(key) } catch (_: Exception) {}
    }

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
            Live.controller = null
            Live.playing = false
            Live.hasMedia = false
            Live.musicColor = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Live.service = this
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
        Live.controller = null
        Live.service = null
        Live.hasMedia = false
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
        Live.controller = playingOne
        try { playingOne?.registerCallback(callback, main) } catch (_: Exception) {}
        updateMusic()
    }

    private fun updateMusic() {
        val c = controller
        if (c == null) {
            Live.playing = false
            Live.hasMedia = false
            Live.musicColor = null
            return
        }
        Live.playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val md = c.metadata
        val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        Live.title = t ?: ""
        Live.artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val st = c.playbackState?.state
        Live.hasMedia = !t.isNullOrBlank() && st != null && st != PlaybackState.STATE_NONE &&
            st != PlaybackState.STATE_STOPPED && st != PlaybackState.STATE_ERROR
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== lastArt) {
            lastArt = art
            Live.art = art
            Live.musicColor = art?.let { bmp -> Colors.vivid(bmp)?.let { Color(Colors.readable(it)) } }
        }
    }

    private fun refreshNotifs() {
        val list: Array<StatusBarNotification> = try { activeNotifications ?: emptyArray() } catch (e: Exception) { emptyArray() }
        val byPkg = LinkedHashMap<String, MutableList<StatusBarNotification>>()
        val details = ArrayList<NotifItem>()
        val labels = HashMap<String, String>()
        for (sbn in list) {
            if (!sbn.isClearable || sbn.packageName == packageName) continue
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            byPkg.getOrPut(sbn.packageName) { mutableListOf() }.add(sbn)

            val ex = n.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ex?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: ""
            val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: ex?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ex?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: ""
            val app = labels.getOrPut(sbn.packageName) {
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (e: Exception) { sbn.packageName }
            }
            details.add(
                NotifItem(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    app = app,
                    title = title,
                    text = text,
                    time = sbn.postTime,
                    smallIcon = n.smallIcon,
                    largeIcon = n.getLargeIcon(),
                    intent = n.contentIntent,
                    autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
                )
            )
        }
        val entries = byPkg.map { (pkg, items) ->
            NotifEntry(pkg, items.last().notification.smallIcon, items.size)
        }.take(6)
        val sorted = details.sortedByDescending { it.time }.take(20)
        main.post {
            Live.notifs = entries
            Live.items = sorted
        }
    }
}
