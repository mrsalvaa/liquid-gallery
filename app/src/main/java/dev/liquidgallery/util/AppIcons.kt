package dev.liquidgallery.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.liquidgallery.R

/** Iconos alternativos de la app, cada uno es un activity-alias del manifest. */
object AppIcons {
    data class Icon(val name: String, val alias: String, val background: Int, val foreground: Int)

    val all = listOf(
        Icon("Liquid", "IconLiquid", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
        Icon("Material", "IconMaterial", R.drawable.ic_launcher_material_background, R.drawable.ic_launcher_material_foreground),
        Icon("Samsung", "IconSamsung", R.drawable.ic_launcher_samsung_background, R.drawable.ic_launcher_samsung_foreground),
        Icon("iPhone", "IconIphone", R.drawable.ic_launcher_iphone_background, R.drawable.ic_launcher_iphone_foreground),
        Icon("Motorola", "IconMotorola", R.drawable.ic_launcher_motorola_background, R.drawable.ic_launcher_motorola_foreground),
    )

    /** Activa el alias elegido y desactiva el resto (primero activa, para no quedarse sin icono). */
    fun apply(context: Context, index: Int) {
        val pm = context.packageManager
        fun set(icon: Icon, enabled: Boolean) = pm.setComponentEnabledSetting(
            ComponentName(context.packageName, "dev.liquidgallery.${icon.alias}"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        val chosen = all.getOrElse(index) { all[0] }
        set(chosen, true)
        all.filter { it != chosen }.forEach { set(it, false) }
    }
}
