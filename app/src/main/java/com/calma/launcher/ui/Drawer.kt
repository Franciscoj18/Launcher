package com.calma.launcher.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calma.launcher.data.AppEntry
import com.calma.launcher.data.Apps
import com.calma.launcher.data.HomeWallpaper
import com.calma.launcher.data.Prefs
import kotlinx.coroutines.delay

/** Cajón de apps con buscador. Se abre deslizando hacia arriba. */
@Composable
fun AppDrawer(pickMode: Boolean, onDismiss: () -> Unit, onChoose: (AppEntry) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val dismiss = rememberUpdatedState {
        keyboard?.hide()
        onDismiss()
    }

    BackHandler { dismiss.value() }

    val all = Apps.list
    val results = remember(query, all) {
        val q = Apps.normalize(query.trim())
        if (q.isEmpty()) all
        else all.filter { it.search.contains(q) }
            .sortedBy { if (it.search.startsWith(q)) 0 else 1 }
    }

    // El teclado ya no se abre solo: aparece cuando tocas el buscador

    // Deslizar hacia abajo estando arriba de la lista cierra el cajón
    val pull = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    acc += available.y
                    if (acc > 260f) { acc = 0f; dismiss.value() }
                } else if (consumed.y != 0f) {
                    acc = 0f
                }
                return Offset.Zero
            }
        }
    }

    val choose: (AppEntry) -> Unit = { e ->
        keyboard?.hide()
        onChoose(e)
    }

    val blurred = HomeWallpaper.blurred
    Box(Modifier.fillMaxSize()) {
    if (blurred != null) {
        val img = remember(blurred) { blurred.asImageBitmap() }
        Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(if (blurred != null) PanelSurface.copy(alpha = 0.62f) else PanelSurface.copy(alpha = 0.95f))
            .systemBarsPadding()
            .imePadding()
    ) {
        // Barra de búsqueda (también se puede arrastrar hacia abajo para cerrar)
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 120f) dismiss.value() },
                        onVerticalDrag = { change, dy -> total += dy; change.consume() },
                    )
                }
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            if (pickMode) "Elige una app para el inicio" else "Buscar apps",
                            color = Color.White.copy(alpha = 0.45f),
                            fontFamily = Poppins,
                            fontSize = 15.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontFamily = Poppins, fontSize = 15.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(choose) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Borrar",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp).clickable { query = "" },
                    )
                }
            }
        }

        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pull),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(results, key = { it.key + "#" + it.user.hashCode() }) { e ->
                DrawerRow(e, pickMode, choose)
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "No hay apps con ese nombre",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = Poppins,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(28.dp),
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(e: AppEntry, pickMode: Boolean, onChoose: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onChoose(e) },
                    onLongClick = { if (!pickMode) menu = true },
                )
                .padding(horizontal = 26.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(e, 32.dp)
            Spacer(Modifier.width(18.dp))
            Text(e.label, color = Color.White, fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 16.sp, maxLines = 1)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Agregar a inicio") }, onClick = {
                menu = false
                val free = Prefs.dock.indexOfFirst { it.isEmpty() }
                when {
                    Prefs.dock.contains(e.key) -> Toast.makeText(ctx, "Ya está en el inicio", Toast.LENGTH_SHORT).show()
                    free >= 0 -> {
                        Prefs.setDockSlot(free, e.key)
                        Toast.makeText(ctx, "Agregada al inicio", Toast.LENGTH_SHORT).show()
                    }
                    else -> Toast.makeText(
                        ctx,
                        "El inicio está lleno: mantén presionada una app del inicio y elige \"Cambiar app\"",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            })
            DropdownMenuItem(text = { Text("Información de la app") }, onClick = { menu = false; Apps.openInfo(ctx, e) })
            DropdownMenuItem(text = { Text("Desinstalar") }, onClick = { menu = false; Apps.uninstall(ctx, e) })
        }
    }
}
