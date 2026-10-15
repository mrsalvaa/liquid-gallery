package dev.liquidgallery.ui.glass

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.liquidgallery.data.AppSettings
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Immutable
data class GlassPalette(
    val dark: Boolean,
    val base: Color,
    val content: Color,
    val contentSecondary: Color,
    val tint: Color,
    val tintStrong: Color,
    val highlight: Float,
    val rim: Float,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val success: Color,
    val blobs: List<Color>,
    /** Modo color de énfasis: el fondo es un brillo quieto del acento en vez de manchas. */
    val accentGlow: Boolean = false,
    /** Acento para iconos, textos y líneas: ajustado para que se lea sobre el fondo. */
    val accentFg: Color = Color.Unspecified,
)

val DarkGlass = GlassPalette(
    dark = true,
    base = Color(0xFF030407),
    content = Color.White,
    contentSecondary = Color.White.copy(alpha = 0.6f),
    tint = Color(0xFF0A0C12).copy(alpha = 0.28f),
    tintStrong = Color(0xFF0A0C12).copy(alpha = 0.42f),
    highlight = 0.10f,
    rim = 0.45f,
    accent = Color(0xFF7CC8FF),
    onAccent = Color(0xFF041322),
    danger = Color(0xFFFF7A8A),
    success = Color(0xFF6EE7B7),
    blobs = listOf(Color(0xFF38BDF8), Color(0xFFF472B6), Color(0xFF0E7490), Color(0xFF9D174D)),
)

val LightGlass = GlassPalette(
    dark = false,
    base = Color(0xFFF1F3F8),
    content = Color(0xFF0E1222),
    contentSecondary = Color(0xFF0E1222).copy(alpha = 0.58f),
    tint = Color.White.copy(alpha = 0.32f),
    tintStrong = Color.White.copy(alpha = 0.5f),
    highlight = 0.4f,
    rim = 0.9f,
    accent = Color(0xFF1C7ED6),
    onAccent = Color.White,
    danger = Color(0xFFE5484D),
    success = Color(0xFF12A594),
    blobs = listOf(Color(0xFFA5DCFB), Color(0xFFFBC4DE), Color(0xFF7CC8EE), Color(0xFFF5A3C7)),
)

/** Set de colores del fondo; cada uno trae su color de acento. */
data class ColorSet(
    val name: String,
    val darkBlobs: List<Color>,
    val lightBlobs: List<Color>,
    val darkAccent: Color,
    val lightAccent: Color,
)

val COLOR_SETS = listOf(
    ColorSet(
        "Celeste y rosa",
        darkBlobs = listOf(Color(0xFF38BDF8), Color(0xFFF472B6), Color(0xFF0E7490), Color(0xFF9D174D)),
        lightBlobs = listOf(Color(0xFFA5DCFB), Color(0xFFFBC4DE), Color(0xFF7CC8EE), Color(0xFFF5A3C7)),
        darkAccent = Color(0xFF7CC8FF),
        lightAccent = Color(0xFF1C7ED6),
    ),
    ColorSet(
        "Aurora",
        darkBlobs = listOf(Color(0xFF34D399), Color(0xFF8B5CF6), Color(0xFF0F766E), Color(0xFF5B21B6)),
        lightBlobs = listOf(Color(0xFFA7F3D0), Color(0xFFDDD6FE), Color(0xFF99F6E4), Color(0xFFC4B5FD)),
        darkAccent = Color(0xFF6EE7B7),
        lightAccent = Color(0xFF0D9488),
    ),
    ColorSet(
        "Atardecer",
        darkBlobs = listOf(Color(0xFFFB923C), Color(0xFFE879F9), Color(0xFF9A3412), Color(0xFF86198F)),
        lightBlobs = listOf(Color(0xFFFED7AA), Color(0xFFF5D0FE), Color(0xFFFDBA74), Color(0xFFF0ABFC)),
        darkAccent = Color(0xFFFDBA74),
        lightAccent = Color(0xFFC2410C),
    ),
    ColorSet(
        "Océano",
        darkBlobs = listOf(Color(0xFF3B82F6), Color(0xFF2DD4BF), Color(0xFF1E3A8A), Color(0xFF115E59)),
        lightBlobs = listOf(Color(0xFFBFDBFE), Color(0xFF99F6E4), Color(0xFF93C5FD), Color(0xFF5EEAD4)),
        darkAccent = Color(0xFF93C5FD),
        lightAccent = Color(0xFF1D4ED8),
    ),
)

