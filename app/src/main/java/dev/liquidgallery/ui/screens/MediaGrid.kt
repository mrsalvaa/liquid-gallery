package dev.liquidgallery.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.liquidgallery.data.Media
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.util.dayLabel
import dev.liquidgallery.util.duration
import dev.liquidgallery.util.localDate
import dev.liquidgallery.util.monthLabel
import dev.liquidgallery.util.thumb
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Columnas posibles al pellizcar, como en Samsung. Desde 6 se agrupa por meses. */
val GRID_COLUMNS = listOf(2, 3, 4, 6, 8)

/** Selección múltiple de una cuadrícula. */
@Stable
class Selection {
    var active by mutableStateOf(false)
    var ids by mutableStateOf<Set<Long>>(emptySet())

    fun toggle(id: Long) {
        active = true
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() {
        active = false
        ids = emptySet()
    }
}

data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: (List<Media>) -> Unit,
)

/** Lo que pinta la barra de selección flotante (arriba el contador, abajo las acciones). */
class SelectionBarSpec(
    val selection: Selection,
    /** Todos los elementos de la cuadrícula (para «Todo»). */
    val all: () -> List<Media>,
    val actions: () -> List<SelectionAction>,
) {
    fun selected(): List<Media> = all().filter { it.id in selection.ids }
}

val LocalSelectionBar = staticCompositionLocalOf<(SelectionBarSpec?) -> Unit> { {} }

private sealed interface Entry {
    data class Header(val key: String, val label: String, val ids: List<Long>) : Entry
    data class Cell(val media: Media, val index: Int) : Entry
}

private fun buildEntries(items: List<Media>, byMonth: Boolean, grouped: Boolean): List<Entry> {
    if (!grouped) return items.mapIndexed { i, m -> Entry.Cell(m, i) }
    val out = ArrayList<Entry>(items.size + 64)
    var current: Any? = null
    var headerAt = -1
    val ids = ArrayList<Long>()
    fun closeHeader() {
        if (headerAt >= 0) out[headerAt] = (out[headerAt] as Entry.Header).copy(ids = ids.toList())
        ids.clear()
    }
    items.forEachIndexed { i, m ->
        val day = localDate(m.date)
        val key: Any = if (byMonth) day.year * 100 + day.monthValue else day
        if (key != current) {
            closeHeader()
            current = key
            headerAt = out.size
            out += Entry.Header("h:$key", if (byMonth) monthLabel(day) else dayLabel(day), emptyList())
        }
        ids += m.id
        out += Entry.Cell(m, i)
    }
    closeHeader()
    return out
}

/**
 * Cuadrícula de fotos y vídeos: cabeceras por día (o por mes con muchas columnas), pellizcar para
 * cambiar de columnas, mantener pulsado y arrastrar para seleccionar y barra de desplazamiento rápido.
 */
