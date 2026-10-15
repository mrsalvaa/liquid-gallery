package dev.liquidgallery.util

import android.content.Context
import android.media.ThumbnailUtils
import java.io.File
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import dev.liquidgallery.data.Media

/**
 * Miniatura de una foto o un vídeo. Usa las que Android ya tiene guardadas (rápido y sin
 * decodificar la foto entera). [version] cambia cuando se edita el archivo.
 */
data class Thumb(val uri: Uri, val version: Long, val px: Int)

fun Media.thumb(px: Int = 384) = Thumb(uri, date xor size, px)

class ThumbFetcher(private val context: Context, private val data: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { data.px }.coerceAtMost(data.px)
        val h = options.size.height.pxOrElse { data.px }.coerceAtMost(data.px)
        val side = maxOf(w, h, 96)
        val bitmap = if (data.uri.scheme == "file") {
            // Archivos de la bóveda: Android no tiene miniaturas de ellos.
            val file = File(data.uri.path!!)
            val video = file.extension.lowercase() in VIDEO_EXTENSIONS
            if (video) {
                ThumbnailUtils.createVideoThumbnail(file, Size(side, side), null)
            } else {
                ThumbnailUtils.createImageThumbnail(file, Size(side, side), null)
            }
        } else {
            context.contentResolver.loadThumbnail(data.uri, Size(side, side), null)
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(context, data, options)
    }
}

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.uri}:${data.version}:${data.px}"
}
