// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidgallery.ui.glass

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Los íconos de la app se escriben con su versión Rounded (la de Liquid Glass); en el estilo
 * sólido se dibuja la versión Outlined, de líneas finas. Un ícono nuevo hay que sumarlo acá.
 */
private val SOLID_ICONS: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        Icons.Rounded.DeleteOutline to Icons.Outlined.DeleteOutline,
        Icons.Rounded.FileDownload to Icons.Outlined.FileDownload,
        Icons.Rounded.Add to Icons.Outlined.Add,
        Icons.Rounded.Remove to Icons.Outlined.Remove,
        Icons.Rounded.PhoneAndroid to Icons.Outlined.PhoneAndroid,
        Icons.Rounded.Sync to Icons.Outlined.Sync,
        Icons.Rounded.RssFeed to Icons.Outlined.RssFeed,
        Icons.Rounded.Cloud to Icons.Outlined.Cloud,
        Icons.Rounded.CalendarMonth to Icons.Outlined.CalendarMonth,
        Icons.Rounded.ViewAgenda to Icons.Outlined.ViewAgenda,
        Icons.Rounded.Settings to Icons.Outlined.Settings,
        Icons.Rounded.RadioButtonUnchecked to Icons.Outlined.RadioButtonUnchecked,
        Icons.Rounded.RadioButtonChecked to Icons.Outlined.RadioButtonChecked,
        Icons.Rounded.Layers to Icons.Outlined.Layers,
        Icons.Rounded.FileUpload to Icons.Outlined.FileUpload,
        Icons.Rounded.EventRepeat to Icons.Outlined.EventRepeat,
        Icons.Rounded.Event to Icons.Outlined.Event,
        Icons.Rounded.Edit to Icons.Outlined.Edit,
        Icons.Rounded.CloudOff to Icons.Outlined.CloudOff,
        Icons.Rounded.CalendarViewWeek to Icons.Outlined.CalendarViewWeek,
        Icons.Rounded.CalendarViewDay to Icons.Outlined.CalendarViewDay,
        Icons.AutoMirrored.Rounded.KeyboardArrowRight to Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        Icons.AutoMirrored.Rounded.KeyboardArrowLeft to Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
        Icons.AutoMirrored.Rounded.ArrowBack to Icons.AutoMirrored.Outlined.ArrowBack,
    )
}

/** El ícono que corresponde al estilo actual. */
@Composable
fun styled(icon: ImageVector): ImageVector =
    if (LocalGlass.current.solid) SOLID_ICONS[icon] ?: icon else icon
