package dev.liquidgallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import dev.liquidgallery.data.MediaRepo
import dev.liquidgallery.data.Stores
import dev.liquidgallery.data.Vault
import dev.liquidgallery.util.ThumbFetcher
import dev.liquidgallery.util.ThumbKeyer

class GalleryApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Stores.init(this)
        MediaRepo.init(this)
        Vault.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbFetcher.Factory(this@GalleryApp))
                add(ThumbKeyer())
                add(AnimatedImageDecoder.Factory())
            }
            .crossfade(120)
            .build()
}
