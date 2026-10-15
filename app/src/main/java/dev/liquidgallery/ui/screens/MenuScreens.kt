package dev.liquidgallery.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidgallery.BuildConfig
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.ui.glass.GlassButton
import dev.liquidgallery.ui.glass.GlassCard
import dev.liquidgallery.ui.glass.GlassChip
import dev.liquidgallery.ui.glass.GlassSwitch
import dev.liquidgallery.ui.glass.GlassTextField
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.ScreenTitle
import dev.liquidgallery.ui.glass.SectionLabel
import dev.liquidgallery.ui.glass.SettingRow
import dev.liquidgallery.util.countLabel
import kotlinx.coroutines.delay
import java.time.LocalDate

// --- Permisos ---

/** 0 = sin acceso, 1 = solo las fotos que eligió el usuario (Android 14), 2 = todo. */
fun mediaAccess(context: Context): Int {
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val full = if (Build.VERSION.SDK_INT >= 33) {
        granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return when {
        full -> 2
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
        else -> 0
    }
}

val MEDIA_PERMISSIONS: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun openManageMedia(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
    )
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
}

/** Estado del acceso a las fotos, que se vuelve a mirar al volver a la app. */
@Composable
fun rememberMediaAccess(): Pair<Int, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val access = remember(tick) { mediaAccess(context) }
    LaunchedEffect(access) { MediaRepo.refresh() }
    return access to { tick++ }
}

/** Pantalla completa cuando aún no hay permiso para ver las fotos. */
@Composable
fun PermissionScreen(padding: PaddingValues, onChanged: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    var asked by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked++
        onChanged()
    }
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = p.accentFg)
            Text(
                "Tus fotos y vídeos",
                Modifier.fillMaxWidth().padding(top = 12.dp),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "Galería necesita permiso para mostrarlos. No tiene acceso a internet: nada sale de tu móvil.",
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                fontSize = 15.sp, color = p.contentSecondary, textAlign = TextAlign.Center,
            )
            GlassButton(
                if (asked >= 2) "Abrir ajustes" else "Permitir acceso",
                {
                    if (asked >= 2) openAppSettings(context) else launcher.launch(MEDIA_PERMISSIONS)
                },
                Modifier.fillMaxWidth(),
                prominent = true,
            )
        }
    }
}

// --- Pestaña Más ---

@Composable
fun MenuScreen(
    padding: PaddingValues,
    access: Int,
    onRequestAccess: () -> Unit,
    onOpenSource: (Source) -> Unit,
    onHidden: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalGlass.current
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val trash by MediaRepo.trash.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val canManage = remember(tick) { MediaStore.canManageMedia(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onRequestAccess() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        ScreenTitle("Más")
        GlassCard {
            MenuRow(Icons.Rounded.Favorite, "Favoritos", media.count { it.favorite }.toString()) { onOpenSource(Source.Favorites) }
            MenuRow(Icons.Rounded.PlayCircle, "Vídeos", media.count { it.isVideo && it.album !in prefs.hidden }.toString()) {
                onOpenSource(Source.Videos)
            }
            MenuRow(Icons.Rounded.VisibilityOff, "Álbumes ocultos", prefs.hidden.size.toString(), onClick = onHidden)
            MenuRow(Icons.Rounded.Delete, "Papelera", trash.size.toString()) { onOpenSource(Source.Trash) }
        }
        Spacer(Modifier.height(14.dp))
        if (access == 1) {
            GlassCard {
                Text("Acceso limitado", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Solo ves las fotos que elegiste. Permite el acceso a todas para usar la galería completa.",
                    Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 14.sp, color = p.contentSecondary,
                )
                GlassButton("Permitir todo", { launcher.launch(MEDIA_PERMISSIONS) }, Modifier.fillMaxWidth(), prominent = true)
            }
            Spacer(Modifier.height(14.dp))
        }
        if (!canManage) {
            GlassCard {
                Text("Menos confirmaciones", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Android pide confirmar cada vez que borras, mueves o marcas favoritos. Con el permiso " +
                        "«Gestionar contenido multimedia» se hace al momento.",
                    Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 14.sp, color = p.contentSecondary,
                )
                GlassButton("Permitir", { openManageMedia(context) }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
        }
        GlassCard {
            MenuRow(Icons.Rounded.Settings, "Ajustes", null, onClick = onSettings)
        }
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(22.dp), tint = p.accentFg)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Software libre (GPL-3.0). Sin internet, sin anuncios y sin rastreo: tus fotos no salen del móvil.",
                    fontSize = 13.sp, color = p.contentSecondary,
                )
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = p.accentFg)
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (value != null) Text(value, fontSize = 15.sp, color = p.contentSecondary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = p.contentSecondary)
    }
}

