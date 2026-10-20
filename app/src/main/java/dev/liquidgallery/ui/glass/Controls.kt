package dev.liquidgallery.ui.glass

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.liquidgallery.R
import dev.liquidgallery.util.str

data class NavItem(val label: String, val icon: ImageVector)

/** Barra inferior flotante con burbuja de selección que se desliza con rebote. */
@Composable
fun GlassNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Posición mientras se desliza entre pestañas (p. ej. 1,4), o null. Es una función para leerla
     * sin recomponer la pantalla en cada fotograma del arrastre.
     */
    dragPosition: () -> Float? = { null },
) {
    val p = LocalGlass.current
    // Una sola posición para la burbuja: sigue al dedo al deslizar y, al soltar o al tocar una
    // pestaña, continúa desde donde está hasta la pestaña elegida (sin volver a la anterior).
    val bubble = remember { Animatable(selected.toFloat()) }
    val target by rememberUpdatedState(selected)
    val bounce = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    // El resaltado sigue a la burbuja (la pestaña más cercana), no al final de la animación.
    val highlighted by remember { derivedStateOf { bubble.value.roundToInt() } }
    // Arrastre del dedo sobre la propia barra (posición en pestañas, p. ej. 1,4).
    var barDrag by remember { mutableStateOf<Float?>(null) }
    val select by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        snapshotFlow { barDrag ?: dragPosition() }.collectLatest { drag ->
            if (drag != null) bubble.snapTo(drag) else bubble.animateTo(target.toFloat(), bounce)
        }
    }
    LaunchedEffect(selected) {
        if (dragPosition() == null && barDrag == null) bubble.animateTo(selected.toFloat(), bounce)
    }
    GlassSurface(
        modifier.fillMaxWidth().height(70.dp),
        shape = CircleShape,
        strong = true,
        frost = 4.dp,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .pointerInput(items.size) {
                    val last = items.lastIndex.toFloat()
                    fun itemPx() = size.width.toFloat() / items.size
                    detectHorizontalDragGestures(
                        onDragStart = { o -> barDrag = (o.x / itemPx() - 0.5f).coerceIn(0f, last) },
                        onDragEnd = {
                            barDrag?.let { select(it.roundToInt()) }
                            barDrag = null
                        },
                        onDragCancel = { barDrag = null },
                    ) { change, dx ->
                        change.consume()
                        val before = (barDrag ?: 0f).roundToInt()
                        val now = ((barDrag ?: 0f) + dx / itemPx()).coerceIn(0f, last)
                        barDrag = now
                        if (now.roundToInt() != before) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
        ) {
            val itemWidth = maxWidth / items.size
            Box(
                Modifier
                    .offset { IntOffset((itemWidth * bubble.value).roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(p.content.copy(alpha = if (p.dark) 0.12f else 0.07f))
                    .border(1.dp, Color.White.copy(alpha = p.rim * 0.5f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    val active = i == highlighted
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon, null, Modifier.size(24.dp),
                            tint = if (active) p.accentFg else p.contentSecondary,
                        )
                        Text(
                            item.label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) p.content else p.contentSecondary,
                        )
                    }
                }
            }
        }
    }
}

data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

data class SheetSpec(val title: String, val actions: List<SheetAction>, val subtitle: String? = null)

/** Abre un menú de opciones (al mantener pulsado un elemento). Lo pinta [ActionSheetHost]. */
val LocalActionSheet = staticCompositionLocalOf<(SheetSpec) -> Unit> { {} }

@Composable
fun BoxScope.ActionSheetHost(spec: SheetSpec?, onDismiss: () -> Unit) {
    val p = LocalGlass.current
    var last by remember { mutableStateOf(spec) }
    if (spec != null && spec !== last) last = spec
    BackHandler(enabled = spec != null, onBack = onDismiss)

    AnimatedVisibility(spec != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        spec != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val s = last ?: return@AnimatedVisibility
        GlassSurface(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            strong = true,
            frost = 16.dp,
        ) {
            Column(Modifier.padding(vertical = 14.dp)) {
                Text(
                    s.title,
                    Modifier.padding(horizontal = 24.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.subtitle != null) {
                    Text(
                        s.subtitle,
                        Modifier.padding(horizontal = 24.dp),
                        fontSize = 13.sp,
                        color = p.contentSecondary,
                    )
                }
                Box(Modifier.height(8.dp))
                s.actions.forEach { action ->
                    val color = if (action.danger) p.danger else p.content
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(action.icon, null, Modifier.size(22.dp), tint = color)
                        Box(Modifier.width(16.dp))
                        Text(action.label, fontSize = 16.sp, color = color)
                    }
                }
            }
        }
    }
}

data class PromptSpec(
    val title: String,
    val confirmLabel: String,
    val placeholder: String,
    val subtitle: String? = null,
    val onConfirm: (String) -> Unit,
)

/** Pide un texto con una tarjeta de cristal centrada. La pinta [PromptHost]. */
val LocalPrompt = staticCompositionLocalOf<(PromptSpec) -> Unit> { {} }

@Composable
fun BoxScope.PromptHost(spec: PromptSpec?, onDismiss: () -> Unit) {
    val p = LocalGlass.current
    var last by remember { mutableStateOf(spec) }
    if (spec != null && spec !== last) last = spec
    BackHandler(enabled = spec != null, onBack = onDismiss)

    AnimatedVisibility(spec != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        spec != null,
        modifier = Modifier.align(Alignment.Center).imePadding().padding(20.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        val s = last ?: return@AnimatedVisibility
        // Cada apertura es un PromptSpec nuevo: el texto empieza vacío.
        var text by remember(s) { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val confirm = {
            onDismiss()
            s.onConfirm(text.trim())
        }
        LaunchedEffect(s) { focus.requestFocus() }
        GlassSurface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            strong = true,
            frost = 16.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (s.subtitle != null) {
                    Text(s.subtitle, fontSize = 14.sp, color = p.contentSecondary)
                }
                Box(Modifier.height(16.dp))
                GlassTextField(
                    text,
                    { text = it },
                    s.placeholder,
                    focusRequester = focus,
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(str(R.string.cancel), onDismiss, Modifier.weight(1f))
                    GlassButton(
                        s.confirmLabel,
                        confirm,
                        Modifier.weight(1f),
                        prominent = true,
                    )
                }
            }
        }
    }
}
