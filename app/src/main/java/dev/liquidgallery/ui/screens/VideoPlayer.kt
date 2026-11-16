package dev.liquidgallery.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as SystemIcon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Theaters
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import dev.liquidgallery.R
import dev.liquidgallery.ui.glass.GlassChip
import dev.liquidgallery.ui.glass.GlassIconButton
import dev.liquidgallery.ui.glass.GlassSurface
import dev.liquidgallery.ui.glass.LocalGlass
import dev.liquidgallery.ui.glass.styled
import dev.liquidgallery.util.duration
import dev.liquidgallery.util.str
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Segundos que salta el doble toque (se elige en Ajustes). */
val SEEK_STEPS = listOf(5, 10, 15, 30)

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(s: Float) = if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"

/** Salto acumulado por toques seguidos: "+15 s". */
private data class Skip(val forward: Boolean, val seconds: Int, val nonce: Int)

/** Arrastre horizontal: de dónde salió y a dónde va. */
private data class Scrub(val from: Long, val to: Long, val active: Boolean)

/** Arrastre vertical: brillo (mitad izquierda) o volumen (mitad derecha), de 0 a 1. */
private data class Level(val volume: Boolean, val value: Float, val active: Boolean)

private const val ACTION_PIP_TOGGLE = "dev.liquidgallery.action.PIP_TOGGLE"

