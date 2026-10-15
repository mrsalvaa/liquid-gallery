package dev.liquidgallery.util

import android.app.Activity

/**
 * Pide la frecuencia de refresco más alta de la pantalla (120 o 144 Hz) con la misma resolución.
 * Sin esto, algunos móviles dejan la app a 60 Hz aunque la pantalla dé más.
 */
fun Activity.preferHighestRefreshRate() {
    val display = display ?: return
    val current = display.mode
    val best = display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate } ?: return
    if (best.modeId != window.attributes.preferredDisplayModeId) {
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}