/** Colores de énfasis: cada uno con tres tonos (claro, medio, oscuro). */
data class AccentHue(val name: String, val tones: List<Color>)

val ACCENT_HUES = listOf(
    AccentHue("Rojo", listOf(Color(0xFFFCA5A5), Color(0xFFEF4444), Color(0xFFB91C1C))),
    AccentHue("Naranja", listOf(Color(0xFFFDBA74), Color(0xFFF97316), Color(0xFFC2410C))),
    AccentHue("Amarillo", listOf(Color(0xFFFDE68A), Color(0xFFFACC15), Color(0xFFCA8A04))),
    AccentHue("Verde", listOf(Color(0xFF86EFAC), Color(0xFF22C55E), Color(0xFF15803D))),
    AccentHue("Menta", listOf(Color(0xFF5EEAD4), Color(0xFF14B8A6), Color(0xFF0F766E))),
    AccentHue("Celeste", listOf(Color(0xFF7DD3FC), Color(0xFF0EA5E9), Color(0xFF0369A1))),
    AccentHue("Azul", listOf(Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF1D4ED8))),
    AccentHue("Violeta", listOf(Color(0xFFC4B5FD), Color(0xFF8B5CF6), Color(0xFF6D28D9))),
    AccentHue("Rosa", listOf(Color(0xFFF9A8D4), Color(0xFFEC4899), Color(0xFFBE185D))),
    AccentHue("Gris", listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFF4B5563))),
)

/**
 * Versión apagada de un color de énfasis: se mezcla con un gris de su misma luminosidad para que
 * no sobresalga tanto, sin cambiar el tono.
 */
fun muted(color: Color, amount: Float = 0.3f): Color {
    val l = color.luminance()
    val gray = Color(l, l, l, color.alpha)
    return lerp(color, gray, amount)
}

/** Acento aclarado (u oscurecido) lo justo para leerse como icono o texto sobre el fondo. */
private fun foregroundAccent(accent: Color, dark: Boolean): Color {
    val l = accent.luminance()
    return when {
        dark && l < 0.3f -> lerp(accent, Color.White, ((0.3f - l) / 0.3f * 0.6f).coerceIn(0f, 0.6f))
        !dark && l > 0.5f -> lerp(accent, Color.Black, ((l - 0.5f) / 0.5f * 0.6f).coerceIn(0f, 0.6f))
        else -> accent
    }
}

/** Texto legible sobre un fondo de ese color. */
fun readableOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF0B0D12) else Color.White

/** Texto legible sobre el cristal teñido de acento (acento al ~55 % sobre la base). */
private fun onAccentGlass(accent: Color, base: Color): Color = readableOn(accent.copy(alpha = 0.55f).compositeOver(base))

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** false en las pestañas ocultas: sirve para pausar lo que se refresca en cada fotograma. */
val LocalTabActive = compositionLocalOf { true }
/** Lo que refractan las superficies de cristal: el fondo, o el fondo más el contenido. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop> { error("Falta GlassRoot") }

/** Si el tema es oscuro según los ajustes (0 sistema, 1 claro, 2 oscuro). */
@Composable
fun isDarkTheme(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun GlassTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings.themeMode)
    val base = if (dark) DarkGlass else LightGlass
    val p = if (settings.backgroundMode == 1) {
        val accent = muted(Color(settings.accentColor))
        base.copy(accent = accent, onAccent = onAccentGlass(accent, base.base), blobs = listOf(accent), accentGlow = true)
    } else {
        val set = COLOR_SETS.getOrElse(settings.colorSet) { COLOR_SETS[0] }
        val accent = if (dark) set.darkAccent else set.lightAccent
        base.copy(
            blobs = if (dark) set.darkBlobs else set.lightBlobs,
            accent = accent,
            onAccent = onAccentGlass(accent, base.base),
        )
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.base, onBackground = p.content,
            surface = Color(0xFF151A2C), onSurface = p.content,
            error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.base, onBackground = p.content,
            surface = Color(0xFFF8F9FD), onSurface = p.content,
            error = p.danger,
        )
    }
    val palette = p.copy(accentFg = foregroundAccent(p.accent, dark))
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalGlass provides palette,
            LocalContentColor provides p.content,
            content = content,
        )
    }
}

