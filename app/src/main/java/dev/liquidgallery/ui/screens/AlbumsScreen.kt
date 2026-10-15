package dev.liquidgallery.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.liquidgallery.data.Album
import dev.liquidgallery.data.AlbumGroup
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.data.buildAlbums
import dev.liquidgallery.data.newAlbumPath
import dev.liquidgallery.data.sortAlbums
import dev.liquidgallery.ui.glass.GlassCard
import dev.liquidgallery.ui.glass.GlassChip
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.LocalPrompt
import dev.liquidgallery.ui.glass.PromptSpec
import dev.liquidgallery.ui.glass.ScreenTitle
import dev.liquidgallery.ui.glass.SectionLabel
import dev.liquidgallery.ui.glass.SheetAction
import dev.liquidgallery.ui.glass.SheetSpec
import dev.liquidgallery.util.countLabel
import dev.liquidgallery.util.thumb
import kotlin.math.roundToInt

private val ALBUM_COLUMNS = listOf(2, 3, 4)

/** Lo que se ve en la pestaña Álbumes. */
private sealed interface Tile {
    val key: String
    data class AlbumTile(val album: Album) : Tile { override val key get() = "a:${album.path}" }
    data class Virtual(val source: Source, val name: String, val cover: Media?, val count: Int, val icon: ImageVector) : Tile {
        override val key get() = "v:$name"
    }
    data class GroupTile(val group: AlbumGroup, val albums: List<Album>) : Tile { override val key get() = "g:${group.id}" }
}

@Composable
fun AlbumsScreen(
    padding: PaddingValues,
    onOpenSource: (Source) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onReorder: () -> Unit,
    onVault: () -> Unit,
) {
    val actions = LocalActions.current
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val sheet = LocalActionSheet.current
    val prompt = LocalPrompt.current

    val albums = remember(media, prefs, settings.albumSort) {
        sortAlbums(buildAlbums(media, prefs), settings.albumSort, prefs.order)
    }
    val tiles = remember(albums, prefs.groups, media) {
        val grouped = prefs.groups.flatMap { it.albums }.toSet()
        val favorites = media.filter { it.favorite }
        val videos = media.filter { it.isVideo }
        buildList {
            val (pinned, rest) = albums.partition { dev.liquidgallery.data.isPinnedAlbum(it.path) }
            pinned.forEach { add(Tile.AlbumTile(it)) }
            add(Tile.Virtual(Source.Favorites, "Favoritos", favorites.firstOrNull(), favorites.size, Icons.Rounded.Favorite))
            if (videos.isNotEmpty()) add(Tile.Virtual(Source.Videos, "Vídeos", videos.first(), videos.size, Icons.Rounded.PlayCircle))
            prefs.groups.forEach { g -> add(Tile.GroupTile(g, albums.filter { it.path in g.albums })) }
            rest.filter { it.path !in grouped }.forEach { add(Tile.AlbumTile(it)) }
        }
    }

    fun newAlbum() = prompt(
        PromptSpec("Nuevo álbum", "Crear", "Nombre del álbum", "Se crea en Imágenes/") { name ->
            if (name.isNotBlank()) {
                val path = newAlbumPath(name)
                Stores.albums.update { it.copy(empty = (it.empty + path).distinct()) }
                onOpenSource(Source.Album(path))
            }
        },
    )

    fun newGroup() = prompt(
        PromptSpec("Nuevo grupo", "Crear", "Nombre del grupo", "Mantén pulsado un álbum para añadirlo") { name ->
            if (name.isNotBlank()) {
                Stores.albums.update { it.copy(groups = it.groups + AlbumGroup(System.currentTimeMillis(), name)) }
            }
        },
    )

    AlbumGrid(
        padding,
        settings.albumColumns,
        onColumns = { c -> Stores.settings.update { it.copy(albumColumns = c) } },
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Álbumes", Modifier.padding(horizontal = 6.dp), subtitle = countLabel(albums.size, "álbum", "álbumes")) {
                GlassIconButton(Icons.Rounded.Add, "Crear", {
                    sheet(
                        SheetSpec(
                            "Crear",
                            listOf(
                                SheetAction("Álbum", Icons.Rounded.PhotoAlbum) { newAlbum() },
                                SheetAction("Grupo de álbumes", Icons.Rounded.CreateNewFolder) { newGroup() },
                            ),
                        ),
                    )
                }, prominent = true)
                GlassIconButton(Icons.Rounded.MoreVert, "Más", {
                    sheet(
                        SheetSpec(
                            "Álbumes",
                            listOf(
                                SheetAction("Ordenar por…", Icons.AutoMirrored.Rounded.Sort) {
                                    sheet(
                                        SheetSpec(
                                            "Ordenar por",
                                            listOf("Personalizado", "Nombre", "Número de elementos", "Más reciente").mapIndexed { i, label ->
                                                SheetAction(
                                                    if (settings.albumSort == i) "$label  ✓" else label,
                                                    Icons.AutoMirrored.Rounded.Sort,
                                                ) { Stores.settings.update { it.copy(albumSort = i) } }
                                            },
                                        ),
                                    )
                                },
                                SheetAction("Cambiar el orden", Icons.Rounded.SwapVert, onClick = onReorder),
                                SheetAction("Oculto", Icons.Rounded.Lock, onClick = onVault),
                            ),
                        ),
                    )
                })
            }
        }
        items(tiles.size, key = { tiles[it].key }) { i ->
            when (val t = tiles[i]) {
                is Tile.AlbumTile -> AlbumCard(
                    t.album.name, t.album.cover, t.album.count,
                    onClick = { onOpenSource(Source.Album(t.album.path)) },
                    onLongClick = { sheet(albumSheet(t.album, prefs.groups, null, media, actions, sheet)) },
                )
                is Tile.Virtual -> AlbumCard(
                    t.name, t.cover, t.count, icon = t.icon,
                    onClick = { onOpenSource(t.source) },
                )
                is Tile.GroupTile -> GroupCard(
                    t.group.name, t.albums,
                    onClick = { onOpenGroup(t.group.id) },
                    onLongClick = { sheet(groupSheet(t.group, prompt)) },
                )
            }
        }
    }
}

