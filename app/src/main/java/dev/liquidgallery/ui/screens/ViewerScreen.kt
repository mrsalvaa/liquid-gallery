package dev.liquidgallery.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.LocalBackdrop
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.LocalPrompt
import dev.liquidgallery.ui.glass.PromptSpec
import dev.liquidgallery.ui.glass.SheetAction
import dev.liquidgallery.ui.glass.SheetSpec
import dev.liquidgallery.util.dayLabel
import dev.liquidgallery.util.duration
import dev.liquidgallery.util.fileSize
import dev.liquidgallery.util.fullDate
import dev.liquidgallery.util.localDate
import dev.liquidgallery.util.thumb
import dev.liquidgallery.util.timeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Visor a pantalla completa. [external] se usa al abrir un archivo desde otra app que no está en
 * la galería: solo se puede ver y compartir.
 */
@Composable
fun ViewerScreen(
    source: Source,
    startId: Long,
    onClose: (Long?) -> Unit,
    onEdit: (Media) -> Unit,
    external: List<Media>? = null,
) {
    val context = LocalContext.current
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val trash by MediaRepo.trash.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val vault by dev.liquidgallery.data.Vault.media.collectAsStateWithLifecycle()
    val items = external ?: remember(source, media, trash, prefs.archived, vault) { itemsFor(source, media, trash, prefs, vault) }
    val itemsState = rememberUpdatedState(items)
    val actions = LocalActions.current
    val sheet = LocalActionSheet.current
    val prompt = LocalPrompt.current
    val p = LocalGlass.current
    val scope = rememberCoroutineScope()
    val isTrash = source == Source.Trash
    val isVault = source == Source.Vault
    val unlocked by dev.liquidgallery.data.VaultLock.unlocked.collectAsStateWithLifecycle()
    if (isVault && !unlocked) {
        LaunchedEffect(Unit) { onClose(null) }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val readOnly = external != null && external.all { it.id < 0 }

    var currentId by rememberSaveable { mutableLongStateOf(startId) }
    val close by rememberUpdatedState { onClose(currentId) }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose(null) }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val pager = rememberPagerState(items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)) { itemsState.value.size }
    LaunchedEffect(pager) {
        snapshotFlow { itemsState.value.getOrNull(pager.currentPage)?.id }.collect { id -> if (id != null) currentId = id }
    }
    val current = items.getOrNull(pager.currentPage) ?: items.last()

    var chrome by rememberSaveable { mutableStateOf(true) }
    var details by rememberSaveable { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    BackHandler(enabled = details) { details = false }
    BackHandler(enabled = !details) { close() }

    // Fondo: negro, salvo en tema claro con los controles a la vista.
    val dark = !chrome || p.dark
    val bg by animateColorAsState(if (dark) Color.Black else Color(0xFFF4F5F9), label = "bg")
    val fg = if (dark) Color.White else Color(0xFF0E1222)
    SystemBars(visible = chrome, lightIcons = dark, restoreLight = !p.dark)

    // Vídeo: un reproductor para todo el visor; solo suena el de la página actual.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (settings.videoLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (settings.videoMuted) 0f else 1f
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var playingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(current.id) {
        if (playingId != null && playingId != current.id) {
            player.stop()
            playingId = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    fun play(m: Media) {
        player.setMediaItem(MediaItem.fromUri(m.uri))
        player.prepare()
        player.playWhenReady = true
        playingId = m.id
        chrome = false
    }

    // Deslizar hacia abajo cierra; hacia arriba abre los detalles.
    val dismiss = remember { Animatable(0f) }
    val density = LocalDensity.current
    val closeAt = with(density) { 120.dp.toPx() }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(bg.copy(alpha = 1f - (abs(dismiss.value) / (closeAt * 3)).coerceIn(0f, 0.7f))),
        ) {
            HorizontalPager(
                state = pager,
                key = { itemsState.value.getOrNull(it)?.id ?: it },
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dismiss.value.roundToInt()) }
                    .graphicsLayer {
                        val s = 1f - (abs(dismiss.value) / (closeAt * 8)).coerceIn(0f, 0.15f)
                        scaleX = s
                        scaleY = s
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !zoomed && !details,
                        state = rememberDraggableState { d -> scope.launch { dismiss.snapTo(dismiss.value + d) } },
                        onDragStopped = { v ->
                            when {
                                dismiss.value > closeAt || v > 2500f -> close()
                                dismiss.value < -closeAt / 2 || v < -2500f -> {
                                    if (!readOnly) details = true
                                    dismiss.animateTo(0f, spring(dampingRatio = 0.8f))
                                }
                                else -> dismiss.animateTo(0f, spring(dampingRatio = 0.8f))
                            }
                        },
                    ),
            ) { page ->
                val m = itemsState.value.getOrNull(page) ?: return@HorizontalPager
                val isCurrent = page == pager.currentPage
                if (m.isVideo) {
                    VideoPage(
                        m, player,
                        playing = playingId == m.id,
                        onPlay = { play(m) },
                        onTap = { chrome = !chrome },
                    )
                } else {
                    ImagePage(
                        m,
                        isCurrent = isCurrent,
                        onTap = { chrome = !chrome },
                        onZoomed = { if (isCurrent) zoomed = it },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // Cabecera: atrás, fecha y hora, más opciones.
            AnimatedVisibility(
                chrome && !details,
                Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", { close() })
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        if (isTrash) {
                            val days = TimeUnit.MILLISECONDS.toDays((current.expires - System.currentTimeMillis()).coerceAtLeast(0))
                            Text("Papelera", color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Se eliminará en $days días", color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
                        } else if (readOnly) {
                            Text(current.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(dayLabel(localDate(current.date)), color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(timeOf(current.date), color = fg.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                    if (!readOnly && !isTrash && !isVault) {
                        GlassIconButton(Icons.Rounded.MoreVert, "Más", {
                            sheet(moreSheet(current, actions, prompt, context) { details = true })
                        })
                    }
                }
            }

            // Pie: controles del vídeo y acciones.
            AnimatedVisibility(
                chrome && !details,
                Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (current.isVideo && playingId == current.id) {
                        VideoControls(player, fg)
                        Spacer(Modifier.height(10.dp))
                    }
                    GlassSurface(Modifier.fillMaxWidth().height(64.dp), shape = CircleShape, strong = true, frost = 6.dp) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when {
                                readOnly -> {
                                    BarButton(Icons.Rounded.Share, "Compartir", fg) { actions.share(listOf(current)) }
                                }
                                isVault -> {
                                    BarButton(Icons.Rounded.Visibility, "Mostrar", fg) { actions.unhide(listOf(current)) }
                                    BarButton(Icons.Rounded.Share, "Compartir", fg) { actions.share(listOf(current)) }
                                    BarButton(Icons.Rounded.DeleteForever, "Eliminar", p.danger) {
                                        sheet(deleteHiddenSheet(listOf(current), actions) {})
                                    }
                                }
                                isTrash -> {
                                    BarButton(Icons.Rounded.Restore, "Restaurar", fg) { actions.restore(listOf(current)) }
                                    BarButton(Icons.Rounded.DeleteForever, "Eliminar", p.danger) { actions.deleteForever(listOf(current)) }
                                }
                                else -> {
                                    BarButton(
                                        if (current.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        "Favorito",
                                        if (current.favorite) Color(0xFFFF6B8B) else fg,
                                    ) { actions.favorite(listOf(current), !current.favorite) }
                                    if (!current.isVideo && !current.isGif) {
                                        BarButton(Icons.Rounded.Edit, "Editar", fg) {
                                            player.pause()
                                            onEdit(current)
                                        }
                                    }
                                    BarButton(Icons.Rounded.Share, "Compartir", fg) { actions.share(listOf(current)) }
                                    BarButton(Icons.Rounded.Delete, "Eliminar", fg) { actions.trash(listOf(current)) }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                details,
                Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                DetailsCard(current) { details = false }
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(26.dp), tint = tint)
    }
}

/** Oculta las barras del sistema con los controles y pone sus iconos claros sobre el fondo negro. */
@Composable
private fun SystemBars(visible: Boolean, lightIcons: Boolean, restoreLight: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    LaunchedEffect(visible, lightIcons) {
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(controller) {
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = restoreLight
            controller.isAppearanceLightNavigationBars = restoreLight
        }
    }
}

@Composable
private fun ImagePage(m: Media, isCurrent: Boolean, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val context = LocalContext.current
    val zoomState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    val zoomed = (zoomState.zoomableState.zoomFraction ?: 0f) > 0.01f
    LaunchedEffect(zoomed, isCurrent) { if (isCurrent) onZoomed(zoomed) }
    LaunchedEffect(isCurrent) { if (!isCurrent) zoomState.zoomableState.resetZoom() }
    Box(Modifier.fillMaxSize()) {
        // Miniatura (ya en memoria) mientras carga la foto completa.
        if (!zoomState.isImageDisplayed) {
            AsyncImage(m.thumb(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        ZoomableAsyncImage(
            model = ImageRequest.Builder(context).data(m.uri).build(),
            contentDescription = m.name,
            state = zoomState,
            modifier = Modifier.fillMaxSize(),
            onClick = { onTap() },
        )
    }
}

@Composable
private fun VideoPage(m: Media, player: ExoPlayer, playing: Boolean, onPlay: () -> Unit, onTap: () -> Unit) {
    val ratio = if (m.width > 0 && m.height > 0) m.width.toFloat() / m.height else 16f / 9f
    var videoRatio by remember { mutableFloatStateOf(ratio) }
    if (playing) {
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }
    Box(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            AndroidView(
                factory = { TextureView(it).also { tv -> player.setVideoTextureView(tv) } },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = Modifier.fillMaxWidth().aspectRatio(videoRatio, matchHeightConstraintsFirst = ratio < 1f),
            )
        } else {
            AsyncImage(m.thumb(1024), m.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            GlassSurface(
                Modifier.size(76.dp),
                shape = CircleShape,
                tint = Color.Black.copy(alpha = 0.25f),
                onClick = onPlay,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Reproducir", Modifier.align(Alignment.Center).size(40.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoControls(player: ExoPlayer, fg: Color) {
    val p = LocalGlass.current
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            if (seeking == null) position = player.currentPosition
            length = player.duration.coerceAtLeast(0)
            playing = player.isPlaying
            delay(200)
        }
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), strong = true, frost = 6.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BarButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir", fg) {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                if (player.isPlaying) player.pause() else player.play()
                playing = !playing
            }
            Text(duration(seeking?.toLong() ?: position), color = fg, fontSize = 12.sp)
            Slider(
                value = (seeking ?: position.toFloat()).coerceIn(0f, length.toFloat().coerceAtLeast(1f)),
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let { player.seekTo(it.toLong()) }
                    seeking = null
                },
                valueRange = 0f..length.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = p.accent,
                    inactiveTrackColor = fg.copy(alpha = 0.25f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(duration(length), color = fg, fontSize = 12.sp)
            BarButton(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Sonido", fg) {
                muted = !muted
                player.volume = if (muted) 0f else 1f
            }
        }
    }
}

private fun moreSheet(
    m: Media,
    actions: GalleryActions,
    prompt: (PromptSpec) -> Unit,
    context: android.content.Context,
    onDetails: () -> Unit,
) = SheetSpec(
    m.name,
    listOf(
        SheetAction("Detalles", Icons.Rounded.Info, onClick = onDetails),
        SheetAction("Mover a un álbum", Icons.AutoMirrored.Rounded.DriveFileMove) { actions.pickAlbum(listOf(m), false) },
        SheetAction("Copiar a un álbum", Icons.Rounded.ContentCopy) { actions.pickAlbum(listOf(m), true) },
        if (m.id in dev.liquidgallery.data.Stores.albums.value.archived) {
            SheetAction("Desarchivar", Icons.Rounded.Unarchive) { actions.archive(listOf(m), false) }
        } else {
            SheetAction("Archivar", Icons.Rounded.Archive) { actions.archive(listOf(m), true) }
        },
        SheetAction("Ocultar", Icons.Rounded.VisibilityOff) { actions.hide(listOf(m)) },
        SheetAction("Cambiar el nombre", Icons.Rounded.DriveFileRenameOutline) {
            prompt(PromptSpec("Cambiar el nombre", "Guardar", m.name.substringBeforeLast('.')) { actions.rename(m, it) })
        },
        SheetAction("Usar como…", Icons.Rounded.Wallpaper) {
            val intent = Intent(Intent.ACTION_ATTACH_DATA)
                .setDataAndType(m.uri, m.mime)
                .putExtra("mimeType", m.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, null))
        },
        SheetAction("Abrir con…", Icons.AutoMirrored.Rounded.OpenInNew) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, null))
        },
    ),
)

private data class Exif(val camera: String?, val shot: String?, val lens: String?)

@Composable
private fun DetailsCard(m: Media, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    val exif by produceState<Exif?>(null, m.uri) {
        value = if (m.isVideo) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(m.uri)!!.use { input ->
                    val e = ExifInterface(input)
                    val make = e.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                    val model = e.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                    val camera = listOfNotNull(make, model).let { parts ->
                        if (make != null && model != null && model.startsWith(make, true)) model else parts.joinToString(" ")
                    }.ifBlank { null }
                    val f = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    val iso = e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    val shot = listOfNotNull(
                        f.takeIf { it > 0 }?.let { "f/%.1f".format(it) },
                        t.takeIf { it > 0 }?.let { if (it >= 1) "%.1f s".format(it) else "1/${(1 / it).roundToInt()} s" },
                        iso.takeIf { it > 0 }?.let { "ISO $it" },
                        focal.takeIf { it > 0 }?.let { "%.1f mm".format(it) },
                    ).joinToString(" · ").ifBlank { null }
                    Exif(camera, shot, e.getAttribute(ExifInterface.TAG_LENS_MODEL))
                }
            }.getOrNull()
        }
    }
    GlassSurface(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(34.dp),
        strong = true,
        frost = 18.dp,
        onClick = onClose,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(fullDate(m.date), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(timeOf(m.date), fontSize = 14.sp, color = p.contentSecondary)
            Spacer(Modifier.height(16.dp))
            DetailRow("Nombre", m.name)
            DetailRow("Ubicación", "/${m.album}")
            val mp = m.width.toLong() * m.height / 1_000_000.0
            DetailRow(
                if (m.isVideo) "Vídeo" else "Imagen",
                buildString {
                    if (m.width > 0) append("${m.width} × ${m.height}")
                    if (!m.isVideo && mp >= 1) append(" · %.0f MP".format(mp))
                    if (m.isVideo) append(" · ${duration(m.duration)}")
                    append(" · ${fileSize(m.size)}")
                },
            )
            exif?.camera?.let { DetailRow("Cámara", it) }
            exif?.shot?.let { DetailRow("Disparo", it) }
            exif?.lens?.let { DetailRow("Objetivo", it) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val p = LocalGlass.current
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, fontSize = 12.sp, color = p.contentSecondary, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 15.sp)
    }
}
