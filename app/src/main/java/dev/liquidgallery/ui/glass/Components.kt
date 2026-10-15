package dev.liquidgallery.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Superficie de "cristal líquido" al estilo de Apple: refracta lo que tiene detrás como una lente
 * (más en los bordes, con algo de aberración cromática), apenas lo desenfoca, realza los colores
 * y añade un brillo en el borde y una sombra suave. Al pulsarla se deforma como una gota.
 *
 * [frost] controla cuánto se esmerila: poco para controles ("clear"), más para tarjetas con texto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    tint: Color? = null,
    frost: Dp = 6.dp,
    refraction: Boolean = true,
    shadowRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalGlass.current
    val backdrop = LocalBackdrop.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish = spring<Float>(dampingRatio = 0.42f, stiffness = 520f)
    val sx by animateFloatAsState(if (pressed) 1.04f else 1f, squish, label = "sx")
    val sy by animateFloatAsState(if (pressed) 0.95f else 1f, squish, label = "sy")
    val surface = tint ?: if (strong) p.tintStrong else p.tint
    val clickable = onClick != null || onLongClick != null

    // El efecto de cristal no se entera de los cambios de color de su tinte: al cambiar de tema
    // (o de color de énfasis) se rehace la superficie para que no se quede con el tinte anterior.
    key(surface, p.dark) {
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(frost.toPx())
                    if (refraction) {
                        val side = size.minDimension
                        lens(
                            refractionHeight = minOf(side * 0.3f, 22.dp.toPx()),
                            refractionAmount = minOf(side * 0.55f, 40.dp.toPx()),
                            chromaticAberration = true,
                        )
                    }
                },
                highlight = { Highlight.Default.copy(alpha = if (p.dark) 0.75f else 1f) },
                shadow = {
                    Shadow(radius = shadowRadius, color = Color.Black.copy(alpha = if (p.dark) 0.4f else 0.1f))
                },
                layerBlock = {
                    scaleX = sx
                    scaleY = sy
                },
                onDrawSurface = { drawRect(surface) },
            )
            .then(
                if (clickable) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick ?: {},
                        onLongClick = onLongClick?.let {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                it()
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = sx
                scaleY = sy
            },
            content = content,
        )
    }
    }
}

/** Tarjeta de cristal con relleno y columna. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier.fillMaxWidth(), frost = 14.dp, onClick = onClick, onLongClick = onLongClick) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    prominent: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalGlass.current
    GlassSurface(
        modifier.size(size).graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        shape = CircleShape,
        strong = true,
        tint = if (prominent) p.accent.copy(alpha = 0.6f) else null,
        onClick = if (enabled) onClick else null,
    ) {
        Icon(
            icon,
            contentDescription,
            Modifier.align(Alignment.Center).size(size * 0.45f),
            tint = if (prominent) p.onAccent else p.content,
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    prominent: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalGlass.current
    val tint = when {
        danger -> p.danger.copy(alpha = 0.55f)
        prominent -> p.accent.copy(alpha = 0.6f)
        else -> null
    }
    val textColor = when {
        danger -> Color.White
        prominent -> p.onAccent
        else -> p.content
    }
    GlassSurface(
        modifier.heightIn(min = 54.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        shape = CircleShape,
        strong = true,
        tint = tint,
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp), tint = textColor)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = CircleShape,
) {
    val p = LocalGlass.current
    GlassSurface(
        modifier.height(40.dp),
        shape = shape,
        tint = if (selected) p.accent.copy(alpha = 0.5f) else null,
        frost = 4.dp,
        shadowRadius = 6.dp,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Text(
            text,
            Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
            color = if (selected) p.onAccent else p.content,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Interruptor con pulgar de "gota" que se estira al moverse. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val track by animateColorAsState(
        if (checked) p.accent else p.content.copy(alpha = if (p.dark) 0.16f else 0.12f),
        label = "track",
    )
    val offset by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "thumb",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stretch by animateDpAsState(if (pressed) 32.dp else 26.dp, label = "stretch")
    Box(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, Color.White.copy(alpha = p.rim * 0.4f), CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = if (checked) offset - (stretch - 26.dp) else offset)
                .size(width = stretch, height = 26.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Color.White, Color(0xFFE8ECF5))),
                ),
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle? = null,
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val p = LocalGlass.current
    val style = textStyle ?: TextStyle(fontSize = 17.sp)
    GlassSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), frost = 12.dp, refraction = false) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = style, color = p.contentSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = style.copy(color = p.content),
                cursorBrush = SolidColor(p.accentFg),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp),
        color = LocalGlass.current.contentSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

/** Fila de ajuste: título, subtítulo opcional y control a la derecha. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val p = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = p.contentSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val p = LocalGlass.current
    Row(
        modifier.fillMaxWidth().padding(top = TitleTopPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = p.contentSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

/** Margen sobre los títulos de cada pantalla, para que no queden pegados a la barra de estado. */
val TitleTopPadding = 24.dp
