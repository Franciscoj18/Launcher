package com.calma.launcher.island

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.calma.launcher.data.Prefs

/** Lo que la isla puede mostrar de forma continua, en orden de prioridad. */
enum class Kind { CALL, NAV, TIMER, RIDE, PROGRESS }

class IslandItem(
    val key: String,
    val kind: Kind,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val smallIcon: Icon?,
    val largeIcon: Icon?,
    /** 0..1, -1 = sin barra, -2 = sin porcentaje (indeterminado). */
    val progress: Float,
    /** Hora base del cronómetro (0 = no hay). */
    val chronoBase: Long,
    val countDown: Boolean,
    val intent: PendingIntent?,
    val time: Long,
)

enum class AlertKind { CHARGING, SOUND, HEADPHONES, CODE, MESSAGE, EVENT }

class IslandAlert(
    val kind: AlertKind,
    val title: String,
    val text: String,
    val code: String? = null,
    val smallIcon: Icon? = null,
    val largeIcon: Icon? = null,
    val intent: PendingIntent? = null,
    val pkg: String = "",
    val durationMs: Long = 3500,
    val level: Int = -1,
    val id: Long = System.nanoTime(),
)

/** Estado compartido de la isla dinámica. */
object Island {
    const val ALL_FEATURES = "music,charge,code,nav,ride,progress,privacy,timer,call,sound,phones,event"

    var items by mutableStateOf<List<IslandItem>>(emptyList())
        internal set
    var alert by mutableStateOf<IslandAlert?>(null)
        private set
    var camera by mutableStateOf(false)
        internal set
    var mic by mutableStateOf(false)
        internal set
    var landscape by mutableStateOf(false)
        internal set
    var panelOpen by mutableStateOf(false)
        internal set
    /** Sube cuando tocas fuera de la isla (para encogerla). */
    var outsideTaps by mutableIntStateOf(0)
        internal set

    private val main = Handler(Looper.getMainLooper())
    private var hideTask: Runnable? = null
    private val labels = HashMap<String, String>()
    private var lastCode = ""
    private var lastCodeTime = 0L
    private val lastMessage = HashMap<String, String>()

    fun has(feature: String): Boolean = Prefs.islandEnabled && feature in Prefs.islandFeatures.split(",")

    fun show(a: IslandAlert) {
        main.post {
            alert = a
            hideTask?.let { main.removeCallbacks(it) }
            val r = Runnable { if (alert?.id == a.id) alert = null }
            hideTask = r
            main.postDelayed(r, a.durationMs)
        }
    }

    fun dismissAlert() {
        hideTask?.let { main.removeCallbacks(it) }
        alert = null
    }

    private fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
    }

    private val clockPkgs = setOf(
        "com.google.android.deskclock", "com.android.deskclock", "com.transsion.deskclock",
        "com.motorola.timeweatherwidget", "com.sec.android.app.clockpackage", "com.coloros.alarmclock",
        "com.oneplus.deskclock", "com.miui.clock",
    )

    private fun isRide(pkg: String): Boolean {
        val p = pkg.lowercase()
        return listOf("uber", "didi", "rappi", "indriver", "cabify").any { p.contains(it) }
    }

    /** Revisa todas las notificaciones activas y arma la lista de actividades en vivo. */
    fun classify(ctx: Context, list: Array<StatusBarNotification>) {
        val out = ArrayList<IslandItem>()
        for (sbn in list) {
            val pkg = sbn.packageName
            if (pkg == ctx.packageName) continue
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val ex = n.extras ?: continue
            if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) continue
            val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
            val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
            val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val pCur = ex.getInt(Notification.EXTRA_PROGRESS, 0)
            val indet = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)

            val kind = when {
                n.category == Notification.CATEGORY_CALL && ongoing -> Kind.CALL
                ongoing && (n.category == Notification.CATEGORY_NAVIGATION || pkg == "com.google.android.apps.maps" ||
                    pkg == "com.waze") -> Kind.NAV
                (pkg in clockPkgs || countDown) && chrono -> Kind.TIMER
                ongoing && isRide(pkg) -> Kind.RIDE
                ongoing && (pMax > 0 || indet) -> Kind.PROGRESS
                else -> null
            } ?: continue

            val feature = when (kind) {
                Kind.CALL -> "call"
                Kind.NAV -> "nav"
                Kind.TIMER -> "timer"
                Kind.RIDE -> "ride"
                Kind.PROGRESS -> "progress"
            }
            if (!has(feature)) continue

            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))
                ?.toString().orEmpty()
            val progress = when {
                indet -> -2f
                pMax > 0 -> (pCur.toFloat() / pMax).coerceIn(0f, 1f)
                else -> -1f
            }
            out.add(
                IslandItem(
                    key = sbn.key,
                    kind = kind,
                    pkg = pkg,
                    app = label(ctx, pkg),
                    title = title,
                    text = text,
                    smallIcon = n.smallIcon,
                    largeIcon = n.getLargeIcon(),
                    progress = progress,
                    chronoBase = if (chrono || kind == Kind.CALL) n.`when` else 0L,
                    countDown = countDown,
                    intent = n.contentIntent,
                    time = sbn.postTime,
                )
            )
        }
        val sorted = out.sortedWith(compareBy<IslandItem>({ it.kind.ordinal }, { -it.time }))
        main.post { items = sorted }
    }

    private val codeWords = Regex(
        "(c[oó]digo|code|verificaci[oó]n|verification|verify|otp|\\bpin\\b|clave|contrase[ñn]a|token|passcode)",
        RegexOption.IGNORE_CASE,
    )
    private val codeDigits = Regex("(?<![\\d$.,:/])(\\d{4,8})(?![\\d.,:%/])")
    private val codeSplit = Regex("(?<![\\d$.,])(\\d{3})[ -](\\d{3})(?![\\d.,%])")

    /** Para avisos al momento: códigos de verificación y mensajes (si están activados). */
    fun onPosted(ctx: Context, sbn: StatusBarNotification) {
        if (!Prefs.islandEnabled) return
        val pkg = sbn.packageName
        if (pkg == ctx.packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val all = "$title $text $big"

        if (has("code") && codeWords.containsMatchIn(all)) {
            val code = codeSplit.find(all)?.let { it.groupValues[1] + it.groupValues[2] }
                ?: codeDigits.find(all)?.groupValues?.get(1)
            if (code != null) {
                val now = System.currentTimeMillis()
                if (code != lastCode || now - lastCodeTime > 120_000) {
                    lastCode = code
                    lastCodeTime = now
                    show(
                        IslandAlert(
                            kind = AlertKind.CODE,
                            title = label(ctx, pkg),
                            text = title,
                            code = code,
                            smallIcon = n.smallIcon,
                            intent = n.contentIntent,
                            pkg = pkg,
                            durationMs = 20_000,
                        )
                    )
                }
                return
            }
        }

        if (has("msg") && sbn.isClearable &&
            (n.category == Notification.CATEGORY_MESSAGE || ex.containsKey(Notification.EXTRA_MESSAGES))
        ) {
            val sig = "$title|$text"
            if (lastMessage[sbn.key] == sig || text.isBlank()) return
            lastMessage[sbn.key] = sig
            if (lastMessage.size > 50) lastMessage.clear()
            show(
                IslandAlert(
                    kind = AlertKind.MESSAGE,
                    title = title,
                    text = text,
                    smallIcon = n.smallIcon,
                    largeIcon = n.getLargeIcon(),
                    intent = n.contentIntent,
                    pkg = pkg,
                    durationMs = 4500,
                )
            )
        }
    }

    fun locked(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
}
