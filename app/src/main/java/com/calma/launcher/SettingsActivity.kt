package com.calma.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calma.launcher.data.Controls
import com.calma.launcher.data.DeviceInfo
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.IconLoader
import com.calma.launcher.data.IconPack
import com.calma.launcher.data.Place
import com.calma.launcher.data.Prefs
import com.calma.launcher.data.Weather
import com.calma.launcher.lock.Live
import com.calma.launcher.lock.LockActivity
import com.calma.launcher.lock.LockService
import com.calma.launcher.panel.PanelService
import com.calma.launcher.ui.Accent
import com.calma.launcher.ui.AccentChoices
import com.calma.launcher.ui.PanelSurface
import com.calma.launcher.ui.CalmaTheme
import com.calma.launcher.ui.Poppins
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {

    /** Sube al volver a esta pantalla, para revisar permisos otra vez. */
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CalmaTheme {
                SettingsScreen(resumes.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        LockService.sync(this)
    }
}

@Composable
private fun SettingsScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val isDefault = remember(resumeTick) {
        val ri = ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        ri?.activityInfo?.packageName == ctx.packageName
    }
    val canOverlay = remember(resumeTick) { Settings.canDrawOverlays(ctx) }
    val packs = remember(resumeTick) { IconPack.installed(ctx) }

    var cityQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    fun useLocation() {
        locating = true
        scope.launch {
            val loc = Weather.currentLocation(ctx)
            if (loc == null) {
                Toast.makeText(ctx, "No se pudo obtener tu ubicación. Prueba buscando tu ciudad.", Toast.LENGTH_LONG).show()
            } else {
                val name = Weather.cityName(ctx, loc, Prefs.lang)
                Prefs.setLocation(name, loc.latitude, loc.longitude)
                Weather.refreshIfStale(force = true)
            }
            locating = false
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useLocation()
        else Toast.makeText(ctx, "Sin permiso de ubicación. Puedes buscar tu ciudad.", Toast.LENGTH_LONG).show()
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickHome = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            Prefs.setHomeImageValue(uri.toString())
            scope.launch { HomeWallpaper.load(ctx.applicationContext) }
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { DeviceInfo.refreshCalendar(ctx) }
    }
    val hasNotifAccess = remember(resumeTick) { Live.hasAccess(ctx) }
    val panelEnabled = remember(resumeTick) { PanelService.isEnabled(ctx) }
    val canWriteSettings = remember(resumeTick) { Controls.canWrite(ctx) }
    val hasCalendar = remember(resumeTick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    var messageText by remember { mutableStateOf(Prefs.message) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            Prefs.setLockImageValue(uri.toString())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Calma", color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Text("Ajustes del launcher", color = Color.White.copy(alpha = 0.6f), fontFamily = Poppins, fontSize = 13.sp)

        // ---------- Launcher predeterminado ----------
        Card {
            Title("Launcher predeterminado")
            Body(
                if (isDefault) "Calma ya es tu pantalla de inicio. ✓"
                else "Para usar Calma al presionar el botón de inicio, elígelo como app de inicio."
            )
            if (!isDefault) {
                Button("Elegir app de inicio") {
                    open(ctx, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
        }

        // ---------- Íconos ----------
        Card {
            Title("Íconos")
            Choices(
                options = listOf("mono" to "Blancos", "original" to "Originales"),
                selected = Prefs.iconStyle,
                onSelect = { Prefs.setIconStyleValue(it); IconLoader.clear() },
            )
            Spacer(Modifier.height(12.dp))
            Label("Paquete de íconos")
            Body("Para que se vean idénticos al video, instala un paquete de íconos blancos como \"Whicons\" (gratis) y elígelo aquí.")
            RadioLine("Ninguno (convertir automáticamente)", Prefs.iconPack.isEmpty()) {
                Prefs.setIconPackValue(""); IconLoader.clear()
            }
            for ((pkg, name) in packs) {
                RadioLine(name, Prefs.iconPack == pkg) { Prefs.setIconPackValue(pkg); IconLoader.clear() }
            }
            Button("Buscar Whicons en Play Store") {
                open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=whicons&c=apps")))
            }
            Spacer(Modifier.height(10.dp))
            SwitchLine("Mostrar nombres bajo los íconos", Prefs.showLabels) { Prefs.setShowLabelsValue(it) }
        }

        // ---------- Apariencia ----------
        Card {
            Title("Apariencia")
            Label("Estilo del reloj")
            Choices(
                options = listOf("eco" to "Eco", "apilado" to "Apilado", "palabras" to "Palabras", "terminal" to "Terminal"),
                selected = Prefs.clockStyle,
                onSelect = { Prefs.setClockStyleValue(it) },
            )
            Spacer(Modifier.height(14.dp))
            SwitchLine("Adaptar los colores al fondo", Prefs.wallTheme) { on ->
                Prefs.setWallThemeValue(on)
                if (on) Prefs.setAccent("auto", Prefs.accentColor)
                HomeWallpaper.refreshAccent(ctx.applicationContext)
            }
            Body("Menús, cajón de apps, tarjetas y panel toman el tono de tu fondo de pantalla.")
            Spacer(Modifier.height(8.dp))
            Label("Color de acento")
            AccentPicker()
            Spacer(Modifier.height(14.dp))
            Label("Mensaje bajo la fecha")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (messageText.isEmpty()) {
                    Text("Escribe algo tuyo (opcional)", color = Color.White.copy(alpha = 0.4f), fontFamily = Poppins, fontSize = 14.sp)
                }
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it.take(60); Prefs.setMessageValue(messageText) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontFamily = Poppins, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            SwitchLine("Frase del día (si no hay mensaje)", Prefs.dailyQuote) { Prefs.setDailyQuoteValue(it) }
            SwitchLine("Animación al volver al inicio", Prefs.enterAnim) { Prefs.setEnterAnimValue(it) }
        }

        // ---------- Fecha y hora ----------
        Card {
            Title("Fecha y hora")
            Label("Idioma")
            Choices(
                options = listOf("es" to "Español", "en" to "English"),
                selected = Prefs.lang,
                onSelect = { Prefs.setLangValue(it) },
            )
            Spacer(Modifier.height(8.dp))
            SwitchLine("Formato de 24 horas", Prefs.use24h) { Prefs.setUse24hValue(it) }
        }

        // ---------- Clima ----------
        Card {
            Title("Clima")
            Body(if (Prefs.hasLocation) "Ciudad: ${Prefs.city}" else "Aún no eliges ciudad.")
            if (Prefs.hasWeather) Body(Weather.sentence(Prefs.lang))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (cityQuery.isEmpty()) {
                        Text("Escribe tu ciudad", color = Color.White.copy(alpha = 0.4f), fontFamily = Poppins, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = cityQuery,
                        onValueChange = { cityQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontFamily = Poppins, fontSize = 14.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (cityQuery.isNotBlank()) {
                                searching = true
                                scope.launch { results = Weather.searchCity(cityQuery, Prefs.lang); searching = false }
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(if (searching) "…" else "Buscar") {
                    if (cityQuery.isNotBlank()) {
                        searching = true
                        scope.launch {
                            results = Weather.searchCity(cityQuery, Prefs.lang)
                            searching = false
                            if (results.isEmpty()) Toast.makeText(ctx, "No encontré esa ciudad", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            for (p in results) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Prefs.setLocation(p.name, p.lat, p.lon)
                            results = emptyList()
                            cityQuery = ""
                            scope.launch { Weather.refreshIfStale(force = true) }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Text(p.name, color = Color.White, fontFamily = Poppins, fontSize = 15.sp)
                    Text(p.detail, color = Color.White.copy(alpha = 0.5f), fontFamily = Poppins, fontSize = 12.sp)
                }
            }
            Button(if (locating) "Buscando ubicación…" else "Usar mi ubicación") {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    useLocation()
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }

        // ---------- Panel de controles ----------
        Card {
            Title("Panel de controles")
            Body("Linterna, brillo, volumen, rotación, No molestar, música y notificaciones con el estilo de Calma.")
            SwitchLine("Abrirlo al deslizar hacia abajo en el inicio", Prefs.panelHome) { Prefs.setPanelHomeValue(it) }
            Spacer(Modifier.height(6.dp))
            Label("En cualquier app")
            if (panelEnabled) {
                Body("Activado. ✓ Desliza hacia abajo desde la esquina superior para abrirlo.")
            } else {
                Body("Actívalo en Accesibilidad → Panel de controles de Calma. Android mostrará una advertencia: es normal para este tipo de apps. Calma no lee lo que hay en tu pantalla.")
                Button("Activar en todas las apps") { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            Spacer(Modifier.height(10.dp))
            Label("Esquina para abrirlo")
            Choices(
                options = listOf("left" to "Izquierda", "right" to "Derecha"),
                selected = Prefs.panelSide,
                onSelect = { Prefs.setPanelSideValue(it); PanelService.instance?.refreshTrigger() },
            )
            Spacer(Modifier.height(10.dp))
            if (!canWriteSettings) {
                Body("Para cambiar el brillo y la rotación desde el panel, Calma necesita \"Modificar ajustes del sistema\".")
                Button("Permitir brillo y rotación") { open(ctx, Controls.writeSettingsIntent(ctx)) }
                Spacer(Modifier.height(10.dp))
            }
            Label("Botones del panel")
            val names = listOf(
                "wifi" to "Wi‑Fi", "data" to "Datos", "bt" to "Bluetooth", "torch" to "Linterna",
                "rotate" to "Rotación", "dnd" to "No molestar", "shot" to "Captura", "lock" to "Bloquear",
            )
            val on = Prefs.panelButtons.split(",").toSet()
            for ((key, name) in names) {
                SwitchLine(name, key in on) { Prefs.togglePanelButton(key) }
            }
        }

        // ---------- Panel (tercera pantalla) ----------
        Card {
            Title("Panel del día")
            Body("La tercera pantalla muestra tu próxima alarma, tu próximo evento, el clima de la semana, la batería y el almacenamiento.")
            if (!hasCalendar) {
                Button("Permitir ver mi calendario") { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
            } else {
                Body("Calendario conectado. ✓")
            }
        }

        // ---------- Música y notificaciones ----------
        Card {
            Title("Música y notificaciones")
            Body("Con el acceso a notificaciones, la segunda pantalla se tiñe con el color de la canción que suena y la pantalla de bloqueo muestra qué apps tienen avisos. Calma no guarda ni envía nada.")
            if (!hasNotifAccess) {
                Button("Dar acceso a notificaciones") {
                    open(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            } else {
                Body("Acceso activado. ✓")
            }
            SwitchLine("Teñir con el color de la portada", Prefs.musicTint) { Prefs.setMusicTintValue(it) }
        }

        // ---------- Widget ----------
        Card {
            Title("Widget de música")
            Body("Para agregarlo o cambiarlo: mantén presionado un espacio vacío del inicio → \"Agregar widget\". Aparece en la segunda pantalla (desliza a la izquierda).")
            Label("Altura del widget: ${Prefs.widgetHeight} dp")
            Slider(
                value = Prefs.widgetHeight.toFloat(),
                onValueChange = { Prefs.setWidgetHeightValue(it.roundToInt()) },
                valueRange = 110f..300f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
            )
        }

        // ---------- Pantalla de bloqueo ----------
        Card {
            Title("Pantalla de bloqueo")
            Body("Muestra la hora grande al encender la pantalla. Desliza hacia arriba para entrar. Si usas huella, se quita sola al desbloquear.")
            SwitchLine("Activar", Prefs.lockEnabled) { on ->
                Prefs.setLockEnabledValue(on)
                if (on) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!Settings.canDrawOverlays(ctx)) {
                        Toast.makeText(ctx, "Activa \"Mostrar sobre otras apps\" para Calma", Toast.LENGTH_LONG).show()
                        open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                LockService.sync(ctx)
            }
            if (Prefs.lockEnabled && !canOverlay) {
                Body("⚠ Falta el permiso \"Mostrar sobre otras apps\". Sin él no puede aparecer.")
                Button("Dar permiso") {
                    open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            }
            SwitchLine("Mostrar notificaciones", Prefs.lockNotifs) { Prefs.setLockNotifsValue(it) }
            SwitchLine("Mostrar el contenido de los mensajes", Prefs.lockContent) { Prefs.setLockContentValue(it) }
            SwitchLine("Controles de música", Prefs.lockMusic) { Prefs.setLockMusicValue(it) }
            if (!hasNotifAccess) {
                Body("⚠ Las notificaciones y los controles de música necesitan el acceso a notificaciones (ver \"Música y notificaciones\").")
            }
            SwitchLine("Modo reposo al cargar", Prefs.reposo) { Prefs.setReposoValue(it) }
            Body("En reposo la hora queda tenue y encendida mientras carga. Toca para ver la pantalla normal.")
            Spacer(Modifier.height(6.dp))
            Body(if (Prefs.lockImage.isEmpty()) "Fondo: el mismo de tu pantalla de inicio." else "Fondo: imagen personalizada.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Elegir imagen") { pickImage.launch(arrayOf("image/*")) }
                if (Prefs.lockImage.isNotEmpty()) SmallButton("Quitar") { Prefs.setLockImageValue("") }
                SmallButton("Probar") { LockActivity.show(ctx) }
            }
            Spacer(Modifier.height(6.dp))
            Body("Consejo: en Ajustes → Seguridad del teléfono, usa \"Deslizar\" o huella para que entrar sea más rápido.")
        }

        // ---------- Fondo ----------
        Card {
            Title("Fondo de pantalla")
            Body(
                if (Prefs.homeImage.isEmpty()) "Ahora usas el fondo del sistema. Elige una imagen tuya aquí para activar el efecto de profundidad y el cajón difuminado."
                else "Usas tu propia imagen en Calma."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Elegir mi imagen") { pickHome.launch(arrayOf("image/*")) }
                if (Prefs.homeImage.isNotEmpty()) {
                    SmallButton("Usar la del sistema") {
                        Prefs.setHomeImageValue("")
                        scope.launch { HomeWallpaper.load(ctx.applicationContext) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchLine("Efecto de profundidad al inclinar", Prefs.parallax) { Prefs.setParallaxValue(it) }
            Label("Oscurecer el fondo: ${(Prefs.dim * 100).roundToInt()}%")
            Slider(
                value = Prefs.dim,
                onValueChange = { Prefs.setDimValue(it) },
                valueRange = 0f..0.7f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
            )
            Button("Cambiar fondo del sistema") {
                open(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Gestos: desliza hacia arriba para ver todas tus apps y buscarlas, hacia abajo para las notificaciones, y mantén presionado para el menú.",
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = Poppins,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(30.dp))
    }
}

private fun open(ctx: android.content.Context, i: Intent) {
    try {
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "No se pudo abrir", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PanelSurface)
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.65f),
        fontFamily = Poppins,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun Button(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.Black, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp)
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.Black else Color.White, fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RadioLine(text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (on) Accent else Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontFamily = Poppins, fontSize = 14.sp)
    }
}

@Composable
private fun SwitchLine(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontFamily = Poppins, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent),
        )
    }
}


@Composable
private fun AccentPicker() {
    val auto = Prefs.accentMode == "auto"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Automático: sale del fondo
        val autoColor = HomeWallpaper.autoAccent ?: Color.White
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(Color(0xFFFF6B6B), Color(0xFFFFD166), Color(0xFF6EE7B7), Color(0xFF67C8FF), Color(0xFFB69CFF), Color(0xFFFF6B6B))
                    )
                )
                .border(if (auto) 3.dp else 0.dp, Color.White, CircleShape)
                .clickable { Prefs.setAccent("auto", Prefs.accentColor) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(autoColor))
        }
        for (c in AccentChoices) {
            val on = !auto && Prefs.accentColor == c
            Box(
                Modifier
                    .size(if (on) 30.dp else 26.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 0.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    .clickable { Prefs.setAccent("custom", c) }
            )
        }
    }
    Text(
        if (auto) "Automático: se toma del fondo de pantalla" else "Elegido por ti",
        color = Color.White.copy(alpha = 0.5f),
        fontFamily = Poppins,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}
