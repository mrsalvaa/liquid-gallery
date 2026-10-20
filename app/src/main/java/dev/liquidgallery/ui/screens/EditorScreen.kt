package dev.liquidgallery.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquidgallery.data.Media
import dev.liquidgallery.ui.glass.GlassButton
import dev.liquidgallery.ui.glass.GlassChip
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.GlassNavBar
import dev.liquidgallery.ui.glass.LocalActionSheet
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.LocalBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquidgallery.ui.glass.NavItem
import dev.liquidgallery.ui.glass.SheetAction
import dev.liquidgallery.ui.glass.SheetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.liquidgallery.R
import dev.liquidgallery.util.str

/** Filtros: nombre y matriz de color 4x5 (como android.graphics.ColorMatrix). */
private data class Filter(val name: Int, val matrix: FloatArray?)

private val FILTERS = listOf(
    Filter(R.string.filter_original, null),
    Filter(R.string.filter_vivid, saturationMatrix(1.35f).also { scaleRgb(it, 1.04f, 1.04f, 1.04f) }),
    Filter(R.string.filter_warm, floatArrayOf(1.08f, 0f, 0f, 0f, 8f, 0f, 1.02f, 0f, 0f, 2f, 0f, 0f, 0.88f, 0f, -6f, 0f, 0f, 0f, 1f, 0f)),
    Filter(R.string.filter_cool, floatArrayOf(0.9f, 0f, 0f, 0f, -4f, 0f, 1.0f, 0f, 0f, 2f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)),
    Filter(R.string.filter_bw, saturationMatrix(0f)),
    Filter(R.string.filter_noir, saturationMatrix(0f).also { contrast(it, 1.35f) }),
    Filter(R.string.filter_sepia, floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    Filter(R.string.filter_faded, saturationMatrix(0.7f).also { contrast(it, 0.8f); offset(it, 18f) }),
    Filter(R.string.filter_dramatic, saturationMatrix(1.15f).also { contrast(it, 1.3f); offset(it, -10f) }),
)

private fun saturationMatrix(s: Float) = android.graphics.ColorMatrix().apply { setSaturation(s) }.array.copyOf()
private fun scaleRgb(m: FloatArray, r: Float, g: Float, b: Float) {
    for (i in 0..4) { m[i] *= r; m[5 + i] *= g; m[10 + i] *= b }
}
private fun contrast(m: FloatArray, c: Float) {
    val t = 128f * (1 - c)
    for (row in 0..2) {
        for (i in 0..3) m[row * 5 + i] *= c
        m[row * 5 + 4] = m[row * 5 + 4] * c + t
    }
}
private fun offset(m: FloatArray, o: Float) {
    for (row in 0..2) m[row * 5 + 4] += o
}

/** Ajustes de -1 a 1. */
private data class Adjust(val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val warmth: Float = 0f) {
    val isIdentity get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

private fun colorMatrix(filter: Int, a: Adjust): android.graphics.ColorMatrix? {
    val f = FILTERS[filter].matrix
    if (f == null && a.isIdentity) return null
    val m = android.graphics.ColorMatrix()
    if (f != null) m.set(f)
    if (a.saturation != 0f) m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1f + a.saturation) })
    if (a.contrast != 0f) {
        val c = 1f + a.contrast * 0.6f
        val t = 128f * (1 - c)
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }
    if (a.brightness != 0f) {
        val b = a.brightness * 70f
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, b, 0f, 1f, 0f, 0f, b, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f)))
    }
    if (a.warmth != 0f) {
        val w = a.warmth * 0.14f
        m.postConcat(android.graphics.ColorMatrix().apply { setScale(1f + w, 1f, 1f - w, 1f) })
    }
    return m
}

/** Proporciones de recorte; 0 = libre, -1 = la de la foto. */
private val RATIOS get() = listOf(str(R.string.ratio_free) to 0f, str(R.string.filter_original) to -1f, "1:1" to 1f, "4:3" to 4f / 3, "3:4" to 3f / 4, "16:9" to 16f / 9, "9:16" to 9f / 16)

/** Gira y voltea un bitmap (rotación en múltiplos de 90, en sentido antihorario). */
private fun orient(src: Bitmap, rotation: Int, flip: Boolean): Bitmap {
    if (rotation == 0 && !flip) return src
    val m = Matrix()
    if (flip) m.postScale(-1f, 1f)
    m.postRotate(-rotation.toFloat())
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun decode(context: android.content.Context, m: Media, maxSide: Int?): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, m.uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        if (maxSide != null) {
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) {
                val s = maxSide.toFloat() / side
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }
    }
}