/**
 * Raíz de cada pantalla. Hay dos capas que el cristal puede refractar:
 * - el fondo de color, para las superficies que están dentro del contenido (tarjetas, botones);
 * - el fondo más el contenido, para lo que flota encima ([floating]: barra inferior, menús).
 */
@Composable
fun GlassRoot(
    animated: Boolean = true,
    floating: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val background = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(page)) {
            LiquidBackground(Modifier.fillMaxSize().layerBackdrop(background), animated)
            CompositionLocalProvider(LocalBackdrop provides background) {
                Box(Modifier.fillMaxSize(), content = content)
            }
        }
        // Degradado bajo la barra de estado para que el contenido no se mezcle con la hora.
        val p = LocalGlass.current
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(p.base, p.base.copy(alpha = 0.6f)))),
        )
        CompositionLocalProvider(LocalBackdrop provides page) {
            Box(Modifier.fillMaxSize(), content = floating)
        }
    }
}

@Composable
fun LiquidBackground(modifier: Modifier = Modifier, animated: Boolean = true) {
    val p = LocalGlass.current
    // Las manchas se mueven tan despacio (una vuelta cada 90 s) que basta con moverlas ~15 veces
    // por segundo. Animarlas a la frecuencia de la pantalla obligaría a redibujar todo el cristal
    // en cada fotograma (120 veces por segundo) aunque no se toque nada.
    var phase by remember { mutableFloatStateOf(0.8f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animated, lifecycle) {
        if (!animated) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                phase = (SystemClock.uptimeMillis() % 90_000L) / 90_000f * (2 * PI).toFloat()
                delay(66)
            }
        }
    }
    // En movimiento las manchas pasan por el centro y se notan más: van más tenues.
    val alpha = when {
        p.dark && animated -> 0.2f
        p.dark -> 0.34f
        animated -> 0.42f
        else -> 0.6f
    }
    if (p.accentGlow) {
        AccentGlowBackground(modifier)
        return
    }
    Canvas(modifier) {
        drawRect(p.base)
        val w = size.width
        val h = size.height
        val r = max(w, h) * 0.48f
        val t = if (animated) phase else 0.8f
        p.blobs.forEachIndexed { i, color ->
            val a = t * (1 + i * 0.25f) + i * 1.7f
            val center = Offset(
                x = w * (0.5f + 0.38f * cos(a)),
                y = h * (0.5f + 0.40f * sin(a * 0.8f + i)),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }
    }
}

/** Fondo del modo color de énfasis: base oscura (o clara) con dos resplandores quietos del acento. */
@Composable
private fun AccentGlowBackground(modifier: Modifier) {
    val p = LocalGlass.current
    val accent = p.blobs.first()
    val strong = if (p.dark) 0.18f else 0.3f
    Canvas(modifier) {
        drawRect(p.base)
        val r = max(size.width, size.height)
        listOf(
            Offset(size.width * 0.1f, size.height * 0.05f) to strong,
            Offset(size.width * 0.95f, size.height * 0.85f) to strong * 0.55f,
        ).forEach { (center, alpha) ->
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color.Transparent), center, r * 0.75f),
                radius = r * 0.75f,
                center = center,
            )
        }
    }
}
