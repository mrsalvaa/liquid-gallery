package dev.liquidgallery.data

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lanza las confirmaciones de Android (borrar, mover, favoritos...) y espera la respuesta.
 * Con el permiso "Gestionar multimedia" Android las aprueba sin mostrar nada.
 * Hay que crearlo al construir la actividad (antes de onStart).
 */
class ConsentRunner(activity: ComponentActivity) {
    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        pending?.complete(it.resultCode == Activity.RESULT_OK)
        pending = null
    }

    suspend fun run(sender: IntentSender): Boolean {
        pending?.complete(false)
        val result = CompletableDeferred<Boolean>()
        pending = result
        launcher.launch(IntentSenderRequest.Builder(sender).build())
        return result.await()
    }
}

val LocalConsent = staticCompositionLocalOf<ConsentRunner> { error("Falta ConsentRunner") }

/** Operaciones sobre fotos y vídeos. Todas devuelven si se hicieron y refrescan la lista. */
class MediaOps(private val context: Context, private val consent: ConsentRunner) {
    private val resolver get() = context.contentResolver

    private suspend fun <T> done(block: suspend () -> T): T = block().also { MediaRepo.refresh() }

    suspend fun setFavorite(items: List<Media>, favorite: Boolean): Boolean = done {
        if (items.isEmpty()) return@done false
        consent.run(MediaStore.createFavoriteRequest(resolver, items.map { it.uri }, favorite).intentSender)
    }

    /** A la papelera: Android las borra solas a los 30 días. */
    suspend fun trash(items: List<Media>): Boolean = done {
        if (items.isEmpty()) return@done false
        consent.run(MediaStore.createTrashRequest(resolver, items.map { it.uri }, true).intentSender)
    }

    suspend fun restore(items: List<Media>): Boolean = done {
        if (items.isEmpty()) return@done false
        consent.run(MediaStore.createTrashRequest(resolver, items.map { it.uri }, false).intentSender)
    }

    suspend fun deleteForever(items: List<Media>): Boolean = done {
        if (items.isEmpty()) return@done false
        consent.run(MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender)
    }

    private suspend fun requestWrite(uris: List<Uri>): Boolean =
        consent.run(MediaStore.createWriteRequest(resolver, uris).intentSender)

    /** Mueve a otra carpeta (álbum). Devuelve cuántos se movieron. */
    suspend fun move(items: List<Media>, targetPath: String): Int = done {
        val todo = items.filter { it.album != targetPath }
        if (todo.isEmpty() || !requestWrite(todo.map { it.uri })) return@done 0
        withContext(Dispatchers.IO) {
            todo.count { m ->
                runCatching {
                    resolver.update(m.uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath) }, null, null) > 0
                }.getOrDefault(false)
            }
        }.also { if (it > 0) forgetEmpty(targetPath) }
    }

    /** Copia a otra carpeta (álbum). No necesita permiso: las copias son de la app. */
    suspend fun copy(items: List<Media>, targetPath: String): Int = done {
        withContext(Dispatchers.IO) {
            items.count { m ->
                runCatching {
                    val target = insert(m.name, m.mime, m.isVideo, targetPath, m.date) ?: return@runCatching false
                    try {
                        resolver.openInputStream(m.uri)!!.use { input ->
                            resolver.openOutputStream(target)!!.use { input.copyTo(it) }
                        }
                        publish(target)
                        true
                    } catch (e: Exception) {
                        resolver.delete(target, null, null)
                        false
                    }
                }.getOrDefault(false)
            }
        }.also { if (it > 0) forgetEmpty(targetPath) }
    }

    /** Cambia el nombre manteniendo la extensión. */
    suspend fun rename(item: Media, newName: String): Boolean = done {
        val ext = item.name.substringAfterLast('.', "")
        val base = newName.trim().removeSuffix(".$ext").ifEmpty { return@done false }
        if (!requestWrite(listOf(item.uri))) return@done false
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.update(
                    item.uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, if (ext.isEmpty()) base else "$base.$ext") },
                    null, null,
                ) > 0
            }.getOrDefault(false)
        }
    }

    /** Guarda una foto editada como copia junto a la original. Devuelve la nueva Uri. */
    suspend fun saveCopy(original: Media, bitmap: Bitmap): Uri? = done {
        withContext(Dispatchers.IO) {
            val format = formatFor(original.mime)
            val base = original.name.substringBeforeLast('.')
            val name = "${base}_editada.${format.second}"
            val target = insert(name, format.third, false, original.album, System.currentTimeMillis())
                ?: return@withContext null
            try {
                resolver.openOutputStream(target)!!.use { bitmap.compress(format.first, 95, it) }
                publish(target)
                target
            } catch (e: Exception) {
                resolver.delete(target, null, null)
                null
            }
        }
    }

    /** Sobrescribe la original con la versión editada. */
    suspend fun overwrite(original: Media, bitmap: Bitmap): Boolean = done {
        if (!requestWrite(listOf(original.uri))) return@done false
        withContext(Dispatchers.IO) {
            runCatching {
                val format = formatFor(original.mime)
                resolver.openOutputStream(original.uri, "wt")!!.use { bitmap.compress(format.first, 95, it) }
                // La foto ya va girada: se quita la orientación que tuviera.
                resolver.update(
                    original.uri,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.ORIENTATION, 0)
                        put(MediaStore.MediaColumns.WIDTH, bitmap.width)
                        put(MediaStore.MediaColumns.HEIGHT, bitmap.height)
                    },
                    null, null,
                )
                true
            }.getOrDefault(false)
        }
    }

    private fun formatFor(mime: String): Triple<Bitmap.CompressFormat, String, String> = when (mime) {
        "image/png" -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
        "image/webp" -> Triple(Bitmap.CompressFormat.WEBP_LOSSY, "webp", "image/webp")
        else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
    }

    private fun insert(name: String, mime: String, video: Boolean, path: String, date: Long): Uri? {
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        return resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                put(MediaStore.MediaColumns.DATE_TAKEN, date)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        )
    }

    private fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun forgetEmpty(path: String) {
        if (path in Stores.albums.value.empty) Stores.albums.update { it.copy(empty = it.empty - path) }
    }
}

/** Carpeta para un álbum nuevo: Pictures/<nombre>/ (ahí caben fotos y vídeos). */
fun newAlbumPath(name: String): String =
    "Pictures/" + name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_") + "/"