@Composable
fun MediaGrid(
    items: List<Media>,
    padding: PaddingValues,
    selection: Selection,
    onOpen: (Media) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    onColumns: ((Int) -> Unit)? = null,
    grouped: Boolean = true,
    state: LazyGridState = rememberLazyGridState(),
    /** Elemento al que volver (p. ej. el último que se vio en el visor). */
    focusId: Long? = null,
    selectionBar: SelectionBarSpec? = null,
    badge: (@Composable (Media) -> Unit)? = null,
    header: LazyGridScope.() -> Unit = {},
) {
    val p = LocalGlass.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val byMonth = columns >= 6
    val entries = remember(items, byMonth, grouped) { buildEntries(items, byMonth, grouped) }
    val latestEntries by rememberUpdatedState(entries)
    val latestItems by rememberUpdatedState(items)
    val open by rememberUpdatedState(onOpen)
    val setColumns by rememberUpdatedState(onColumns)
    val cols by rememberUpdatedState(columns)

    // Barra de selección flotante mientras se selecciona.
    val postBar = LocalSelectionBar.current
    if (selection.active && selectionBar != null) {
        DisposableEffect(selectionBar) {
            postBar(selectionBar)
            onDispose { postBar(null) }
        }
    }
    BackHandler(enabled = selection.active) { selection.clear() }

    // Si desaparecen elementos seleccionados (borrados, movidos), se quitan de la selección.
    LaunchedEffect(items) {
        if (selection.ids.isNotEmpty()) {
            val present = items.mapTo(HashSet()) { it.id }
            val kept = selection.ids.filterTo(HashSet()) { it in present }
            if (kept.size != selection.ids.size) selection.ids = kept
        }
    }

    LaunchedEffect(focusId, entries) {
        val id = focusId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is Entry.Cell && it.media.id == id }
        if (index < 0) return@LaunchedEffect
        // El índice real cuenta los elementos de [header] que hay antes.
        val offset = state.layoutInfo.totalItemsCount - entries.size
        val target = index + offset.coerceAtLeast(0)
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (!visible) state.scrollToItem(target)
    }

    Box(modifier.fillMaxSize()) {
        val dir = LocalLayoutDirection.current
        val density = LocalDensity.current
        val edge = with(density) { 96.dp.toPx() }
        var dragPointer by remember { mutableStateOf<Offset?>(null) }
        var autoScroll by remember { mutableStateOf<Job?>(null) }

        fun entryAt(pos: Offset): Entry.Cell? {
            val info = state.layoutInfo
            val offset = info.totalItemsCount - latestEntries.size
            val hit = info.visibleItemsInfo.firstOrNull {
                pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
                    pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
            } ?: return null
            return latestEntries.getOrNull(hit.index - offset) as? Entry.Cell
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding(),
                start = padding.calculateStartPadding(dir) + 2.dp,
                end = padding.calculateEndPadding(dir) + 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                // Pellizcar con dos dedos cambia el número de columnas.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var zoom = 1f
                        var changed = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom *= event.calculateZoom()
                                event.changes.forEach { it.consume() }
                                val set = setColumns
                                if (!changed && set != null && (zoom > 1.25f || zoom < 0.8f)) {
                                    val i = GRID_COLUMNS.indexOf(cols).coerceAtLeast(0)
                                    val next = GRID_COLUMNS.getOrNull(if (zoom > 1f) i - 1 else i + 1)
                                    if (next != null) {
                                        set(next)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    changed = true
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // Tocar abre (o selecciona); mantener pulsado y arrastrar selecciona un rango.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull CANCEL
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull CANCEL
                                if (change.changedToUp()) {
                                    return@withTimeoutOrNull if (change.isConsumed) CANCEL else TAP
                                }
                                if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    return@withTimeoutOrNull CANCEL
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            0
                        }
                        when (outcome) {
                            CANCEL -> return@awaitEachGesture
                            TAP -> {
                                val cell = entryAt(down.position) ?: return@awaitEachGesture
                                if (selection.active) selection.toggle(cell.media.id) else open(cell.media)
                                return@awaitEachGesture
                            }
                        }
                        // Pulsación larga: empieza (o amplía) la selección y sigue al dedo.
                        val start = entryAt(down.position) ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val base = selection.ids
                        val adding = start.media.id !in base
                        selection.active = true
                        var last = start.index
                        fun applyRange(to: Int) {
                            val all = latestItems
                            val range = minOf(start.index, to)..maxOf(start.index, to)
                            val ids = range.mapNotNull { all.getOrNull(it)?.id }
                            selection.ids = if (adding) base + ids else base - ids.toSet()
                        }
                        applyRange(start.index)
                        dragPointer = down.position
                        autoScroll = scope.launch {
                            while (isActive) {
                                val y = dragPointer?.y ?: break
                                val h = state.layoutInfo.viewportSize.height
                                val speed = when {
                                    y < edge -> -(edge - y) / edge * 28f
                                    y > h - edge -> (y - (h - edge)) / edge * 28f
                                    else -> 0f
                                }
                                if (speed != 0f) {
                                    state.scrollBy(speed)
                                    dragPointer?.let { pos -> entryAt(pos)?.let { if (it.index != last) { last = it.index; applyRange(it.index) } } }
                                }
                                delay(16)
                            }
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                dragPointer = change.position
                                val cell = entryAt(change.position)
                                if (cell != null && cell.index != last) {
                                    last = cell.index
                                    applyRange(cell.index)
                                }
                            }
                        } finally {
                            dragPointer = null
                            autoScroll?.cancel()
                        }
                    }
                },
        ) {
            header()
            items(
                count = entries.size,
                key = { i ->
                    when (val e = entries[i]) {
                        is Entry.Header -> e.key
                        is Entry.Cell -> e.media.id
                    }
                },
                span = { i -> if (entries[i] is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                contentType = { i -> if (entries[i] is Entry.Header) 0 else 1 },
            ) { i ->
                when (val e = entries[i]) {
                    is Entry.Header -> DateHeader(e, selection)
                    is Entry.Cell -> Thumbnail(
                        e.media,
                        selecting = selection.active,
                        selected = e.media.id in selection.ids,
                        small = columns >= 6,
                        badge = badge,
                    )
                }
            }
        }

        if (entries.size > 80) {
            FastScroller(state, padding, entries.size) { index ->
                val offset = state.layoutInfo.totalItemsCount - latestEntries.size
                val e = latestEntries.getOrNull(index - offset) ?: latestEntries.getOrNull((index - offset).coerceAtLeast(0))
                when (e) {
                    is Entry.Cell -> localDate(e.media.date).let { if (byMonth) monthLabel(it) else dayLabel(it) }
                    is Entry.Header -> e.label
                    null -> ""
                }
            }
        }
    }
}

