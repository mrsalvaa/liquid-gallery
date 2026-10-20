package dev.liquidgallery.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidgallery.data.ConsentRunner
import dev.liquidgallery.data.LocalConsent
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaOps
import dev.liquidgallery.ui.glass.ActionSheetHost
import dev.liquidgallery.ui.glass.GlassChip
import dev.liquidgallery.ui.glass.GlassRoot
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.LocalPrompt
import dev.liquidgallery.ui.glass.PromptHost
import dev.liquidgallery.ui.glass.PromptSpec
import dev.liquidgallery.ui.glass.SheetSpec
import dev.liquidgallery.ui.screens.GalleryActions
import dev.liquidgallery.ui.screens.LocalActions
import dev.liquidgallery.ui.screens.LocalSelectionBar
import dev.liquidgallery.ui.screens.LocalToast
import dev.liquidgallery.ui.screens.SelectionBarSpec
import dev.liquidgallery.ui.screens.ToastHost
import dev.liquidgallery.R
import dev.liquidgallery.util.str
import dev.liquidgallery.util.plural

/**
 * Lo común a las dos actividades: menús, diálogos, avisos, barra de selección y las acciones
 * sobre fotos. [floating] va encima del contenido y refracta lo que tiene detrás.
 */
@Composable
fun GalleryHost(
    consent: ConsentRunner,
    animated: Boolean,
    onPickAlbum: (List<Media>, Boolean) -> Unit,
    floating: @Composable BoxScope.(selectionActive: Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SheetSpec?>(null) }
    var prompt by remember { mutableStateOf<PromptSpec?>(null) }
    var toast by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var selectionBar by remember { mutableStateOf<SelectionBarSpec?>(null) }
    val showToast: (String) -> Unit = { toast = it to System.nanoTime() }
    val ops = remember(consent) { MediaOps(context.applicationContext, consent) }
    val actions = remember(ops) { GalleryActions(context, ops, scope, showToast, onPickAlbum) }

    CompositionLocalProvider(
        LocalConsent provides consent,
        LocalActions provides actions,
        LocalActionSheet provides { sheet = it },
        LocalPrompt provides { prompt = it },
        LocalToast provides showToast,
        LocalSelectionBar provides { selectionBar = it },
    ) {
        GlassRoot(
            animated = animated,
            floating = {
                floating(selectionBar != null)
                SelectionBars(selectionBar)
                ToastHost(toast) { toast = null }
                ActionSheetHost(sheet) { sheet = null }
                PromptHost(prompt) { prompt = null }
            },
            content = content,
        )
    }
}

/** Arriba: cerrar, contador y «Todo». Abajo: las acciones sobre lo seleccionado. */
@Composable
private fun BoxScope.SelectionBars(spec: SelectionBarSpec?) {
    var last by remember { mutableStateOf(spec) }
    if (spec != null) last = spec
    val p = LocalGlass.current
    AnimatedVisibility(
        spec != null,
        Modifier.align(Alignment.TopCenter),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val s = last ?: return@AnimatedVisibility
        val count = s.selection.ids.size
        val total = s.all().size
        GlassSurface(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(60.dp),
            shape = CircleShape,
            strong = true,
            frost = 6.dp,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clickable(remember { MutableInteractionSource() }, null) { s.selection.clear() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, str(R.string.close), tint = p.content) }
                Text(
                    when (count) {
                        0 -> str(R.string.select_items)
                        else -> plural(R.plurals.selected, count)
                    },
                    Modifier.weight(1f).padding(start = 4.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val all = total > 0 && count == total
                GlassChip(str(R.string.all), all, {
                    s.selection.ids = if (all) emptySet() else s.all().mapTo(HashSet()) { it.id }
                })
            }
        }
    }
    AnimatedVisibility(
        spec != null,
        Modifier.align(Alignment.BottomCenter),
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        val s = last ?: return@AnimatedVisibility
        val enabled = s.selection.ids.isNotEmpty()
        GlassSurface(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(70.dp),
            shape = CircleShape,
            strong = true,
            frost = 4.dp,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 6.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                s.actions().forEach { action ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) {
                                action.onClick(s.selected())
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val tint = if (action.danger) p.danger else p.content
                        Icon(action.icon, null, Modifier.size(24.dp), tint = tint)
                        Spacer(Modifier.height(2.dp))
                        Text(action.label, fontSize = 11.sp, color = tint, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Iconos de las barras del sistema según el tema (claros sobre fondo oscuro). */
@Composable
fun SystemBarsFor(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(dark) {
        val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
