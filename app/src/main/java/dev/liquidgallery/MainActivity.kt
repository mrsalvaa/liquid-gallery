package dev.liquidgallery

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.liquidgallery.data.ConsentRunner
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.data.VaultLock
import dev.liquidgallery.ui.GalleryHost
import dev.liquidgallery.ui.SystemBarsFor
import dev.liquidgallery.ui.glass.GlassNavBar
import dev.liquidgallery.ui.glass.GlassTheme
import dev.liquidgallery.ui.glass.LocalTabActive
import dev.liquidgallery.ui.glass.NavItem
import dev.liquidgallery.ui.glass.isDarkTheme
import dev.liquidgallery.ui.screens.AlbumPickerScreen
import dev.liquidgallery.ui.screens.AlbumsScreen
import dev.liquidgallery.ui.screens.EditorScreen
import dev.liquidgallery.ui.screens.GroupScreen
import dev.liquidgallery.ui.screens.MenuScreen
import dev.liquidgallery.ui.screens.PermissionScreen
import dev.liquidgallery.ui.screens.PhotosScreen
import dev.liquidgallery.ui.screens.ReorderAlbumsScreen
import dev.liquidgallery.ui.screens.SearchScreen
import dev.liquidgallery.ui.screens.SettingsScreen
import dev.liquidgallery.ui.screens.Source
import dev.liquidgallery.ui.screens.SourceGridScreen
import dev.liquidgallery.ui.screens.ViewerScreen
import dev.liquidgallery.ui.screens.rememberMediaAccess
import dev.liquidgallery.util.preferHighestRefreshRate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pantallas que se abren encima de las pestañas, apiladas. */
sealed interface Overlay {
    data class Grid(val source: Source) : Overlay
    data class Group(val id: Long) : Overlay
    data class Viewer(val source: Source, val startId: Long) : Overlay
    data class Editor(val mediaId: Long) : Overlay
    data class PickAlbum(val ids: List<Long>, val copy: Boolean) : Overlay
    data object Search : Overlay
    data object Settings : Overlay
    data object Reorder : Overlay
}

private const val TAB_PHOTOS = 0
private const val TAB_ALBUMS = 1
private const val TAB_MORE = 2

