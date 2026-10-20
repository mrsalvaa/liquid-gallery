package dev.liquidgallery

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.fragment.app.FragmentActivity
import dev.liquidgallery.util.preferHighestRefreshRate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.liquidgallery.data.ConsentRunner
import dev.liquidgallery.data.Media
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.ui.GalleryHost
import dev.liquidgallery.ui.SystemBarsFor
import dev.liquidgallery.ui.glass.GlassTheme
import dev.liquidgallery.ui.glass.isDarkTheme
import dev.liquidgallery.ui.screens.EditorScreen
import dev.liquidgallery.ui.screens.Source
import dev.liquidgallery.ui.screens.ViewerScreen
import dev.liquidgallery.ui.screens.mediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.liquidgallery.util.str

/**
 * Abre una foto o un vídeo que manda otra app. Si está en la galería se puede deslizar por su
 * álbum y usar todas las acciones; si no, solo verlo y compartirlo.
 */
class ViewerActivity : FragmentActivity() {
    private val consent = ConsentRunner(this)

    /** null mientras se busca; luego el álbum y el elemento, o una lista externa. */
    private var target by mutableStateOf<Target?>(null)

    private sealed interface Target {
        data class InGallery(val media: Media) : Target
        data class External(val media: Media) : Target
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch { target = resolve(uri) }
        setContent {
            val settings by Stores.settings.state.collectAsStateWithLifecycle()
            SystemBarsFor(isDarkTheme(settings.themeMode))
            GlassTheme(settings) {
                var editing by androidx.compose.runtime.remember { mutableStateOf<Media?>(null) }
                GalleryHost(
                    consent = consent,
                    animated = false,
                    onPickAlbum = { _, _ -> },
                ) {
                    when (val t = target) {
                        null -> Box(Modifier.fillMaxSize().background(Color.Black))
                        is Target.InGallery -> {
                            val e = editing
                            if (e != null) {
                                EditorScreen(e, { editing = null }, { editing = null })
                            } else {
                                ViewerScreen(Source.Album(t.media.album), t.media.id, { finish() }, { editing = it })
                            }
                        }
                        is Target.External -> ViewerScreen(Source.All, t.media.id, { finish() }, {}, external = listOf(t.media))
                    }
                }
            }
        }
    }

    private suspend fun resolve(uri: Uri): Target = withContext(Dispatchers.IO) {
        // ¿Es un elemento de MediaStore que ya conocemos?
        if (uri.authority == MediaStore.AUTHORITY && mediaAccess(this@ViewerActivity) > 0) {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null) {
                runCatching { MediaRepo.reload() }
                MediaRepo.media.value.firstOrNull { it.id == id }?.let { return@withContext Target.InGallery(it) }
            }
        }
        var name = uri.lastPathSegment ?: str(R.string.file)
        var size = 0L
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        size = c.getLong(1)
                    }
                }
            }
        }
        val mime = intent.type ?: contentResolver.getType(uri) ?: "image/*"
        Target.External(
            Media(
                id = -1, uri = uri, name = name, mime = mime, isVideo = mime.startsWith("video"),
                date = System.currentTimeMillis(), size = size, width = 0, height = 0, duration = 0,
                album = "", albumName = "", favorite = false,
            ),
        )
    }
}