/** Cuadrícula de álbumes; pellizcar cambia el número de columnas. */
@Composable
private fun AlbumGrid(
    padding: PaddingValues,
    columns: Int,
    onColumns: ((Int) -> Unit)?,
    content: LazyGridScope.() -> Unit,
) {
    val dir = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val set by rememberUpdatedState(onColumns)
    val cols by rememberUpdatedState(columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
            start = padding.calculateStartPadding(dir) + 14.dp,
            end = padding.calculateEndPadding(dir) + 14.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var changed = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        zoom *= event.calculateZoom()
                        event.changes.forEach { it.consume() }
                        val s = set
                        if (!changed && s != null && (zoom > 1.25f || zoom < 0.8f)) {
                            val i = ALBUM_COLUMNS.indexOf(cols).coerceAtLeast(0)
                            ALBUM_COLUMNS.getOrNull(if (zoom > 1f) i - 1 else i + 1)?.let {
                                s(it)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            changed = true
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        content = content,
    )
}

private fun albumSheet(
    album: Album,
    groups: List<AlbumGroup>,
    inGroup: AlbumGroup?,
    media: List<Media>,
    actions: GalleryActions,
    sheet: (SheetSpec) -> Unit,
): SheetSpec =
    SheetSpec(
        album.name,
        buildList {
            if (inGroup != null) {
                add(
                    SheetAction("Sacar del grupo", Icons.Rounded.FolderOff) {
                        Stores.albums.update { p ->
                            p.copy(groups = p.groups.map { if (it.id == inGroup.id) it.copy(albums = it.albums - album.path) else it })
                        }
                    },
                )
            }
            groups.filter { it.id != inGroup?.id }.forEach { g ->
                add(
                    SheetAction("Añadir a «${g.name}»", Icons.Rounded.Folder) {
                        Stores.albums.update { p ->
                            p.copy(
                                groups = p.groups.map {
                                    when (it.id) {
                                        g.id -> it.copy(albums = (it.albums + album.path).distinct())
                                        else -> it.copy(albums = it.albums - album.path)
                                    }
                                },
                            )
                        }
                    },
                )
            }
            val items = media.filter { it.album == album.path }
            if (items.isNotEmpty()) {
                add(SheetAction("Ocultar álbum", Icons.Rounded.VisibilityOff) { actions.hide(items) })
            }
            add(
                SheetAction("Eliminar álbum", Icons.Rounded.Delete, danger = true) {
                    if (items.isEmpty()) {
                        Stores.albums.update { it.copy(empty = it.empty - album.path) }
                    } else {
                        sheet(deleteAlbumSheet(album.name, items, actions))
                    }
                },
            )
        },
        subtitle = countLabel(album.count),
    )

private fun groupSheet(group: AlbumGroup, prompt: (PromptSpec) -> Unit): SheetSpec = SheetSpec(
    group.name,
    listOf(
        SheetAction("Cambiar el nombre", Icons.Rounded.Edit) {
            prompt(
                PromptSpec("Cambiar el nombre", "Guardar", group.name) { name ->
                    if (name.isNotBlank()) {
                        Stores.albums.update { p -> p.copy(groups = p.groups.map { if (it.id == group.id) it.copy(name = name) else it }) }
                    }
                },
            )
        },
        SheetAction("Desagrupar", Icons.Rounded.FolderOff, danger = true) {
            Stores.albums.update { p -> p.copy(groups = p.groups.filter { it.id != group.id }) }
        },
    ),
    subtitle = "Desagrupar no borra los álbumes",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    name: String,
    cover: Media?,
    count: Int,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val p = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.combinedClickable(
            remember { MutableInteractionSource() }, null,
            onClick = onClick,
            onLongClick = onLongClick?.let {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(p.content.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = p.rim * 0.25f), RoundedCornerShape(20.dp)),
        ) {
            if (cover != null) {
                AsyncImage(cover.thumb(), name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (icon != null || cover == null) {
                Icon(
                    icon ?: Icons.Rounded.PhotoAlbum, null,
                    Modifier.align(if (cover == null) Alignment.Center else Alignment.BottomStart).padding(8.dp).size(if (cover == null) 36.dp else 20.dp),
                    tint = if (cover == null) p.contentSecondary else Color.White,
                )
            }
        }
        Text(
            name, Modifier.padding(start = 4.dp, top = 8.dp),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(count.toString(), Modifier.padding(start = 4.dp), fontSize = 13.sp, color = p.contentSecondary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(name: String, albums: List<Album>, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.combinedClickable(
            remember { MutableInteractionSource() }, null,
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        ),
    ) {
        GlassSurface(Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(20.dp), frost = 10.dp) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0..1) {
                            val a = albums.getOrNull(row * 2 + col)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(p.content.copy(alpha = 0.08f)),
                            ) {
                                a?.cover?.let { AsyncImage(it.thumb(192), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp), tint = p.accentFg)
            Spacer(Modifier.width(4.dp))
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(countLabel(albums.size, "álbum", "álbumes"), Modifier.padding(start = 4.dp), fontSize = 13.sp, color = p.contentSecondary)
    }
}

/** Álbumes de un grupo. */
@Composable
fun GroupScreen(groupId: Long, padding: PaddingValues, onBack: () -> Unit, onOpenSource: (Source) -> Unit) {
    val actions = LocalActions.current
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val sheet = LocalActionSheet.current
    val group = prefs.groups.firstOrNull { it.id == groupId }
    val albums = remember(media, prefs, group) {
        val all = buildAlbums(media, prefs).associateBy { it.path }
        group?.albums?.mapNotNull { all[it] }.orEmpty()
    }
    AlbumGrid(padding, settings.albumColumns, null) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            BackTitle(group?.name ?: "Grupo", onBack, subtitle = countLabel(albums.size, "álbum", "álbumes"))
        }
        if (albums.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("Grupo vacío", "Mantén pulsado un álbum y elige «Añadir a…».")
            }
        }
        items(albums.size, key = { albums[it].path }) { i ->
            val a = albums[i]
            AlbumCard(
                a.name, a.cover, a.count,
                onClick = { onOpenSource(Source.Album(a.path)) },
                onLongClick = { sheet(albumSheet(a, prefs.groups, group, media, actions, sheet)) },
            )
        }
    }
}

/** Elegir el álbum al que mover o copiar. */
@Composable
fun AlbumPickerScreen(items: List<Media>, copy: Boolean, padding: PaddingValues, onDone: () -> Unit) {
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val prompt = LocalPrompt.current
    val albums = remember(media, prefs) { sortAlbums(buildAlbums(media, prefs), 0, prefs.order) }
    val from = items.map { it.album }.distinct().singleOrNull()
    AlbumGrid(padding, 3, null) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            BackTitle(if (copy) "Copiar a" else "Mover a", onDone, subtitle = countLabel(items.size))
        }
        item(key = "new") {
            AlbumCard("Nuevo álbum", null, 0, icon = Icons.Rounded.Add, onClick = {
                prompt(
                    PromptSpec("Nuevo álbum", if (copy) "Copiar" else "Mover", "Nombre del álbum") { name ->
                        if (name.isNotBlank()) {
                            actions.moveTo(items, newAlbumPath(name), copy)
                            onDone()
                        }
                    },
                )
            })
        }
        items(albums.size, key = { albums[it].path }) { i ->
            val a = albums[i]
            Box(Modifier.graphicsLayer { alpha = if (a.path == from) 0.4f else 1f }) {
                AlbumCard(a.name, a.cover, a.count, onClick = {
                    if (a.path != from) {
                        actions.moveTo(items, a.path, copy)
                        onDone()
                    }
                })
            }
        }
    }
}

/** Ordenar álbumes a mano: arrastrar desde el asa. */
@Composable
fun ReorderAlbumsScreen(padding: PaddingValues, onBack: () -> Unit) {
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val initial = remember {
        sortAlbums(buildAlbums(media, prefs), settings.albumSort, prefs.order)
            .filter { !dev.liquidgallery.data.isPinnedAlbum(it.path) }
    }
    var list by remember { mutableStateOf(initial) }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 76.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val p = LocalGlass.current

    fun save() {
        Stores.albums.update { it.copy(order = list.map { a -> a.path }) }
        Stores.settings.update { it.copy(albumSort = 0) }
    }
    androidx.activity.compose.BackHandler {
        save()
        onBack()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        userScrollEnabled = dragging < 0,
    ) {
        item {
            BackTitle("Cambiar el orden", { save(); onBack() }, subtitle = "Arrastra desde el asa")
            SectionLabel("Cámara siempre va primero")
        }
        itemsIndexed(list, key = { _, a -> a.path }) { i, a ->
            val lifted by animateFloatAsState(if (dragging == i) 1f else 0f, label = "lift")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .zIndex(if (dragging == i) 1f else 0f)
                    .offset { IntOffset(0, if (dragging == i) dragY.roundToInt() else 0) }
                    .graphicsLayer {
                        scaleX = 1f + lifted * 0.03f
                        scaleY = 1f + lifted * 0.03f
                    }
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.content.copy(alpha = if (dragging == i) 0.14f else 0.06f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(p.content.copy(alpha = 0.08f))) {
                    a.cover?.let { AsyncImage(it.thumb(192), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(a.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(countLabel(a.count), fontSize = 13.sp, color = p.contentSecondary)
                }
                Icon(
                    Icons.Rounded.DragHandle, "Arrastrar",
                    Modifier
                        .size(44.dp)
                        .padding(10.dp)
                        .pointerInput(a.path) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = list.indexOfFirst { it.path == a.path }
                                    dragY = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { dragging = -1; dragY = 0f },
                                onDragCancel = { dragging = -1; dragY = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val from = dragging
                                if (from < 0) return@detectDragGestures
                                val to = when {
                                    dragY > rowPx / 2 && from < list.lastIndex -> from + 1
                                    dragY < -rowPx / 2 && from > 0 -> from - 1
                                    else -> from
                                }
                                if (to != from) {
                                    list = list.toMutableList().apply { add(to, removeAt(from)) }
                                    dragY -= (to - from) * rowPx
                                    dragging = to
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                    tint = p.contentSecondary,
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            GlassCard {
                Text("El orden se guarda al volver.", fontSize = 14.sp, color = p.contentSecondary)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Por nombre", false, { list = list.sortedBy { it.name.lowercase() } })
                    GlassChip("Por cantidad", false, { list = list.sortedByDescending { it.count } })
                }
            }
        }
    }
}
