package dev.liquidgallery.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    /** Fecha en que se hizo (o, si no se sabe, en que se modificó), en ms. */
    val date: Long,
    val size: Long,
    /** Ancho y alto ya girados según la orientación. */
    val width: Int,
    val height: Int,
    /** Duración en ms (solo vídeos). */
    val duration: Long,
    /** Ruta relativa de su carpeta, p. ej. "DCIM/Camera/": identifica el álbum. */
    val album: String,
    val albumName: String,
    val favorite: Boolean,
    /** En la papelera: cuándo la borrará Android para siempre (ms). */
    val expires: Long = 0,
) {
    val isGif get() = mime == "image/gif"
}

@Immutable
data class Album(
    val path: String,
    val name: String,
    val cover: Media?,
    val count: Int,
    val latest: Long,
)

/** Nombre de un álbum: los habituales traducidos, el resto el de su carpeta. */
fun albumName(path: String, bucket: String?): String {
    val p = path.trimEnd('/').lowercase()
    return when {
        p == "dcim/camera" -> "Cámara"
        p.endsWith("/screenshots") -> "Capturas de pantalla"
        p.endsWith("/screen recordings") || p.endsWith("/screenrecorder") -> "Grabaciones de pantalla"
        p == "download" -> "Descargas"
        p.endsWith("whatsapp images") -> "WhatsApp"
        p.endsWith("whatsapp video") -> "WhatsApp Vídeo"
        else -> bucket ?: path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
    }
}

/** Álbumes que van primero, como en Samsung. */
fun isPinnedAlbum(path: String): Boolean = path.trimEnd('/').equals("DCIM/Camera", ignoreCase = true)

object MediaRepo {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    private val _media = MutableStateFlow<List<Media>>(emptyList())
    /** Todo menos la papelera, del más reciente al más antiguo. */
    val media: StateFlow<List<Media>> = _media.asStateFlow()

    private val _trash = MutableStateFlow<List<Media>>(emptyList())
    val trash: StateFlow<List<Media>> = _trash.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh(debounce = true)
        }
        app.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer,
        )
    }

    /** Vuelve a leer MediaStore. Con [debounce] agrupa ráfagas de cambios (p. ej. al mover 100 fotos). */
    fun refresh(debounce: Boolean = false) {
        pending?.cancel()
        pending = scope.launch {
            if (debounce) delay(400)
            val all = try {
                query(trashed = false)
            } catch (e: SecurityException) {
                emptyList()
            }
            _media.value = all
            _loaded.value = true
            _trash.value = try {
                query(trashed = true)
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }

    suspend fun reload() = withContext(Dispatchers.Default) {
        pending?.cancel()
        _media.value = query(trashed = false)
        _trash.value = query(trashed = true)
        _loaded.value = true
    }

    private fun query(trashed: Boolean): List<Media> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}," +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
        }
        val out = ArrayList<Media>()
        app.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, args, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iModified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iOrientation = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val iExpires = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(iTaken)
                val rotated = c.getInt(iOrientation).let { it == 90 || it == 270 }
                val w = c.getInt(iW)
                val h = c.getInt(iH)
                val path = c.getString(iPath) ?: ""
                out += Media(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = c.getString(iName) ?: "",
                    mime = c.getString(iMime) ?: if (video) "video/*" else "image/*",
                    isVideo = video,
                    date = if (taken > 0) taken else c.getLong(iModified) * 1000,
                    size = c.getLong(iSize),
                    width = if (rotated) h else w,
                    height = if (rotated) w else h,
                    duration = c.getLong(iDuration),
                    album = path,
                    albumName = albumName(path, c.getString(iBucket)),
                    favorite = c.getInt(iFav) == 1,
                    expires = c.getLong(iExpires) * 1000,
                )
            }
        }
        out.sortWith(compareByDescending<Media> { it.date }.thenByDescending { it.id })
        return out
    }
}

/** Álbumes a partir de la lista de elementos, más los vacíos que creó el usuario. */
fun buildAlbums(media: List<Media>, prefs: AlbumPrefs): List<Album> {
    val byPath = LinkedHashMap<String, MutableList<Media>>()
    media.forEach { byPath.getOrPut(it.album) { ArrayList() }.add(it) }
    val albums = byPath.map { (path, items) ->
        Album(path, items.first().albumName, items.first(), items.size, items.first().date)
    }.toMutableList()
    prefs.empty.filter { it !in byPath }.forEach { path ->
        albums += Album(path, albumName(path, null), null, 0, Long.MAX_VALUE)
    }
    return albums
}

/** Ordena según el ajuste: personalizado, nombre, número de elementos o más reciente. */
fun sortAlbums(albums: List<Album>, sort: Int, order: List<String>): List<Album> {
    val pinned = albums.filter { isPinnedAlbum(it.path) }
    val rest = albums.filterNot { isPinnedAlbum(it.path) }
    val sorted = when (sort) {
        1 -> rest.sortedBy { it.name.lowercase() }
        2 -> rest.sortedByDescending { it.count }
        3 -> rest.sortedByDescending { it.latest }
        else -> {
            val rank = order.withIndex().associate { it.value to it.index }
            rest.sortedWith(compareBy<Album> { rank[it.path] ?: Int.MAX_VALUE }.thenByDescending { it.latest })
        }
    }
    return pinned + sorted
}