/** Si la actividad está en ventana flotante (PiP): ahí no se dibuja ningún control. */
@Composable
internal fun rememberInPip(): Boolean {
    val activity = LocalView.current.context as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

private const val MODE_NONE = 0
private const val MODE_SEEK = 1
private const val MODE_BRIGHTNESS = 2
private const val MODE_VOLUME = 3
private const val MODE_PINCH = 4
private const val MODE_IGNORE = 5

/**
 * Controles del reproductor, encima del vídeo que se está viendo en el visor:
 * - un toque muestra u oculta los controles; dos toques a los lados saltan [seekSeconds] (y cada
 *   toque seguido suma), dos en el centro pausan;
 * - deslizar de lado adelanta o atrasa con un contador; deslizar hacia arriba o abajo cambia el
 *   brillo (izquierda) o el volumen (derecha); mantener pulsado reproduce a 2x;
 * - pellizcar ([fill]) hace que el vídeo llene la pantalla; el modo cine ([cinema]) ilumina las
 *   franjas negras con los colores del vídeo;
 * - ventana flotante ([pip]; false donde no se ofrece, como en Oculto), con su botón de pausa;
 * - bloqueo de toques, girar la pantalla, velocidad, repetir y silencio.
 */
@Composable
fun VideoPlayerOverlay(
    player: ExoPlayer,
    title: String,
    seekSeconds: Int,
    fill: Boolean,
    onFill: (Boolean) -> Unit,
    cinema: Boolean,
    onCinema: (Boolean) -> Unit,
    pip: Boolean,
    onExit: () -> Unit,
) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val view = LocalView.current
    val activity = view.context as? Activity
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val step by rememberUpdatedState(seekSeconds)
    val setFill by rememberUpdatedState(onFill)
    val inPip = rememberInPip()

    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var unlockHint by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var loop by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var sliding by remember { mutableStateOf<Float?>(null) }
    var skip by remember { mutableStateOf<Skip?>(null) }
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    var level by remember { mutableStateOf<Level?>(null) }
    // Cambia con cada uso de los controles, para que no se oculten mientras se tocan.
    var touched by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val orientation = activity?.requestedOrientation
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player.setPlaybackSpeed(1f)
            player.setSeekParameters(SeekParameters.DEFAULT)
            activity?.let { a ->
                if (orientation != null) a.requestedOrientation = orientation
                a.window.attributes = a.window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
    LaunchedEffect(player) {
        while (true) {
            if (sliding == null) position = player.currentPosition
            length = player.duration.coerceAtLeast(0)
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
            if (player.playbackState == Player.STATE_ENDED && !locked) controls = true
            delay(200)
        }
    }
    LaunchedEffect(controls, playing, touched, speedMenu) {
        if (controls && playing && sliding == null) {
            delay(if (speedMenu) 6000 else 3500)
            controls = false
            speedMenu = false
        }
    }
    LaunchedEffect(skip) {
        if (skip != null) {
            delay(800)
            skip = null
        }
    }
    LaunchedEffect(scrub) {
        if (scrub?.active == false) {
            delay(500)
            scrub = null
        }
    }
    LaunchedEffect(level) {
        if (level?.active == false) {
            delay(700)
            level = null
        }
    }
    LaunchedEffect(unlockHint) {
        if (unlockHint > 0) {
            delay(3000)
            unlockHint = 0
        }
    }
    BackHandler { if (locked) unlockHint++ else onExit() }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
        playing = player.playWhenReady
        touched++
    }

    fun skipBy(forward: Boolean) {
        val total = player.duration
        if (total <= 0) return
        val target = (player.currentPosition + (if (forward) 1 else -1) * step * 1000L).coerceIn(0, total)
        player.seekTo(target)
        position = target
        val previous = skip
        skip = Skip(
            forward,
            if (previous != null && previous.forward == forward) previous.seconds + step else step,
            (previous?.nonce ?: 0) + 1,
        )
        touched++
    }

    // Ventana flotante: con la forma del vídeo y un botón para pausar o seguir.
    fun pipParams(): PictureInPictureParams {
        val video = player.videoSize
        val ratio = if (video.width > 0 && video.height > 0) {
            video.width * video.pixelWidthHeightRatio / video.height
        } else {
            16f / 9f
        }.coerceIn(0.42f, 2.38f)
        val label = str(if (playing) R.string.pause else R.string.play)
        val toggle = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_PIP_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = SystemIcon.createWithResource(context, if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .setActions(listOf(RemoteAction(icon, label, label, toggle)))
            .build()
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = togglePlay()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(playing, inPip) {
        if (inPip) activity?.setPictureInPictureParams(pipParams())
    }
    // Al volver de la ventana flotante, los controles quedan a la vista.
    LaunchedEffect(inPip) {
        if (!inPip) controls = true
    }

    fun brightness(): Float {
        val own = activity?.window?.attributes?.screenBrightness ?: -1f
        if (own >= 0f) return own
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0f, 1f)
    }

    if (inPip) return
    Box(Modifier.fillMaxSize()) {
        // Capa de gestos: debajo de los botones, encima del vídeo.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { at ->
                            val s = skip
                            val third = size.width / 3f
                            when {
                                locked -> unlockHint++
                                // Tras un doble toque, cada toque más del mismo lado sigue sumando.
                                s != null && s.forward && at.x > third * 2 -> skipBy(true)
                                s != null && !s.forward && at.x < third -> skipBy(false)
                                else -> {
                                    controls = !controls
                                    speedMenu = false
                                }
                            }
                        },
                        onDoubleTap = { at ->
                            val third = size.width / 3f
                            when {
                                locked -> unlockHint++
                                at.x < third -> skipBy(false)
                                at.x > third * 2 -> skipBy(true)
                                else -> togglePlay()
                            }
                        },
                        onLongPress = {
                            if (!locked && player.isPlaying) {
                                boosting = true
                                player.setPlaybackSpeed(2f)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (boosting) {
                                boosting = false
                                player.setPlaybackSpeed(speed)
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var mode = MODE_NONE
                        var total = Offset.Zero
                        var zoom = 1f
                        var from = 0L
                        var target = 0L
                        var sent = 0L
                        var span = 1L
                        var startX = 0f
                        var value = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed == 0) break
                                if (pressed >= 2 && (mode == MODE_NONE || mode == MODE_PINCH)) {
                                    mode = MODE_PINCH
                                    zoom *= event.calculateZoom()
                                    if (zoom > 1.12f) setFill(true) else if (zoom < 0.89f) setFill(false)
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange()
                                total += d
                                if (mode == MODE_NONE && total.getDistance() > viewConfiguration.touchSlop) {
                                    val total_ms = player.duration
                                    mode = when {
                                        boosting -> MODE_IGNORE
                                        abs(total.x) > abs(total.y) -> if (total_ms > 0) MODE_SEEK else MODE_IGNORE
                                        down.position.x < size.width / 2f -> MODE_BRIGHTNESS
                                        else -> MODE_VOLUME
                                    }
                                    when (mode) {
                                        MODE_SEEK -> {
                                            from = player.currentPosition
                                            target = from
                                            sent = from
                                            startX = total.x
                                            // Todo el ancho recorre el vídeo entero, o minuto y medio si es largo.
                                            span = total_ms.coerceAtMost(90_000L)
                                            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                        }
                                        MODE_BRIGHTNESS -> value = brightness()
                                        MODE_VOLUME -> {
                                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            value = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
                                        }
                                    }
                                }
                                when (mode) {
                                    MODE_SEEK -> {
                                        target = (from + ((total.x - startX) / size.width * span).toLong()).coerceIn(0, player.duration)
                                        if (abs(target - sent) >= 250) {
                                            player.seekTo(target)
                                            sent = target
                                        }
                                        scrub = Scrub(from, target, true)
                                        position = target
                                    }
                                    MODE_BRIGHTNESS -> {
                                        value = (value - d.y / (size.height * 0.6f)).coerceIn(0.01f, 1f)
                                        activity?.let { a ->
                                            a.window.attributes = a.window.attributes.apply { screenBrightness = value }
                                        }
                                        level = Level(false, value, true)
                                    }
                                    MODE_VOLUME -> {
                                        value = (value - d.y / (size.height * 0.6f)).coerceIn(0f, 1f)
                                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0)
                                        if (value > 0f && muted) {
                                            muted = false
                                            player.volume = 1f
                                        }
                                        level = Level(true, value, true)
                                    }
                                }
                                if (mode != MODE_NONE && mode != MODE_IGNORE) change.consume()
                            }
                        } finally {
                            if (mode == MODE_SEEK) {
                                player.setSeekParameters(SeekParameters.DEFAULT)
                                player.seekTo(target)
                                scrub = Scrub(from, target, false)
                                touched++
                            }
                            level?.let { level = it.copy(active = false) }
                        }
                    }
                },
        )

        // Contador del doble toque, del lado al que salta.
        skip?.let { s ->
            Pill(
                Modifier
                    .align(if (s.forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp),
            ) {
                if (!s.forward) {
                    Icon(Icons.Rounded.FastRewind, null, Modifier.size(22.dp), tint = p.content)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    (if (s.forward) "+" else "−") + str(R.string.seconds_short, s.seconds),
                    color = p.content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                )
                if (s.forward) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.FastForward, null, Modifier.size(22.dp), tint = p.content)
                }
            }
        }

        // Contador al deslizar: cuánto se movió y dónde queda.
        scrub?.let { s ->
            val delta = s.to - s.from
            Pill(Modifier.align(BiasAlignment(0f, -0.45f))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        (if (delta < 0) "−" else "+") + duration(abs(delta)),
                        color = p.content, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("${duration(s.to)} / ${duration(length)}", color = p.contentSecondary, fontSize = 13.sp)
                }
            }
        }

        // Brillo o volumen.
        level?.let { l ->
            Pill(Modifier.align(BiasAlignment(0f, -0.45f))) {
                Icon(
                    when {
                        !l.volume -> Icons.Rounded.BrightnessMedium
                        l.value == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    str(if (l.volume) R.string.volume else R.string.brightness),
                    Modifier.size(22.dp), tint = p.content,
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.width(120.dp).height(6.dp).clip(CircleShape).drawBehind {
                        drawRect(Color.White.copy(alpha = 0.25f))
                        drawRect(Color.White, size = Size(size.width * l.value, size.height))
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${(l.value * 100).roundToInt()}%",
                    Modifier.width(44.dp), color = p.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Mantener pulsado: 2x mientras dure.
        if (boosting) {
            Pill(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 76.dp)) {
                Text("2x", color = p.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.FastForward, null, Modifier.size(20.dp), tint = p.content)
            }
        }

        // Bloqueado: solo queda el candado, que aparece al tocar.
        AnimatedVisibility(
            locked && unlockHint > 0,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 28.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GlassSurface(
                shape = CircleShape, strong = true, frost = 4.dp,
                onClick = {
                    locked = false
                    unlockHint = 0
                    controls = true
                    touched++
                },
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LockOpen, null, Modifier.size(22.dp), tint = p.content)
                    Spacer(Modifier.width(10.dp))
                    Text(str(R.string.unlock), color = p.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(controls && !locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // Arriba: salir, nombre y sonido.
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back), onExit)
                    Text(
                        title,
                        Modifier.weight(1f).padding(horizontal = 14.dp),
                        color = p.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (pip) {
                        GlassIconButton(Icons.Rounded.PictureInPictureAlt, str(R.string.player_pip), {
                            controls = false
                            runCatching { activity?.enterPictureInPictureMode(pipParams()) }
                        })
                        Spacer(Modifier.width(10.dp))
                    }
                    GlassIconButton(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        str(R.string.sound),
                        {
                            muted = !muted
                            player.volume = if (muted) 0f else 1f
                            touched++
                        },
                    )
                }

                // Centro: saltar atrás, reproducir o pausar, saltar adelante.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.Rounded.FastRewind, str(R.string.rewind), { skipBy(false) }, size = 56.dp)
                    GlassIconButton(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        str(if (playing) R.string.pause else R.string.play),
                        { togglePlay() },
                        size = 76.dp,
                    )
                    GlassIconButton(Icons.Rounded.FastForward, str(R.string.forward), { skipBy(true) }, size = 56.dp)
                }

                // Abajo: barra de progreso y botones.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (landscape) 8.dp else 12.dp),
                ) {
                    GlassSurface(Modifier.fillMaxWidth(), shape = CircleShape, strong = true, frost = 4.dp) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(duration(sliding?.toLong() ?: position), color = p.content, fontSize = 12.sp)
                            SeekBar(
                                value = (sliding ?: position.toFloat()).coerceIn(0f, length.toFloat().coerceAtLeast(1f)),
                                max = length.toFloat().coerceAtLeast(1f),
                                onChange = {
                                    sliding = it
                                    touched++
                                },
                                onDone = {
                                    sliding?.let {
                                        player.seekTo(it.toLong())
                                        position = it.toLong()
                                    }
                                    sliding = null
                                    touched++
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            Text(duration(length), color = p.content, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(if (landscape) 8.dp else 10.dp))
                    if (speedMenu) {
                        Row(
                            Modifier.fillMaxWidth().height(60.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SPEEDS.forEach { s ->
                                GlassChip(speedLabel(s), s == speed, {
                                    speed = s
                                    player.setPlaybackSpeed(s)
                                    speedMenu = false
                                    touched++
                                })
                            }
                        }
                    } else {
                        GlassSurface(Modifier.fillMaxWidth().height(60.dp), shape = CircleShape, strong = true, frost = 4.dp) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BarButton(Icons.Rounded.Lock, str(R.string.player_lock), p.content) {
                                    locked = true
                                    unlockHint = 0
                                }
                                BarButton(Icons.Rounded.ScreenRotation, str(R.string.player_rotate), p.content) {
                                    activity?.requestedOrientation = if (landscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                    touched++
                                }
                                BarButton(
                                    Icons.Rounded.Theaters,
                                    str(R.string.player_cinema),
                                    if (cinema) p.accent else p.content,
                                ) {
                                    onCinema(!cinema)
                                    touched++
                                }
                                Box(
                                    Modifier.size(52.dp).clickable(remember { MutableInteractionSource() }, null) {
                                        speedMenu = true
                                        touched++
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        speedLabel(speed),
                                        color = if (speed != 1f) p.accent else p.content,
                                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                BarButton(
                                    if (loop) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    str(R.string.player_repeat),
                                    if (loop) p.accent else p.content,
                                ) {
                                    loop = !loop
                                    player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    touched++
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de cristal para los avisos del reproductor (contadores, brillo, volumen). */
@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    GlassSurface(modifier, shape = RoundedCornerShape(26.dp), strong = true, frost = 4.dp) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Barra de progreso fina, con un punto blanco que se arrastra o se lleva de un toque. */
@Composable
private fun SeekBar(value: Float, max: Float, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalGlass.current.accent
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Box(
        modifier
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..max)
                setProgress {
                    change(it.coerceIn(0f, max))
                    done()
                    true
                }
            }
            .pointerInput(max) {
                val edge = 8.dp.toPx()
                fun at(x: Float) = ((x - edge) / (size.width - edge * 2)).coerceIn(0f, 1f) * max
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    change(at(down.position.x))
                    try {
                        drag(down.id) {
                            it.consume()
                            change(at(it.position.x))
                        }
                    } finally {
                        done()
                    }
                }
            }
            .drawBehind {
                val edge = 8.dp.toPx()
                val thick = 4.dp.toPx()
                val y = (size.height - thick) / 2f
                val width = size.width - edge * 2
                val x = edge + width * (value / max).coerceIn(0f, 1f)
                val round = CornerRadius(thick / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(edge, y), Size(width, thick), round)
                drawRoundRect(accent, Offset(edge, y), Size(x - edge, thick), round)
                drawCircle(Color.White, 7.dp.toPx(), Offset(x, size.height / 2f))
            },
    )
}