class MainActivity : FragmentActivity() {
    private val consent = ConsentRunner(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            SystemBarsFor(isDarkTheme(settings.themeMode))
            GlassTheme(settings) {
                App(consent, settings.animatedBackground)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lo oculto se vuelve a bloquear al salir de la app.
        if (!isChangingConfigurations) VaultLock.lock()
    }
}

@Composable
private fun App(consent: ConsentRunner, animated: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_PHOTOS) }
    // Posición de la barra mientras se desliza entre pestañas (p. ej. 1,4), o null.
    var tabDrag by remember { mutableStateOf<Float?>(null) }
    val stack = remember { mutableStateListOf<Overlay>() }
    val holder = rememberSaveableStateHolder()
    // Último elemento visto en el visor: la cuadrícula vuelve a él.
    var focusId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val (access, recheck) = rememberMediaAccess()

    fun push(o: Overlay) {
        stack += o
    }
    fun pop() {
        if (stack.isEmpty()) return
        val key = "${stack.lastIndex}:${stack.last()}"
        stack.removeAt(stack.lastIndex)
        holder.removeState(key)
    }
    BackHandler(enabled = stack.isNotEmpty()) { pop() }
    BackHandler(enabled = stack.isEmpty() && tab != TAB_PHOTOS) { tab = TAB_PHOTOS }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    val navHeight = 70.dp + 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabPadding = PaddingValues(
        top = insets.calculateTopPadding(),
        bottom = navHeight + 16.dp,
        start = insets.calculateStartPadding(dir),
        end = insets.calculateEndPadding(dir),
    )
    val overlayPadding = PaddingValues(
        top = insets.calculateTopPadding(),
        bottom = insets.calculateBottomPadding() + 16.dp,
        start = insets.calculateStartPadding(dir),
        end = insets.calculateEndPadding(dir),
    )
    val openViewer: (Source, Media) -> Unit = { source, m -> push(Overlay.Viewer(source, m.id)) }
    val openSource: (Source) -> Unit = { push(Overlay.Grid(it)) }
    val openVault = { push(Overlay.Grid(Source.Vault)) }

    GalleryHost(
        consent = consent,
        animated = animated,
        onPickAlbum = { items, copy -> push(Overlay.PickAlbum(items.map { it.id }, copy)) },
        floating = { selecting ->
            AnimatedVisibility(
                stack.isEmpty() && !selecting && access > 0,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                GlassNavBar(
                    items = listOf(
                        NavItem("Imágenes", Icons.Rounded.Photo),
                        NavItem("Álbumes", Icons.Rounded.PhotoAlbum),
                        NavItem("Más", Icons.Rounded.Menu),
                    ),
                    selected = tab,
                    dragPosition = { tabDrag },
                    onSelect = { tab = it },
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) {
        if (access == 0) {
            PermissionScreen(overlayPadding, recheck)
            return@GalleryHost
        }
        val top = stack.lastOrNull()
        // Las pestañas siguen creadas debajo de lo que se abra encima: al volver están igual
        // (desplazamiento, selección) y no hay que reconstruir nada.
        val tabsAlpha by animateFloatAsState(if (top == null) 1f else 0f, tween(if (top == null) 220 else 120), label = "tabs")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = tabsAlpha }
                .drawWithContent { if (tabsAlpha > 0.01f) drawContent() }
                .then(if (top != null) Modifier.clearAndSetSemantics { }.pointerInput(Unit) { swallowTouches() } else Modifier),
        ) {
            SwipeTabs(tab, onScreen = top == null, onTab = { tab = it }, onDrag = { tabDrag = it }) { t ->
                when (t) {
                    TAB_PHOTOS -> PhotosScreen(
                        tabPadding, focusId, openViewer,
                        onSearch = { push(Overlay.Search) },
                        onSettings = { push(Overlay.Settings) },
                    )
                    TAB_ALBUMS -> AlbumsScreen(
                        tabPadding,
                        onOpenSource = openSource,
                        onOpenGroup = { push(Overlay.Group(it)) },
                        onReorder = { push(Overlay.Reorder) },
                        onVault = openVault,
                    )
                    else -> MenuScreen(
                        tabPadding, access, recheck,
                        onOpenSource = openSource,
                        onVault = openVault,
                        onSettings = { push(Overlay.Settings) },
                    )
                }
            }
        }

        val topKey = if (top == null) "none" else "${stack.lastIndex}:$top"
        AnimatedContent(
            targetState = top to topKey,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
            },
            contentKey = { it.second },
            label = "overlay",
        ) { (current, key) ->
            if (current == null) {
                Box(Modifier.fillMaxSize())
                return@AnimatedContent
            }
            holder.SaveableStateProvider(key) {
                when (current) {
                    is Overlay.Grid -> SourceGridScreen(current.source, overlayPadding, focusId, ::pop, openViewer)
                    is Overlay.Group -> GroupScreen(current.id, overlayPadding, ::pop, openSource)
                    is Overlay.Viewer -> ViewerScreen(
                        current.source,
                        current.startId,
                        onClose = { last ->
                            focusId = last
                            pop()
                        },
                        onEdit = { push(Overlay.Editor(it.id)) },
                    )
                    is Overlay.Editor -> {
                        val media by MediaRepo.media.collectAsStateWithLifecycle()
                        val m = remember(media) { media.firstOrNull { it.id == current.mediaId } }
                        if (m == null) LaunchedEffect(Unit) { pop() } else EditorScreen(m, ::pop, ::pop)
                    }
                    is Overlay.PickAlbum -> {
                        val media by MediaRepo.media.collectAsStateWithLifecycle()
                        val items = remember { media.filter { it.id in current.ids } }
                        AlbumPickerScreen(items, current.copy, overlayPadding, ::pop)
                    }
                    Overlay.Search -> SearchScreen(overlayPadding, searchQuery, { searchQuery = it }, ::pop, openViewer)
                    Overlay.Settings -> SettingsScreen(overlayPadding, ::pop)
                    Overlay.Reorder -> ReorderAlbumsScreen(overlayPadding, ::pop)
                }
            }
        }
    }
}

/** Se traga todos los toques (para las pestañas que quedan debajo de otra pantalla). */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swallowTouches() {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Pestañas que se cambian deslizando, con el HorizontalPager de Compose: resuelve bien los bordes,
 * las inercias y los deslizamientos rápidos encadenados. Las tres siguen creadas aunque no se vean
 * (no se reconstruyen ni pierden el desplazamiento) y la burbuja de la barra sigue al dedo.
 */
@Composable
private fun SwipeTabs(
    tab: Int,
    onScreen: Boolean,
    onTab: (Int) -> Unit,
    onDrag: (Float?) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = tab) { TAB_MORE + 1 }
    val select by rememberUpdatedState(onTab)
    val drag by rememberUpdatedState(onDrag)
    // Deslizar: cuando la página se asienta pasa a ser la pestaña elegida.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { select(it) }
    }
    // Mientras se mueve, la barra muestra la posición exacta (p. ej. 1,4).
    LaunchedEffect(pager) {
        snapshotFlow { if (pager.isScrollInProgress) pager.currentPage + pager.currentPageOffsetFraction else null }
            .collect { drag(it) }
    }
    // Tocar la barra o volver atrás: se va a esa página.
    LaunchedEffect(tab) {
        if (pager.targetPage != tab) pager.animateScrollToPage(tab)
    }
    HorizontalPager(
        state = pager,
        beyondViewportPageCount = TAB_MORE,
        userScrollEnabled = onScreen,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        CompositionLocalProvider(LocalTabActive provides (page == pager.currentPage && onScreen)) { content(page) }
    }
}