private const val TAP = 1
private const val CANCEL = 2

@Composable
private fun DateHeader(h: Entry.Header, selection: Selection) {
    val p = LocalGlass.current
    val all = h.ids.isNotEmpty() && selection.ids.containsAll(h.ids)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection.active) {
            CheckCircle(
                all,
                Modifier
                    .padding(end = 10.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {
                        selection.ids = if (all) selection.ids - h.ids.toSet() else selection.ids + h.ids
                    },
            )
        }
        Text(h.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.content)
    }
}

@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Int = 22) {
    val p = LocalGlass.current
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (checked) p.accent else Color.Black.copy(alpha = 0.25f))
            .border(1.5.dp, if (checked) p.accent else Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size((size * 0.7f).dp), tint = p.onAccent)
    }
}

@Composable
private fun Thumbnail(
    m: Media,
    selecting: Boolean,
    selected: Boolean,
    small: Boolean,
    badge: (@Composable (Media) -> Unit)?,
) {
    val p = LocalGlass.current
    val inset by animateFloatAsState(if (selected) 0.86f else 1f, label = "inset")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(if (small) 2.dp else 4.dp))
            .background(p.content.copy(alpha = 0.06f)),
    ) {
        AsyncImage(
            model = m.thumb(if (small) 192 else 384),
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = inset
                    scaleY = inset
                    clip = inset < 1f
                    shape = RoundedCornerShape(8.dp)
                },
        )
        if (!small && (m.isVideo || m.isGif)) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m.isVideo) {
                    Text(duration(m.duration), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
                } else {
                    Text("GIF", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else if (small && m.isVideo) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.BottomEnd).size(14.dp), tint = Color.White)
        }
        if (!small && m.favorite && !selecting) {
            Icon(
                Icons.Rounded.Favorite, null,
                Modifier.align(Alignment.BottomStart).padding(5.dp).size(13.dp),
                tint = Color.White,
            )
        }
        badge?.invoke(m)
        if (selecting) {
            CheckCircle(selected, Modifier.align(Alignment.TopStart).padding(6.dp), size = if (small) 16 else 22)
        }
    }
}

/** Asa a la derecha para saltar por la lista, con la fecha mientras se arrastra. */
@Composable
private fun FastScroller(state: LazyGridState, padding: PaddingValues, count: Int, labelAt: (Int) -> String) {
    val p = LocalGlass.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scrolling by remember { derivedStateOf { state.isScrollInProgress } }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true else {
            delay(1500)
            visible = false
        }
    }
    val total = state.layoutInfo.totalItemsCount.coerceAtLeast(1)
    val fraction by remember {
        derivedStateOf {
            if (dragging) dragFraction else {
                val info = state.layoutInfo
                val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
                (state.firstVisibleItemIndex.toFloat() / (info.totalItemsCount - visibleCount).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
    ) {
        val thumbH = 44.dp
        val track = with(LocalDensity.current) { (maxHeight - thumbH).toPx() }.coerceAtLeast(1f)
        AnimatedVisibility(visible, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.offset { IntOffset(0, (fraction * track).roundToInt()) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dragging) {
                        GlassSurface(shape = CircleShape, strong = true, frost = 10.dp) {
                            Text(
                                labelAt((dragFraction * (total - 1)).roundToInt()),
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(width = 22.dp, height = thumbH)
                            .clip(RoundedCornerShape(11.dp))
                            .background(p.content.copy(alpha = if (dragging) 0.75f else 0.45f))
                            .pointerInput(count) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragFraction = fraction
                                        dragging = true
                                    },
                                    onDragEnd = { dragging = false },
                                    onDragCancel = { dragging = false },
                                ) { change, dy ->
                                    change.consume()
                                    dragFraction = (dragFraction + dy / track).coerceIn(0f, 1f)
                                    val target = (dragFraction * (state.layoutInfo.totalItemsCount - 1)).roundToInt()
                                    scope.launch { state.scrollToItem(target) }
                                }
                            },
                    )
                }
            }
        }
    }
}
