package dev.liquidgallery.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.data.Vault
import dev.liquidgallery.data.VaultLock
import dev.liquidgallery.data.albumName
import dev.liquidgallery.ui.glass.GlassButton
import dev.liquidgallery.ui.glass.GlassCard
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.LocalGlass
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
                    SelectionAction("Eliminar", Icons.Rounded.Delete, danger = true) { sel ->
                        actions.trash(sel) { selection.clear() }
                    },
                    SelectionAction("Más", Icons.Rounded.MoreHoriz) { sel ->
                        sheet(moreSelectionSheet(sel, actions) { selection.clear() })
                    },
                )
            },
        )
    }
}

/** Mover, copiar, archivar y ocultar lo seleccionado (o un solo elemento). */
fun moreSelectionSheet(sel: List<Media>, actions: GalleryActions, done: () -> Unit): SheetSpec {
    val archived = Stores.albums.value.archived
    val allArchived = sel.isNotEmpty() && sel.all { it.id in archived }
    return SheetSpec(
        countLabel(sel.size),
        listOf(
            SheetAction("Mover a un álbum", Icons.AutoMirrored.Rounded.DriveFileMove) { actions.pickAlbum(sel, false) },
            SheetAction("Copiar a un álbum", Icons.Rounded.ContentCopy) { actions.pickAlbum(sel, true) },
            if (allArchived) {
                SheetAction("Desarchivar", Icons.Rounded.Unarchive) { actions.archive(sel, false, done) }
            } else {
                SheetAction("Archivar", Icons.Rounded.Archive) { actions.archive(sel, true, done) }
            },
            SheetAction("Ocultar", Icons.Rounded.VisibilityOff) { actions.hide(sel, done) },
        ),
        subtitle = "Ocultar lo mueve a la bóveda privada, protegida con tu huella o PIN",
    )
}

/** Pestaña Imágenes: todo menos lo archivado, agrupado por días. */
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
    val items = remember(media, prefs.archived) { itemsFor(Source.All, media, emptyList(), prefs) }
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

/** Confirmación antes de mandar un álbum entero a la papelera. */
fun deleteAlbumSheet(name: String, items: List<Media>, actions: GalleryActions, after: () -> Unit = {}) = SheetSpec(
    "¿Eliminar «$name»?",
    listOf(
        SheetAction("Mover ${countLabel(items.size)} a la papelera", Icons.Rounded.Delete, danger = true) {
            actions.trash(items, after)
        },
    ),
    subtitle = "Podrás recuperarlos desde la papelera durante 30 días.",
)

fun deleteHiddenSheet(sel: List<Media>, actions: GalleryActions, after: () -> Unit) = SheetSpec(
    "¿Eliminar para siempre?",
    listOf(
        SheetAction("Eliminar ${countLabel(sel.size)}", Icons.Rounded.DeleteForever, danger = true) {
            actions.deleteHidden(sel, after)
        },
    ),
    subtitle = "Lo oculto no pasa por la papelera: no se puede deshacer.",
)

