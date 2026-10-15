package dev.liquidgallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/**
 * Bóveda privada: lo oculto se mueve a archivos internos de la app, donde ninguna otra app ni
 * galería puede verlo. No entra en copias de seguridad (allowBackup = false).
 * Si se desinstala la app sin sacar lo que hay aquí, se pierde.
 */
object Vault {
    private lateinit var dir: File
    private lateinit var store: JsonStore<List<VaultItem>>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var media: StateFlow<List<Media>>
        private set

    fun init(context: Context) {
        dir = File(context.filesDir, "vault").apply { mkdirs() }
        store = JsonStore(File(context.filesDir, "vault.json"), ListSerializer(VaultItem.serializer()), emptyList())
        media = store.state
            .map { list -> list.sortedByDescending { it.date }.map { it.toMedia() } }
            .stateIn(scope, SharingStarted.Eagerly, store.value.sortedByDescending { it.date }.map { it.toMedia() })
    }

    fun file(m: Media): File? = store.value.firstOrNull { it.id == m.id }?.let { File(dir, it.file) }

    private fun VaultItem.toMedia() = Media(
        id = id, uri = Uri.fromFile(File(dir, file)), name = name, mime = mime, isVideo = video,
        date = date, size = size, width = width, height = height, duration = duration,
        album = album, albumName = albumName, favorite = false,
    )

    /** Copia a la bóveda y borra los originales (con la confirmación de Android). */
    suspend fun hide(context: Context, ops: MediaOps, items: List<Media>): Int {
        val todo = items.filter { it.id >= 0 }
        if (todo.isEmpty()) return 0
        val copied = withContext(Dispatchers.IO) {
            todo.mapNotNull { m ->
                val ext = m.name.substringAfterLast('.', if (m.isVideo) "mp4" else "jpg")
                val name = "${UUID.randomUUID()}.$ext"
                val target = File(dir, name)
                runCatching {
                    context.contentResolver.openInputStream(m.uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                    VaultItem(
                        id = -(System.nanoTime() and Long.MAX_VALUE) - 1, file = name, name = m.name, mime = m.mime,
                        video = m.isVideo, date = m.date, size = target.length(), width = m.width, height = m.height,
                        duration = m.duration, album = m.album, albumName = m.albumName,
                    )
                }.getOrElse {
                    target.delete()
                    null
                }
            }
        }
        if (copied.isEmpty()) return 0
        // Si no se pudieron borrar los originales, se deshace: no queremos duplicados.
        if (!ops.deleteForever(todo)) {
            withContext(Dispatchers.IO) { copied.forEach { File(dir, it.file).delete() } }
            return 0
        }
        store.update { it + copied }
        return copied.size
    }

    /** Devuelve a su carpeta original (o a Pictures/Restaurados si ya no se puede). */
    suspend fun restore(context: Context, items: List<Media>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val byId = store.value.associateBy { it.id }
        val done = items.mapNotNull { byId[it.id] }.filter { v ->
            val source = File(dir, v.file)
            fun insert(path: String): Uri? = runCatching {
                val collection = if (v.video) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, v.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, v.mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                        put(MediaStore.MediaColumns.DATE_TAKEN, v.date)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                )
            }.getOrNull()
            val target = insert(v.album.ifEmpty { "Pictures/Restaurados/" }) ?: insert("Pictures/Restaurados/") ?: return@filter false
            try {
                source.inputStream().use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                source.delete()
                true
            } catch (e: Exception) {
                resolver.delete(target, null, null)
                false
            }
        }
        val ids = done.map { it.id }.toSet()
        store.update { list -> list.filter { it.id !in ids } }
        MediaRepo.refresh()
        done.size
    }

    /** Borra para siempre de la bóveda. */
    suspend fun delete(items: List<Media>) = withContext(Dispatchers.IO) {
        val ids = items.map { it.id }.toSet()
        store.value.filter { it.id in ids }.forEach { File(dir, it.file).delete() }
        store.update { list -> list.filter { it.id !in ids } }
    }
}

/** Desbloqueo de lo oculto con huella, cara o el PIN/patrón del móvil. Se bloquea al salir de la app. */
object VaultLock {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun lock() {
        _unlocked.value = false
    }

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** null si se puede pedir; si no, el motivo para mostrarlo. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Configura un bloqueo de pantalla (PIN, patrón o huella) en los ajustes de Android para usar Oculto."
            else -> "Este móvil no permite desbloquear con huella ni PIN."
        }

    fun unlock(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
        if (_unlocked.value) return onResult(true, null)
        unavailableReason(activity)?.let { return onResult(false, it) }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _unlocked.value = true
                    onResult(true, null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false, null)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Oculto")
                .setSubtitle("Desbloquea para ver lo oculto")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
