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
import dev.liquidgallery.R
import dev.liquidgallery.util.str

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
                    SelectionAction(str(R.string.share), Icons.Rounded.Share) { actions.share(it) },
                    SelectionAction(str(R.string.favorite), Icons.Rounded.FavoriteBorder) { sel ->
                        actions.favorite(sel, !sel.all { it.favorite }) { selection.clear() }
                    },
                    SelectionAction(str(R.string.delete), Icons.Rounded.Delete, danger = true) { sel ->
                        actions.trash(sel) { selection.clear() }
                    },
                    SelectionAction(str(R.string.more), Icons.Rounded.MoreHoriz) { sel ->
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
            SheetAction(str(R.string.move_to_album), Icons.AutoMirrored.Rounded.DriveFileMove) { actions.pickAlbum(sel, false) },
            SheetAction(str(R.string.copy_to_album), Icons.Rounded.ContentCopy) { actions.pickAlbum(sel, true) },
            if (allArchived) {
                SheetAction(str(R.string.unarchive), Icons.Rounded.Unarchive) { actions.archive(sel, false, done) }
            } else {
                SheetAction(str(R.string.do_archive), Icons.Rounded.Archive) { actions.archive(sel, true, done) }
            },
            SheetAction(str(R.string.hide), Icons.Rounded.VisibilityOff) { actions.hide(sel, done) },
        ),
        subtitle = str(R.string.hide_subtitle),
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
                str(R.string.tab_photos),
                Modifier.padding(horizontal = 14.dp),
                subtitle = if (loaded) countLabel(items.size) else str(R.string.loading),
            ) {
                GlassIconButton(Icons.Rounded.Search, str(R.string.search), onSearch)
                GlassIconButton(Icons.Rounded.MoreVert, str(R.string.more), {
                    sheet(
                        SheetSpec(
                            str(R.string.tab_photos),
                            listOf(
                                SheetAction(str(R.string.select), Icons.Rounded.CheckCircle) { selection.active = true },
                                SheetAction(str(R.string.settings), Icons.Rounded.Settings, onClick = onSettings),
                            ),
                        ),
                    )
                })
            }
        }
        if (loaded && items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(str(R.string.no_media_title), str(R.string.no_media_subtitle))
            }
        }
    }
}

/** Confirmación antes de mandar un álbum entero a la papelera. */
fun deleteAlbumSheet(name: String, items: List<Media>, actions: GalleryActions, after: () -> Unit = {}) = SheetSpec(
    str(R.string.delete_named, name),
    listOf(
        SheetAction(str(R.string.move_n_to_trash, countLabel(items.size)), Icons.Rounded.Delete, danger = true) {
            actions.trash(items, after)
        },
    ),
    subtitle = str(R.string.trash_recover_hint),
)

fun deleteHiddenSheet(sel: List<Media>, actions: GalleryActions, after: () -> Unit) = SheetSpec(
    str(R.string.delete_forever_q),
    listOf(
        SheetAction(str(R.string.delete_n, countLabel(sel.size)), Icons.Rounded.DeleteForever, danger = true) {
            actions.deleteHidden(sel, after)
        },
    ),
    subtitle = str(R.string.hidden_no_trash),
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
        Source.Favorites -> str(R.string.favorites)
        Source.Videos -> str(R.string.videos)
        Source.Trash -> str(R.string.trash)
        Source.Archive -> str(R.string.archive)
        Source.Vault -> str(R.string.hidden)
        Source.All -> str(R.string.tab_photos)
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
                        SelectionAction(str(R.string.restore), Icons.Rounded.Restore) { actions.restore(it) { selection.clear() } },
                        SelectionAction(str(R.string.delete), Icons.Rounded.DeleteForever, danger = true) {
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
                        SelectionAction(str(R.string.unhide), Icons.Rounded.Visibility) { actions.unhide(it) { selection.clear() } },
                        SelectionAction(str(R.string.share), Icons.Rounded.Share) { actions.share(it) },
                        SelectionAction(str(R.string.delete), Icons.Rounded.DeleteForever, danger = true) { sel ->
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
                    isTrash -> str(R.string.trash_subtitle)
                    isVault -> str(R.string.vault_subtitle, countLabel(items.size))
                    source == Source.Archive -> str(R.string.archive_subtitle, countLabel(items.size))
                    else -> countLabel(items.size)
                },
            ) {
                if (isTrash) {
                    if (items.isNotEmpty()) {
                        GlassIconButton(Icons.Rounded.DeleteSweep, str(R.string.empty_trash), {
                            sheet(
                                SheetSpec(
                                    str(R.string.empty_trash_q),
                                    listOf(
                                        SheetAction(str(R.string.delete_n_forever, countLabel(items.size)), Icons.Rounded.DeleteForever, danger = true) {
                                            actions.deleteForever(items)
                                        },
                                        SheetAction(str(R.string.restore_all), Icons.Rounded.Restore) { actions.restore(items) },
                                    ),
                                    subtitle = str(R.string.cant_undo),
                                ),
                            )
                        })
                    }
                } else {
                    GlassIconButton(Icons.Rounded.MoreVert, str(R.string.more), {
                        val list = buildList {
                            add(SheetAction(str(R.string.select), Icons.Rounded.CheckCircle) { selection.active = true })
                            if (source is Source.Album) {
                                if (items.isNotEmpty()) {
                                    add(SheetAction(str(R.string.hide_album), Icons.Rounded.VisibilityOff) { actions.hide(items) { onBack() } })
                                }
                                add(
                                    SheetAction(str(R.string.delete_album), Icons.Rounded.Delete, danger = true) {
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
                                add(SheetAction(str(R.string.unfavorite_all), Icons.Rounded.Favorite) { actions.favorite(items, false) })
                            }
                            if (isVault && items.isNotEmpty()) {
                                add(SheetAction(str(R.string.unhide_all), Icons.Rounded.Visibility) { actions.unhide(items) })
                            }
                            if (isVault) {
                                add(SheetAction(str(R.string.lock_now), Icons.Rounded.Lock) { VaultLock.lock() })
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
                    str(R.string.vault_warning),
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    color = LocalGlass.current.contentSecondary,
                )
            }
        }
        if (items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                when (source) {
                    Source.Trash -> EmptyState(str(R.string.trash_empty_title), str(R.string.trash_empty_subtitle))
                    Source.Favorites -> EmptyState(str(R.string.favorites_empty_title), str(R.string.favorites_empty_subtitle))
                    Source.Archive -> EmptyState(str(R.string.archive_empty_title), str(R.string.archive_empty_subtitle))
                    Source.Vault -> EmptyState(str(R.string.vault_empty_title), str(R.string.vault_empty_subtitle))
                    else -> EmptyState(str(R.string.album_empty_title), str(R.string.album_empty_subtitle))
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
        BackTitle(str(R.string.hidden), onBack)
        Box(Modifier.fillMaxWidth().padding(top = 60.dp)) {
            GlassCard {
                Icon(Icons.Rounded.Lock, null, Modifier.size(44.dp).align(Alignment.CenterHorizontally), tint = p.accentFg)
                Text(
                    str(R.string.vault_locked),
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    error ?: str(R.string.vault_unlock_hint),
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                    fontSize = 14.sp, color = p.contentSecondary, textAlign = TextAlign.Center,
                )
                GlassButton(str(R.string.unlock), {
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
            if (days <= 0) str(R.string.today) else str(R.string.days_short, days),
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