// --- Ajustes ---

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                GlassChip(label(option), option == selected, { onSelect(option) })
            }
        }
    }
}

@Composable
fun SettingsScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    val s by Stores.settings.state.collectAsStateWithLifecycle()
    val edit: ((dev.liquidgallery.data.AppSettings) -> dev.liquidgallery.data.AppSettings) -> Unit = { Stores.settings.update(it) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val canManage = remember(tick) { MediaStore.canManageMedia(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        BackTitle("Ajustes", onBack)
        SectionLabel("Apariencia")
        AppearanceCard(s, edit)

        SectionLabel("Cuadrícula")
        GlassCard {
            Text("Columnas en Imágenes", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            ChipRow(GRID_COLUMNS, s.gridColumns, { it.toString() }) { v -> edit { it.copy(gridColumns = v) } }
            Text(
                "También puedes pellizcar la cuadrícula. Con 6 u 8 columnas se agrupa por meses.",
                fontSize = 12.sp, color = p.contentSecondary,
            )
        }

        SectionLabel("Vídeos")
        GlassCard {
            SettingRow("Empezar sin sonido") {
                GlassSwitch(s.videoMuted, { v -> edit { it.copy(videoMuted = v) } })
            }
            SettingRow("Repetir al terminar") {
                GlassSwitch(s.videoLoop, { v -> edit { it.copy(videoLoop = v) } })
            }
        }

        SectionLabel("Permisos")
        GlassCard {
            SettingRow(
                "Gestionar contenido multimedia",
                subtitle = if (canManage) "Permitido: sin confirmaciones al borrar o mover" else "Sin permitir: Android pedirá confirmación",
                onClick = { openManageMedia(context) },
            )
        }

        SectionLabel("Acerca de")
        GlassCard {
            SettingRow("Liquid Gallery", subtitle = "Versión ${BuildConfig.VERSION_NAME}")
            Text(
                "Software libre bajo la licencia GPL-3.0. No pide permiso de internet, no tiene anuncios " +
                    "ni rastreo. La papelera y los favoritos son los de Android, así que se comparten con " +
                    "otras galerías.",
                fontSize = 13.sp, color = p.contentSecondary,
            )
        }
    }
}

// --- Búsqueda ---

@Composable
fun SearchScreen(
    padding: PaddingValues,
    initial: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (Source, Media) -> Unit,
) {
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initial) }
    var query by remember { mutableStateOf(initial) }
    LaunchedEffect(text) {
        delay(250)
        query = text.trim()
        onQuery(query)
    }
    val items = remember(query, media, prefs.hidden) {
        if (query.isEmpty()) emptyList() else itemsFor(Source.Search(query), media, emptyList(), prefs)
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (initial.isEmpty()) focus.requestFocus() }
    val selection = remember { Selection() }
    val years = remember(media) { media.map { dev.liquidgallery.util.localDate(it.date).year }.distinct().take(6) }
    val p = LocalGlass.current

    MediaGrid(
        items = items,
        padding = padding,
        selection = selection,
        onOpen = {
            focusManager.clearFocus()
            onOpen(Source.Search(query), it)
        },
        columns = settings.gridColumns,
        selectionBar = standardSelectionBar(selection, items),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                BackTitle("Buscar", onBack, subtitle = if (query.isEmpty()) null else countLabel(items.size, "resultado", "resultados"))
                GlassTextField(
                    text, { text = it }, "Nombre, álbum, fecha, «vídeo»…",
                    focusRequester = focus,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
                val suggestions = listOf("Vídeos", "Capturas", "GIF", "Favoritos", "Ayer") +
                    listOf(LocalDate.now().year.toString()) + years.map { it.toString() }
                ChipRow(suggestions.distinct(), "", { it }, Modifier.padding(top = 8.dp)) { text = it }
                if (query.isNotEmpty() && items.isEmpty()) {
                    EmptyState("Sin resultados", "Prueba con otra palabra.")
                }
                if (query.isEmpty()) {
                    Text(
                        "La búsqueda se hace en el móvil, sin conexión.",
                        Modifier.padding(8.dp), fontSize = 13.sp, color = p.contentSecondary,
                    )
                }
            }
        }
    }
}