/** Aplica todo a la foto a tamaño completo. */
private fun render(full: Bitmap, rotation: Int, flip: Boolean, crop: RectF, matrix: android.graphics.ColorMatrix?): Bitmap {
    val oriented = orient(full, rotation, flip)
    val x = (crop.left * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
    val y = (crop.top * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
    val w = (crop.width() * oriented.width).roundToInt().coerceIn(1, oriented.width - x)
    val h = (crop.height() * oriented.height).roundToInt().coerceIn(1, oriented.height - y)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix) }
    Canvas(out).drawBitmap(oriented, -x.toFloat(), -y.toFloat(), paint)
    return out
}

@Composable
fun EditorScreen(media: Media, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    val actions = LocalActions.current
    val sheet = LocalActionSheet.current
    val scope = rememberCoroutineScope()

    val preview by produceState<Bitmap?>(null, media.uri) {
        value = withContext(Dispatchers.IO) { runCatching { decode(context, media, 2048) }.getOrNull() }
    }
    var tab by remember { mutableIntStateOf(0) }
    var rotation by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var ratio by remember { mutableFloatStateOf(0f) }
    var adjust by remember { mutableStateOf(Adjust()) }
    var adjustKind by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    val changed = rotation != 0 || flip || crop != Rect(0f, 0f, 1f, 1f) || !adjust.isIdentity || filter != 0
    fun reset() {
        rotation = 0; flip = false; crop = Rect(0f, 0f, 1f, 1f); ratio = 0f; adjust = Adjust(); filter = 0
    }
    fun leave() {
        if (!changed) onClose() else sheet(
            SheetSpec(str(R.string.discard_changes_q), listOf(SheetAction(str(R.string.discard), Icons.Rounded.Close, danger = true, onClick = onClose))),
        )
    }
    BackHandler(enabled = !saving) { leave() }

    val oriented = remember(preview, rotation, flip) { preview?.let { orient(it, rotation, flip).asImageBitmap() } }
    val matrix = remember(filter, adjust) { colorMatrix(filter, adjust) }
    val composeFilter = remember(matrix) { matrix?.let { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it.array)) } }

    fun applyRatio(r: Float) {
        ratio = r
        val img = oriented ?: return
        val imgRatio = img.width.toFloat() / img.height
        val target = if (r < 0f) imgRatio else r
        if (target == 0f) return
        // El recorte más grande de esa proporción, centrado.
        crop = if (target > imgRatio) {
            val h = imgRatio / target
            Rect(0f, (1 - h) / 2, 1f, (1 + h) / 2)
        } else {
            val w = target / imgRatio
            Rect((1 - w) / 2, 0f, (1 + w) / 2, 1f)
        }
    }

    fun save(overwrite: Boolean) {
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val full = try {
                        decode(context, media, null)
                    } catch (e: OutOfMemoryError) {
                        decode(context, media, 4096)
                    }
                    render(full, rotation, flip, RectF(crop.left, crop.top, crop.right, crop.bottom), matrix)
                }.getOrNull()
            }
            val ok = result != null && if (overwrite) {
                actions.ops.overwrite(media, result)
            } else {
                actions.ops.saveCopy(media, result) != null
            }
            saving = false
            if (ok) {
                actions.toast(str(if (overwrite) R.string.toast_saved else R.string.toast_saved_copy))
                onSaved()
            } else {
                actions.toast(str(R.string.toast_save_failed))
            }
        }
    }

    // El cristal de los controles refracta el fondo negro del editor, no el fondo de la app.
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.Black))
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.Rounded.Close, str(R.string.cancel), { leave() })
                Text(str(R.string.edit), Modifier.weight(1f).padding(horizontal = 14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (changed) GlassIconButton(Icons.Rounded.Restore, str(R.string.reset), { reset() })
                Spacer(Modifier.width(10.dp))
                GlassButton(
                    str(R.string.save),
                    {
                        sheet(
                            SheetSpec(
                                str(R.string.save),
                                listOf(
                                    SheetAction(str(R.string.save_copy), Icons.Rounded.SaveAs) { save(false) },
                                    SheetAction(str(R.string.overwrite_original), Icons.Rounded.Save) { save(true) },
                                ),
                            ),
                        )
                    },
                    prominent = true,
                    enabled = changed && !saving,
                )
            }

            // Imagen.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val img = oriented
                if (img == null) {
                    CircularProgressIndicator(color = p.accent)
                } else if (tab == 0) {
                    CropView(img, crop, ratio, composeFilter) { crop = it }
                } else {
                    val src = IntOffset((crop.left * img.width).roundToInt(), (crop.top * img.height).roundToInt())
                    val size = IntSize(
                        (crop.width * img.width).roundToInt().coerceIn(1, img.width - src.x),
                        (crop.height * img.height).roundToInt().coerceIn(1, img.height - src.y),
                    )
                    val painter = remember(img, src, size) { BitmapPainter(img, src, size) }
                    Image(
                        painter, null,
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Mantener pulsado para comparar con la original.
                                detectTapGestures(onPress = {
                                    showOriginal = true
                                    tryAwaitRelease()
                                    showOriginal = false
                                })
                            },
                        contentScale = ContentScale.Fit,
                        colorFilter = if (showOriginal) null else composeFilter,
                    )
                }
            }

            // Herramientas de la pestaña.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                when (tab) {
                    0 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassIconButton(Icons.Rounded.RotateLeft, str(R.string.rotate), {
                                rotation = (rotation + 90) % 360
                                crop = Rect(0f, 0f, 1f, 1f)
                                if (ratio != 0f) ratio = 0f
                            }, size = 40.dp)
                            GlassIconButton(Icons.Rounded.Flip, str(R.string.flip), {
                                flip = !flip
                                crop = Rect(1f - crop.right, crop.top, 1f - crop.left, crop.bottom)
                            }, size = 40.dp)
                            RATIOS.forEach { (label, r) -> GlassChip(label, ratio == r, { applyRatio(r) }) }
                        }
                    }
                    1 -> {
                        val labels = listOf(R.string.brightness, R.string.contrast, R.string.saturation, R.string.warmth).map { str(it) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            labels.forEachIndexed { i, l -> GlassChip(l, adjustKind == i, { adjustKind = i }) }
                        }
                        val value = when (adjustKind) {
                            0 -> adjust.brightness
                            1 -> adjust.contrast
                            2 -> adjust.saturation
                            else -> adjust.warmth
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = value,
                                onValueChange = { v ->
                                    val snapped = if (abs(v) < 0.04f) 0f else v
                                    adjust = when (adjustKind) {
                                        0 -> adjust.copy(brightness = snapped)
                                        1 -> adjust.copy(contrast = snapped)
                                        2 -> adjust.copy(saturation = snapped)
                                        else -> adjust.copy(warmth = snapped)
                                    }
                                },
                                valueRange = -1f..1f,
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = p.accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                (value * 100).roundToInt().toString(),
                                Modifier.width(44.dp).padding(start = 8.dp),
                                color = Color.White, fontSize = 14.sp,
                            )
                        }
                    }
                    else -> FilterStrip(preview, filter) { filter = it }
                }
            }

            GlassNavBar(
                items = listOf(
                    NavItem(str(R.string.crop), Icons.Rounded.Crop),
                    NavItem(str(R.string.adjust), Icons.Rounded.Tune),
                    NavItem(str(R.string.filters), Icons.Rounded.AutoAwesome),
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        }

        if (saving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = p.accent)
            }
        }
    }
}

