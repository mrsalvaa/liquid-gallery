package dev.liquidgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.data.albumName
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.ScreenTitle
import dev.liquidgallery.ui.glass.SheetAction
import dev.liquidgallery.ui.glass.SheetSpec
import dev.liquidgallery.util.countLabel
import java.util.concurrent.TimeUnit

/** Acciones de la barra de selección en las cuadrículas normales. */
@Composable
fun standardSelectionBar(selection: Selection, items: List<Media>): SelectionBarSpec {
    val actions = LocalActions.current
    val sheet = LocalActionSheet.current
    val current by rememberUpdatedState(items)
    return remember(selection) {
        SelectionBarSpec(
            selection,
            all = { current },
            actions = {
                listOf(
                    SelectionAction("Compartir", Icons.Rounded.Share) { actions.share(it) },
                    SelectionAction("Favorito", Icons.Rounded.FavoriteBorder) { sel ->
                        actions.favorite(sel, !sel.all { it.favorite }) { selection.clear() }
                    },
                    SelectionAction("Álbum", Icons.AutoMirrored.Rounded.DriveFileMove) { sel ->
                        sheet(
                            SheetSpec(
                                countLabel(sel.size),
                                listOf(
                                    SheetAction("Mover a un álbum", Icons.AutoMirrored.Rounded.DriveFileMove) {
                                        actions.pickAlbum(sel, false)
                                    },
                                    SheetAction("Copiar a un álbum", Icons.Rounded.ContentCopy) {
                                        actions.pickAlbum(sel, true)
                                    },
                                ),
                            ),
                        )
                    },
                    SelectionAction("Eliminar", Icons.Rounded.Delete, danger = true) { sel ->
                        actions.trash(sel) { selection.clear() }
                    },
                )
            },
        )
    }
}

/** Pestaña Imágenes: todo, agrupado por días. */
@Composable
fun PhotosScreen(
    padding: PaddingValues,
    focusId: Long?,
    onOpen: (Source, Media) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val loaded by MediaRepo.loaded.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val items = remember(media, prefs.hidden) { itemsFor(Source.All, media, emptyList(), prefs) }
    val selection = remember { Selection() }
    val sheet = LocalActionSheet.current
    val state = rememberLazyGridState()

    MediaGrid(
        items = items,
        padding = padding,
        selection = selection,
        onOpen = { onOpen(Source.All, it) },
        columns = settings.gridColumns,
        onColumns = { c -> Stores.settings.update { it.copy(gridColumns = c) } },
        state = state,
        focusId = focusId,
        selectionBar = standardSelectionBar(selection, items),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(
                "Imágenes",
                Modifier.padding(horizontal = 14.dp),
                subtitle = if (loaded) countLabel(items.size) else "Cargando…",
            ) {
                GlassIconButton(Icons.Rounded.Search, "Buscar", onSearch)
                GlassIconButton(Icons.Rounded.MoreVert, "Más", {
                    sheet(
                        SheetSpec(
                            "Imágenes",
                            listOf(
                                SheetAction("Seleccionar", Icons.Rounded.CheckCircle) { selection.active = true },
                                SheetAction("Ajustes", Icons.Rounded.Settings, onClick = onSettings),
                            ),
                        ),
                    )
                })
            }
        }
        if (loaded && items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("No hay fotos ni vídeos", "Las fotos que hagas o descargues aparecerán aquí.")
            }
        }
    }
}