/** Cuadrícula de un álbum, favoritos, vídeos, archivo, papelera u oculto. */
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
    val vault by Vault.media.collectAsStateWithLifecycle()
    val unlocked by VaultLock.unlocked.collectAsStateWithLifecycle()
    val prefs by Stores.albums.state.collectAsStateWithLifecycle()
    val settings by Stores.settings.state.collectAsStateWithLifecycle()
    val items = remember(source, media, trash, prefs.archived, vault) { itemsFor(source, media, trash, prefs, vault) }
    val selection = remember { Selection() }
    val sheet = LocalActionSheet.current
    val actions = LocalActions.current
    val isTrash = source == Source.Trash
    val isVault = source == Source.Vault
    val title = when (source) {
        is Source.Album -> items.firstOrNull()?.albumName ?: albumName(source.path, null)
        Source.Favorites -> "Favoritos"
        Source.Videos -> "Vídeos"
        Source.Trash -> "Papelera"
        Source.Archive -> "Archivo"
        Source.Vault -> "Oculto"
        Source.All -> "Imágenes"
        is Source.Search -> source.query
    }

    if (isVault && !unlocked) {
        VaultLocked(padding, onBack)
        return
    }

    val current by rememberUpdatedState(items)
    val bar = when {
        isTrash -> remember(selection) {
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
        isVault -> remember(selection) {
            SelectionBarSpec(
                selection,
                all = { current },
                actions = {
                    listOf(
                        SelectionAction("Mostrar", Icons.Rounded.Visibility) { actions.unhide(it) { selection.clear() } },
                        SelectionAction("Compartir", Icons.Rounded.Share) { actions.share(it) },
                        SelectionAction("Eliminar", Icons.Rounded.DeleteForever, danger = true) { sel ->
                            sheet(deleteHiddenSheet(sel, actions) { selection.clear() })
                        },
                    )
                },
            )
        }
        else -> standardSelectionBar(selection, items)
    }
    val state = rememberLazyGridState()
    var columns by rememberSaveable { mutableIntStateOf(-1) }
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
                subtitle = when {
                    isTrash -> "Se eliminan para siempre a los 30 días"
                    isVault -> "Solo tú puedes verlo · ${countLabel(items.size)}"
                    source == Source.Archive -> "No salen en Imágenes · ${countLabel(items.size)}"
                    else -> countLabel(items.size)
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
                                if (items.isNotEmpty()) {
                                    add(SheetAction("Ocultar álbum", Icons.Rounded.VisibilityOff) { actions.hide(items) { onBack() } })
                                }
                                add(
                                    SheetAction("Eliminar álbum", Icons.Rounded.Delete, danger = true) {
                                        if (items.isEmpty()) {
                                            Stores.albums.update { it.copy(empty = it.empty - source.path) }
                                            onBack()
                                        } else {
                                            sheet(deleteAlbumSheet(title, items, actions) { onBack() })
                                        }
                                    },
                                )
                            }
                            if (source == Source.Favorites && items.isNotEmpty()) {
                                add(SheetAction("Quitar todos de favoritos", Icons.Rounded.Favorite) { actions.favorite(items, false) })
                            }
                            if (isVault && items.isNotEmpty()) {
                                add(SheetAction("Mostrar todo", Icons.Rounded.Visibility) { actions.unhide(items) })
                            }
                            if (isVault) {
                                add(SheetAction("Bloquear ahora", Icons.Rounded.Lock) { VaultLock.lock() })
                            }
                        }
                        sheet(SheetSpec(title, list))
                    })
                }
            }
        }
        if (isVault) {
            item(key = "warning", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Lo oculto se guarda dentro de la app. Si la desinstalas sin mostrarlo antes, se pierde.",
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    color = LocalGlass.current.contentSecondary,
                )
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                when (source) {
                    Source.Trash -> EmptyState("La papelera está vacía", "Lo que elimines se queda aquí 30 días.")
                    Source.Favorites -> EmptyState("Sin favoritos", "Toca el corazón al ver una foto.")
                    Source.Archive -> EmptyState("Nada archivado", "Archiva capturas o memes para quitarlos de Imágenes.")
                    Source.Vault -> EmptyState("Nada oculto", "Selecciona fotos y elige Más → Ocultar.")
                    else -> EmptyState("Álbum vacío", "Mueve o copia fotos a este álbum.")
                }
            }
        }
    }
}

/** Pantalla de Oculto mientras está bloqueado. */
@Composable
fun VaultLocked(padding: PaddingValues, onBack: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    val p = LocalGlass.current
    var error by remember { mutableStateOf<String?>(null) }
    // Al entrar se pide directamente la huella o el PIN.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (activity != null) VaultLock.unlock(activity) { _, reason -> error = reason }
    }
    Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
        BackTitle("Oculto", onBack)
        Box(Modifier.fillMaxWidth().padding(top = 60.dp)) {
            GlassCard {
                Icon(Icons.Rounded.Lock, null, Modifier.size(44.dp).align(Alignment.CenterHorizontally), tint = p.accentFg)
                Text(
                    "Oculto está bloqueado",
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    error ?: "Usa tu huella, cara o el PIN del móvil.",
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                    fontSize = 14.sp, color = p.contentSecondary, textAlign = TextAlign.Center,
                )
                GlassButton("Desbloquear", {
                    if (activity != null) VaultLock.unlock(activity) { _, reason -> error = reason }
                }, Modifier.fillMaxWidth(), prominent = true)
            }
        }
    }
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