@Composable
private fun FilterStrip(preview: Bitmap?, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalGlass.current
    val small = remember(preview) {
        preview?.let {
            val s = 160f / max(it.width, it.height)
            Bitmap.createScaledBitmap(it, (it.width * s).roundToInt().coerceAtLeast(1), (it.height * s).roundToInt().coerceAtLeast(1), true).asImageBitmap()
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FILTERS.forEachIndexed { i, f ->
            Column(
                Modifier.clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, if (i == selected) p.accent else Color.Transparent, RoundedCornerShape(16.dp)),
                ) {
                    if (small != null) {
                        Image(
                            small, str(f.name), Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            colorFilter = f.matrix?.let { ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(it)) },
                        )
                    }
                }
                Text(
                    str(f.name), Modifier.padding(top = 4.dp),
                    color = if (i == selected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Imagen con el marco de recorte: se arrastran las esquinas, los bordes o el marco entero. */
@Composable
private fun CropView(img: ImageBitmap, crop: Rect, ratio: Float, filter: ColorFilter?, onCrop: (Rect) -> Unit) {
    val handle = with(LocalDensity.current) { 28.dp.toPx() }
    val minSize = 0.08f
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / img.width, boxH / img.height)
        val w = img.width * scale
        val h = img.height * scale
        val left = (boxW - w) / 2
        val top = (boxH - h) / 2
        val imgRatio = img.width.toFloat() / img.height
        val fixed = when {
            ratio < 0f -> imgRatio
            else -> ratio
        }
        var current by remember { mutableStateOf(crop) }
        current = crop
        ComposeCanvas(
            Modifier
                .fillMaxSize()
                .pointerInput(img, ratio) {
                    var mode = 0 // 1..4 esquinas (TL, TR, BL, BR), 5..8 bordes (L, T, R, B), 9 mover
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = current
                            val l = left + r.left * w
                            val t = top + r.top * h
                            val rr = left + r.right * w
                            val b = top + r.bottom * h
                            fun near(x: Float, y: Float) = abs(pos.x - x) < handle && abs(pos.y - y) < handle
                            mode = when {
                                near(l, t) -> 1
                                near(rr, t) -> 2
                                near(l, b) -> 3
                                near(rr, b) -> 4
                                fixed == 0f && abs(pos.x - l) < handle && pos.y in t..b -> 5
                                fixed == 0f && abs(pos.y - t) < handle && pos.x in l..rr -> 6
                                fixed == 0f && abs(pos.x - rr) < handle && pos.y in t..b -> 7
                                fixed == 0f && abs(pos.y - b) < handle && pos.x in l..rr -> 8
                                pos.x in l..rr && pos.y in t..b -> 9
                                else -> 0
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        val dx = drag.x / w
                        val dy = drag.y / h
                        var r = current
                        r = when (mode) {
                            9 -> {
                                val nx = (r.left + dx).coerceIn(0f, 1f - r.width)
                                val ny = (r.top + dy).coerceIn(0f, 1f - r.height)
                                Rect(nx, ny, nx + r.width, ny + r.height)
                            }
                            1 -> Rect((r.left + dx).coerceIn(0f, r.right - minSize), (r.top + dy).coerceIn(0f, r.bottom - minSize), r.right, r.bottom)
                            2 -> Rect(r.left, (r.top + dy).coerceIn(0f, r.bottom - minSize), (r.right + dx).coerceIn(r.left + minSize, 1f), r.bottom)
                            3 -> Rect((r.left + dx).coerceIn(0f, r.right - minSize), r.top, r.right, (r.bottom + dy).coerceIn(r.top + minSize, 1f))
                            4 -> Rect(r.left, r.top, (r.right + dx).coerceIn(r.left + minSize, 1f), (r.bottom + dy).coerceIn(r.top + minSize, 1f))
                            5 -> r.copy(left = (r.left + dx).coerceIn(0f, r.right - minSize))
                            6 -> r.copy(top = (r.top + dy).coerceIn(0f, r.bottom - minSize))
                            7 -> r.copy(right = (r.right + dx).coerceIn(r.left + minSize, 1f))
                            8 -> r.copy(bottom = (r.bottom + dy).coerceIn(r.top + minSize, 1f))
                            else -> r
                        }
                        // Con proporción fija, el alto sigue al ancho desde la esquina contraria.
                        if (fixed > 0f && mode in 1..4) {
                            val hNorm = (r.width * imgRatio / fixed)
                            r = when (mode) {
                                1, 2 -> r.copy(top = (r.bottom - hNorm))
                                else -> r.copy(bottom = (r.top + hNorm))
                            }
                            if (r.top < 0f || r.bottom > 1f) r = current
                        }
                        current = r
                        onCrop(r)
                    }
                },
        ) {
            val dst = Rect(Offset(left, top), Size(w, h))
            drawImage(
                img,
                dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
                dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
                colorFilter = filter,
            )
            val r = Rect(left + current.left * w, top + current.top * h, left + current.right * w, top + current.bottom * h)
            clipRect(r.left, r.top, r.right, r.bottom, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
            drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.5.dp.toPx()))
            for (i in 1..2) {
                val x = r.left + r.width * i / 3
                val y = r.top + r.height * i / 3
                drawLine(Color.White.copy(alpha = 0.45f), Offset(x, r.top), Offset(x, r.bottom), 1f)
                drawLine(Color.White.copy(alpha = 0.45f), Offset(r.left, y), Offset(r.right, y), 1f)
            }
            val len = 22.dp.toPx()
            val sw = 4.dp.toPx()
            listOf(
                r.topLeft to Offset(1f, 1f), Offset(r.right, r.top) to Offset(-1f, 1f),
                Offset(r.left, r.bottom) to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f),
            ).forEach { (c, d) ->
                drawLine(Color.White, c, Offset(c.x + d.x * len, c.y), sw)
                drawLine(Color.White, c, Offset(c.x, c.y + d.y * len), sw)
            }
        }
    }
}