/** Cuadrícula de un álbum, favoritos, vídeos o la papelera. */
@Composable
fun SourceGridScreen(
    source: Source,
    padding: PaddingValues,
    focusId: Long?,
    onBack: () -> Unit,
    onOpen: (Source, Media) -> Unit,
) {
    val media by MediaRepo.media.collectAsStateWithLifecycle()
    val trash by MediaRepo.trash.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val items = remember(source, media, trash, prefs.hidden) { itemsFor(source, media, trash, prefs) }
    val selection = remember { Selection() }
    val sheet = LocalActionSheet.current
    val actions = LocalActions.current
    val isTrash = source == Source.Trash
    val title = when (source) {
        is Source.Album -> items.firstOrNull()?.albumName ?: albumName(source.path, null)
        Source.Favorites -> "Favoritos"
        Source.Videos -> "Vídeos"
        Source.Trash -> "Papelera"
        Source.All -> "Imágenes"
        is Source.Search -> source.query
    }
    val current by rememberUpdatedState(items)
    val bar = if (isTrash) {
        remember(selection) {
            SelectionBarSpec(
                selection,
                all = { current },
                actions = {
                    listOf(
                        SelectionAction("Restaurar", Icons.Rounded.Restore) { actions.restore(it) { selection.clear() } },
                        SelectionAction("Eliminar", Icons.Rounded.DeleteForever, danger = true) {
                            actions.deleteForever(it) { selection.clear() }
                        },
                    )
                },
            )
        }
    } else {
        standardSelectionBar(selection, items)
    }
    val state = rememberLazyGridState()
    var columns by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    val cols = if (columns > 0) columns else settings.gridColumns

    MediaGrid(
        items = items,
        padding = padding,
        selection = selection,
        onOpen = { onOpen(source, it) },
        columns = cols,
        onColumns = { columns = it },
        grouped = !isTrash,
        state = state,
        focusId = focusId,
        selectionBar = bar,
        badge = if (isTrash) { m -> TrashBadge(m) } else null,
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            BackTitle(
                title,
                onBack,
                Modifier.padding(horizontal = 14.dp),
                subtitle = if (isTrash) {
                    "Se eliminan para siempre a los 30 días"
                } else {
                    countLabel(items.size)
                },
            ) {
                if (isTrash) {
                    if (items.isNotEmpty()) {
                        GlassIconButton(Icons.Rounded.DeleteSweep, "Vaciar papelera", {
                            sheet(
                                SheetSpec(
                                    "¿Vaciar la papelera?",
                                    listOf(
                                        SheetAction("Eliminar ${countLabel(items.size)} para siempre", Icons.Rounded.DeleteForever, danger = true) {
                                            actions.deleteForever(items)
                                        },
                                        SheetAction("Restaurar todo", Icons.Rounded.Restore) { actions.restore(items) },
                                    ),
                                    subtitle = "No se puede deshacer.",
                                ),
                            )
                        })
                    }
                } else {
                    GlassIconButton(Icons.Rounded.MoreVert, "Más", {
                        val list = buildList {
                            add(SheetAction("Seleccionar", Icons.Rounded.CheckCircle) { selection.active = true })
                            if (source is Source.Album) {
                                val hidden = source.path in prefs.hidden
                                add(
                                    SheetAction(
                                        if (hidden) "Mostrar álbum" else "Ocultar álbum",
                                        if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    ) { toggleHidden(source.path) },
                                )
                                if (items.isNotEmpty()) {
                                    add(
                                        SheetAction("Eliminar álbum", Icons.Rounded.Delete, danger = true) {
                                            actions.trash(items) { onBack() }
                                        },
                                    )
                                }
                            }
                            if (source == Source.Favorites && items.isNotEmpty()) {
                                add(SheetAction("Quitar todos de favoritos", Icons.Rounded.Favorite) { actions.favorite(items, false) })
                            }
                        }
                        sheet(SheetSpec(title, list))
                    })
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                when (source) {
                    Source.Trash -> EmptyState("La papelera está vacía", "Lo que elimines se queda aquí 30 días.")
                    Source.Favorites -> EmptyState("Sin favoritos", "Toca el corazón al ver una foto.")
                    else -> EmptyState("Álbum vacío", "Mueve o copia fotos a este álbum.")
                }
            }
        }
    }
}

fun toggleHidden(path: String) {
    Stores.albums.update { if (path in it.hidden) it.copy(hidden = it.hidden - path) else it.copy(hidden = it.hidden + path) }
}

@Composable
private fun TrashBadge(m: Media) {
    val days = TimeUnit.MILLISECONDS.toDays((m.expires - System.currentTimeMillis()).coerceAtLeast(0)).toInt()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Text(
            if (days <= 0) "Hoy" else "$days d",
            Modifier
                .padding(5.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
