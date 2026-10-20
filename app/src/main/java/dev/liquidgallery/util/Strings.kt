package dev.liquidgallery.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Textos traducidos, también fuera de Compose. Usa los recursos de la aplicación, que siguen el
 * idioma del teléfono (al cambiarlo, Android recrea la actividad y todo se vuelve a pintar).
 */
object Strings {
    lateinit var app: Context
}

fun str(@StringRes id: Int, vararg args: Any): String = Strings.app.getString(id, *args)

fun plural(@PluralsRes id: Int, n: Int, vararg args: Any): String =
    Strings.app.resources.getQuantityString(id, n, *(if (args.isEmpty()) arrayOf(n) else args))
