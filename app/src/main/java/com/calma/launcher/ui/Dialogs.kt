package com.calma.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.calma.launcher.MainActivity
import com.calma.launcher.SettingsActivity
import com.calma.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Menú al mantener presionado el inicio. */
@Composable
fun HomeMenu(activity: MainActivity, onDismiss: () -> Unit, onPickWidget: () -> Unit) {
    val hasWidget = activity.widgetInfo(Prefs.widgetId) != null
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF21C1C1F))
                .padding(vertical = 10.dp)
        ) {
            MenuRow(Icons.Filled.Wallpaper, "Fondo de pantalla") {
                onDismiss()
                openSafe(activity, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
            }
            MenuRow(Icons.Filled.Widgets, if (hasWidget) "Cambiar widget" else "Agregar widget") { onPickWidget() }
            if (hasWidget) {
                MenuRow(Icons.Filled.Tune, "Opciones del widget") { onDismiss(); activity.reconfigureWidget() }
                MenuRow(Icons.Filled.Delete, "Quitar widget") { onDismiss(); activity.removeWidget() }
            }
            MenuRow(Icons.Filled.Settings, "Ajustes de Calma") {
                onDismiss()
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontFamily = Poppins, fontSize = 15.sp)
    }
}

/** Lista de widgets instalados. */
@Composable
fun WidgetPicker(activity: MainActivity, onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val widgets = remember {
        activity.installedWidgets()
            .map { info ->
                val label = try { info.loadLabel(pm) } catch (e: Exception) { info.provider.className }
                val app = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                } catch (e: Exception) { info.provider.packageName }
                Triple(info, label, app)
            }
            // Tu widget de música primero
            .sortedWith(compareBy<Triple<AppWidgetProviderInfo, String, String>>({ if (it.first.provider.packageName == "com.miwidget.musica") 0 else 1 }, { it.third.lowercase() }))
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF21C1C1F))
                .padding(vertical = 14.dp)
        ) {
            Text(
                "Elige un widget",
                color = Color.White,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
            if (widgets.isEmpty()) {
                Text(
                    "No hay widgets instalados",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = Poppins,
                    modifier = Modifier.padding(22.dp),
                )
            }
            LazyColumn {
                items(widgets, key = { it.first.provider.flattenToString() }) { (info, label, app) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(info) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetIcon(info)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(label, color = Color.White, fontFamily = Poppins, fontSize = 15.sp, maxLines = 1)
                            Text(app, color = Color.White.copy(alpha = 0.55f), fontFamily = Poppins, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetIcon(info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            try {
                val d = info.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi)
                    ?: ctx.packageManager.getApplicationIcon(info.provider.packageName)
                d.toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    Box(Modifier.size(36.dp)) {
        img?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
    }
}
