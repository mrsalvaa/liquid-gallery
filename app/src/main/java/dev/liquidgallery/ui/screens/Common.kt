package dev.liquidgallery.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidgallery.data.AlbumPrefs
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaOps
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.TitleTopPadding
import dev.liquidgallery.util.dayLabel
import dev.liquidgallery.util.localDate
import dev.liquidgallery.util.monthLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer

/** De dónde salen los elementos de una cuadrícula o del visor. */
sealed interface Source {
    data object All : Source
    data class Album(val path: String) : Source
    data object Favorites : Source
    data object Videos : Source
    data object Trash : Source
    data class Search(val query: String) : Source
}

/** Elementos de una fuente. Los álbumes ocultos no salen en Imágenes, Vídeos ni búsquedas. */
fun itemsFor(source: Source, media: List<Media>, trash: List<Media>, prefs: AlbumPrefs): List<Media> = when (source) {
    Source.All -> if (prefs.hidden.isEmpty()) media else media.filter { it.album !in prefs.hidden }
    is Source.Album -> media.filter { it.album == source.path }
    Source.Favorites -> media.filter { it.favorite }
    Source.Videos -> media.filter { it.isVideo && it.album !in prefs.hidden }
    Source.Trash -> trash.sortedByDescending { it.expires }
    is Source.Search -> search(media.filter { it.album !in prefs.hidden }, source.query)
}

private fun String.fold(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * Búsqueda sin conexión: cada palabra tiene que aparecer en el nombre, el álbum, la fecha
 * ("octubre", "2024", "ayer") o el tipo ("video", "foto", "gif", "captura", "favorito").
 */
fun search(media: List<Media>, query: String): List<Media> {
    val words = query.fold().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return media.filter { m ->
        val day = localDate(m.date)
        val hay = buildString {
            append(m.name).append(' ').append(m.albumName).append(' ').append(m.album).append(' ')
            append(dayLabel(day)).append(' ').append(monthLabel(day)).append(' ').append(day.year).append(' ')
            append(if (m.isVideo) "video videos " else "foto fotos imagen imagenes ")
            if (m.isGif) append("gif animado ")
            if (m.album.contains("screenshot", true)) append("captura capturas pantalla ")
            if (m.favorite) append("favorito favoritos ")
        }.fold()
        words.all { it in hay }
    }
}

fun shareMedia(context: Context, items: List<Media>) {
    if (items.isEmpty()) return
    val mime = items.map { it.mime }.distinct().singleOrNull()
        ?: if (items.all { it.isVideo }) "video/*" else if (items.none { it.isVideo }) "image/*" else "*/*"
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, items[0].uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
    }.apply {
        type = mime
        clipData = ClipData.newRawUri(null, items[0].uri).also { clip ->
            items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Aviso breve con forma de píldora de cristal. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun BoxScope.ToastHost(message: Pair<String, Long>?, onDone: () -> Unit) {
    var last by remember { mutableStateOf(message?.first) }
    if (message != null) last = message.first
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            onDone()
        }
    }
    AnimatedVisibility(
        message != null,
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        GlassSurface(shape = CircleShape, strong = true, frost = 12.dp) {
            Text(
                last ?: "",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Acciones que se usan desde varias pantallas. */
class GalleryActions(
    val context: Context,
    val ops: MediaOps,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
    /** Abre el selector de álbum para mover ([copy] = false) o copiar. */
    val pickAlbum: (List<Media>, Boolean) -> Unit,
) {
    fun share(items: List<Media>) = shareMedia(context, items)

    fun trash(items: List<Media>, after: () -> Unit = {}) = scope.launch {
        if (ops.trash(items)) {
            toast(if (items.size == 1) "Movido a la papelera" else "${items.size} elementos movidos a la papelera")
            after()
        }
    }

    fun favorite(items: List<Media>, favorite: Boolean, after: () -> Unit = {}) = scope.launch {
        if (ops.setFavorite(items, favorite)) after()
    }

    fun restore(items: List<Media>, after: () -> Unit = {}) = scope.launch {
        if (ops.restore(items)) {
            toast(if (items.size == 1) "Restaurado" else "${items.size} elementos restaurados")
            after()
        }
    }

    fun deleteForever(items: List<Media>, after: () -> Unit = {}) = scope.launch {
        if (ops.deleteForever(items)) {
            toast(if (items.size == 1) "Eliminado" else "${items.size} elementos eliminados")
            after()
        }
    }

    fun moveTo(items: List<Media>, path: String, copy: Boolean, after: () -> Unit = {}) = scope.launch {
        val n = if (copy) ops.copy(items, path) else ops.move(items, path)
        when {
            n == 0 && items.isNotEmpty() -> toast(if (copy) "No se pudo copiar" else "No se pudo mover")
            n > 0 -> {
                toast("${if (copy) "Copiados" else "Movidos"} $n")
                after()
            }
        }
    }

    fun rename(item: Media, name: String) = scope.launch {
        if (!ops.rename(item, name)) toast("No se pudo cambiar el nombre")
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    fun toast(text: String) = toast.invoke(text)

    fun systemToast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

val LocalActions = staticCompositionLocalOf<GalleryActions> { error("Falta GalleryActions") }

/** Cabecera de las pantallas que se abren encima: atrás, título y acciones. */
@Composable
fun BackTitle(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val p = LocalGlass.current
    Row(
        modifier.fillMaxWidth().padding(top = TitleTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onBack)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = p.contentSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    Box(modifier.fillMaxWidth().padding(vertical = 80.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, Modifier.padding(top = 6.dp), fontSize = 14.sp, color = p.contentSecondary)
        }
    }
}
