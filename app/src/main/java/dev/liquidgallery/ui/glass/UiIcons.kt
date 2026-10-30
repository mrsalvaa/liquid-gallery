package dev.liquidgallery.ui.glass

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Los íconos de la app se escriben con su versión Rounded (la de Liquid Glass); en el estilo
 * sólido se dibuja la versión Outlined, de líneas finas. Un ícono nuevo hay que sumarlo acá.
 */
private val SOLID_ICONS: Map<ImageVector, ImageVector> by lazy {
    mapOf(
        Icons.Rounded.Add to Icons.Outlined.Add,
        Icons.Rounded.Archive to Icons.Outlined.Archive,
        Icons.Rounded.AutoAwesome to Icons.Outlined.AutoAwesome,
        Icons.Rounded.Check to Icons.Outlined.Check,
        Icons.Rounded.CheckCircle to Icons.Outlined.CheckCircle,
        Icons.Rounded.Close to Icons.Outlined.Close,
        Icons.Rounded.ContentCopy to Icons.Outlined.ContentCopy,
        Icons.Rounded.CreateNewFolder to Icons.Outlined.CreateNewFolder,
        Icons.Rounded.Crop to Icons.Outlined.Crop,
        Icons.Rounded.Delete to Icons.Outlined.Delete,
        Icons.Rounded.DeleteForever to Icons.Outlined.DeleteForever,
        Icons.Rounded.DeleteSweep to Icons.Outlined.DeleteSweep,
        Icons.Rounded.DragHandle to Icons.Outlined.DragHandle,
        Icons.Rounded.DriveFileRenameOutline to Icons.Outlined.DriveFileRenameOutline,
        Icons.Rounded.Edit to Icons.Outlined.Edit,
        Icons.Rounded.Favorite to Icons.Outlined.Favorite,
        Icons.Rounded.FavoriteBorder to Icons.Outlined.FavoriteBorder,
        Icons.Rounded.Flip to Icons.Outlined.Flip,
        Icons.Rounded.Folder to Icons.Outlined.Folder,
        Icons.Rounded.FolderOff to Icons.Outlined.FolderOff,
        Icons.Rounded.Info to Icons.Outlined.Info,
        Icons.Rounded.Lock to Icons.Outlined.Lock,
        Icons.Rounded.Menu to Icons.Outlined.Menu,
        Icons.Rounded.MoreHoriz to Icons.Outlined.MoreHoriz,
        Icons.Rounded.MoreVert to Icons.Outlined.MoreVert,
        Icons.Rounded.Pause to Icons.Outlined.Pause,
        Icons.Rounded.Photo to Icons.Outlined.Photo,
        Icons.Rounded.PhotoAlbum to Icons.Outlined.PhotoAlbum,
        Icons.Rounded.PhotoLibrary to Icons.Outlined.PhotoLibrary,
        Icons.Rounded.PlayArrow to Icons.Outlined.PlayArrow,
        Icons.Rounded.PlayCircle to Icons.Outlined.PlayCircle,
        Icons.Rounded.Restore to Icons.Outlined.Restore,
        Icons.Rounded.RotateLeft to Icons.Outlined.RotateLeft,
        Icons.Rounded.Save to Icons.Outlined.Save,
        Icons.Rounded.SaveAs to Icons.Outlined.SaveAs,
        Icons.Rounded.Search to Icons.Outlined.Search,
        Icons.Rounded.Settings to Icons.Outlined.Settings,
        Icons.Rounded.Share to Icons.Outlined.Share,
        Icons.Rounded.Shield to Icons.Outlined.Shield,
        Icons.Rounded.SwapVert to Icons.Outlined.SwapVert,
        Icons.Rounded.Tune to Icons.Outlined.Tune,
        Icons.Rounded.Unarchive to Icons.Outlined.Unarchive,
        Icons.Rounded.Visibility to Icons.Outlined.Visibility,
        Icons.Rounded.VisibilityOff to Icons.Outlined.VisibilityOff,
        Icons.Rounded.Wallpaper to Icons.Outlined.Wallpaper,
        Icons.AutoMirrored.Rounded.ArrowBack to Icons.AutoMirrored.Outlined.ArrowBack,
        Icons.AutoMirrored.Rounded.DriveFileMove to Icons.AutoMirrored.Outlined.DriveFileMove,
        Icons.AutoMirrored.Rounded.KeyboardArrowRight to Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        Icons.AutoMirrored.Rounded.OpenInNew to Icons.AutoMirrored.Outlined.OpenInNew,
        Icons.AutoMirrored.Rounded.Sort to Icons.AutoMirrored.Outlined.Sort,
        Icons.AutoMirrored.Rounded.VolumeOff to Icons.AutoMirrored.Outlined.VolumeOff,
        Icons.AutoMirrored.Rounded.VolumeUp to Icons.AutoMirrored.Outlined.VolumeUp,
    )
}

/** El ícono que corresponde al estilo actual. */
@Composable
fun styled(icon: ImageVector): ImageVector =
    if (LocalGlass.current.solid) SOLID_ICONS[icon] ?: icon else icon
