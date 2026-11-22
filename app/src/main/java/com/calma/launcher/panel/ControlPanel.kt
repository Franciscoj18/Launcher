package com.calma.launcher.panel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calma.launcher.SettingsActivity
import com.calma.launcher.data.Controls
import com.calma.launcher.data.Dates
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.lock.Live
import com.calma.launcher.ui.Accent
import com.calma.launcher.ui.CardFill
import com.calma.launcher.ui.MusicCard
import com.calma.launcher.ui.NotifCard
import com.calma.launcher.ui.PanelSurface
import com.calma.launcher.ui.Poppins
import kotlinx.coroutines.delay

private class PanelButton(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val on: Boolean,
    val onClick: () -> Unit,
    val onLong: (() -> Unit)? = null,
)

/** El panel de controles de Calma (el mismo en el inicio y sobre cualquier app). */
@Composable
fun ControlPanel(host: PanelHost) {
    val ctx = host.hostContext
    val es = Prefs.lang == "es"
    val density = LocalDensity.current
    val closePx = with(density) { 80.dp.toPx() }

    // Estados que se vuelven a leer cada segundo mientras el panel está abierto
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        Controls.prepare(ctx)
        DeviceInfo.refreshQuick(ctx)
        while (true) {
            delay(1000)
            tick++
            now = System.currentTimeMillis()
        }
    }
    val d = remember(now / 60_000L, Prefs.lang, Prefs.use24h) { Dates(now, Prefs.lang, Prefs.use24h) }
    val wifi = remember(tick) { Controls.wifi(ctx) }
    val data = remember(tick) { Controls.mobileData(ctx) }
    val bt = remember(tick) { Controls.bluetooth(ctx) }
    val rotation = remember(tick) { Controls.rotation(ctx) }
    val dnd = remember(tick) { Controls.dnd(ctx) }
    val autoBright = remember(tick) { Controls.autoBrightness(ctx) }
    var bright by remember { mutableFloatStateOf(Controls.brightness(ctx)) }
    var volume by remember { mutableFloatStateOf(Controls.volume(ctx)) }
    var draggingVolume by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { if (!draggingVolume) volume = Controls.volume(ctx) }

    fun needWrite() {
        Toast.makeText(ctx, if (es) "Permite a Calma \"Modificar ajustes del sistema\"" else "Allow Calma to modify system settings", Toast.LENGTH_LONG).show()
        host.launch(Controls.writeSettingsIntent(ctx))
    }

    fun needService() {
        Toast.makeText(ctx, if (es) "Activa el panel de Calma en Accesibilidad" else "Turn on Calma's panel in Accessibility", Toast.LENGTH_LONG).show()
        host.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    val all = listOf(
        PanelButton("wifi", "Wi‑Fi", Icons.Filled.Wifi, wifi,
            onClick = { host.launch(Intent(Settings.Panel.ACTION_WIFI)) },
            onLong = { host.launch(Intent(Settings.ACTION_WIFI_SETTINGS)) }),
        PanelButton("data", if (es) "Datos" else "Data", Icons.Filled.NetworkCell, data,
            onClick = { host.launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            onLong = { host.launch(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)) }),
        PanelButton("bt", "Bluetooth", Icons.Filled.Bluetooth, bt,
            onClick = { host.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }),
        PanelButton("torch", if (es) "Linterna" else "Flashlight", Icons.Filled.FlashlightOn, Controls.torch,
            onClick = { Controls.toggleTorch(ctx) }),
        PanelButton("rotate", if (es) "Rotación" else "Rotation", Icons.Filled.ScreenRotation, rotation,
            onClick = { if (!Controls.setRotation(ctx, !rotation)) needWrite() else tick++ }),
        PanelButton("dnd", if (es) "No molestar" else "Do not disturb", Icons.Filled.DoNotDisturbOn, dnd,
            onClick = {
                if (!Controls.toggleDnd(ctx)) host.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                else tick++
            }),
        PanelButton("shot", if (es) "Captura" else "Screenshot", Icons.Filled.Screenshot, false,
            onClick = { if (!host.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) needService() }),
        PanelButton("lock", if (es) "Bloquear" else "Lock", Icons.Filled.Lock, false,
            onClick = { if (!host.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) needService() }),
    )
    val enabled = Prefs.panelButtons.split(",").toSet()
    val buttons = all.filter { it.key in enabled }

    val blurred = HomeWallpaper.blurred
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -closePx) host.closePanel() },
                    onVerticalDrag = { change, dy -> total += dy; change.consume() },
                )
            }
    ) {
        if (blurred != null) {
            val img = remember(blurred) { blurred.asImageBitmap() }
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(PanelSurface.copy(alpha = if (blurred != null) 0.72f else 0.96f)))

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 20.dp)
        ) {
            // Hora, fecha, batería y clima
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(d.time, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp)
                        if (d.amPm.isNotEmpty()) {
                            Text(" ${d.amPm.lowercase()}", color = Color.White.copy(alpha = 0.8f), fontFamily = Poppins, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Text(
                        if (es) "${d.weekday} ${d.day} de ${d.month}" else "${d.weekday}, ${d.month} ${d.day}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontFamily = Poppins,
                        fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (DeviceInfo.battery >= 0) {
                        Text(
                            "${DeviceInfo.battery}%" + if (DeviceInfo.charging) " ⚡" else "",
                            color = Color.White,
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                    if (Prefs.hasWeather) {
                        Text(Weather.short(Prefs.lang), color = Color.White.copy(alpha = 0.75f), fontFamily = Poppins, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardFill)
                        .clickable { host.launch(Intent(ctx, SettingsActivity::class.java)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = if (es) "Ajustes de Calma" else "Calma settings", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Botones redondos
            Spacer(Modifier.height(18.dp))
            buttons.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    row.forEach { b -> ToggleButton(b, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Brillo
            SliderRow(
                icon = Icons.Filled.WbSunny,
                value = bright,
                onChange = { v ->
                    if (Controls.setBrightness(ctx, v)) bright = v
                },
                onStart = { if (!Controls.canWrite(ctx)) needWrite() },
                trailing = {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (autoBright) Accent else Color.White.copy(alpha = 0.12f))
                            .clickable {
                                if (!Controls.setAutoBrightness(ctx, !autoBright)) needWrite() else tick++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.BrightnessAuto, contentDescription = if (es) "Brillo automático" else "Auto brightness", tint = if (autoBright) PanelSurface else Color.White, modifier = Modifier.size(17.dp))
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            // Volumen
            SliderRow(
                icon = Icons.Filled.VolumeUp,
                value = volume,
                onChange = { v -> draggingVolume = true; volume = v; Controls.setVolume(ctx, v) },
                onStart = {},
                onEnd = { draggingVolume = false },
            )

            // Música
            if (Live.hasMedia) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MusicCard(onOpen = { host.launchPending(Live.playerIntent(), Live.playerPackage()) })
                }
            }

            // Notificaciones
            Spacer(Modifier.height(14.dp))
            val items = Live.items
            if (!Live.hasAccess(ctx)) {
                SmallNote(if (es) "Da acceso a notificaciones en Ajustes de Calma para verlas aquí." else "Give Calma notification access to see them here.")
            } else if (host.locked) {
                SmallNote(if (es) "Desbloquea el teléfono para ver tus notificaciones." else "Unlock your phone to see notifications.")
            } else if (items.isEmpty()) {
                SmallNote(if (es) "Sin notificaciones" else "No notifications")
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (es) "Notificaciones" else "Notifications", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (es) "Borrar todo" else "Clear all",
                        color = Accent,
                        fontFamily = Poppins,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { try { Live.service?.cancelAllNotifications() } catch (_: Exception) {} }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.key }) { n ->
                        NotifCard(
                            n, es,
                            onOpen = {
                                if (n.autoCancel) Live.dismiss(n.key)
                                host.launchPending(n.intent, n.pkg)
                            },
                            onDismiss = { Live.dismiss(n.key) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            // Abajo: panel del sistema y asa para cerrar
            Text(
                if (es) "Abrir el panel de Motorola" else "Open system panel",
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = Poppins,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { host.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .clickable { host.closePanel() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleButton(b: PanelButton, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = b.onClick, onLongClick = b.onLong)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (b.on) Accent else Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(b.icon, contentDescription = b.label, tint = if (b.on) PanelSurface else Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            b.label,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = Poppins,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    value: Float,
    onChange: (Float) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    var started by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardFill)
            .padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Slider(
            value = value,
            onValueChange = {
                if (!started) { started = true; onStart() }
                onChange(it)
            },
            onValueChangeFinished = { started = false; onEnd() },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
private fun SmallNote(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.6f),
        fontFamily = Poppins,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
