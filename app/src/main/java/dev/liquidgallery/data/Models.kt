package dev.liquidgallery.data

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    /** 0 = según el sistema, 1 = claro, 2 = oscuro. */
    val themeMode: Int = 0,
    /** 0 = colores en movimiento (COLOR_SETS), 1 = color de énfasis. */
    val backgroundMode: Int = 0,
    /** Índice en COLOR_SETS. */
    val colorSet: Int = 0,
    /** Color de énfasis en ARGB, para backgroundMode = 1. */
    val accentColor: Long = 0xFF0EA5E9,
    val animatedBackground: Boolean = true,
    /** Columnas de la cuadrícula de imágenes (se cambia pellizcando). */
    val gridColumns: Int = 4,
    /** Columnas de la cuadrícula de álbumes. */
    val albumColumns: Int = 3,
    /** Orden de los álbumes: 0 personalizado, 1 nombre, 2 número de elementos, 3 más reciente. */
    val albumSort: Int = 0,
    /** Los vídeos empiezan sin sonido. */
    val videoMuted: Boolean = false,
    /** Los vídeos vuelven a empezar al terminar. */
    val videoLoop: Boolean = false,
    /** Índice del icono de la app (ver AppIcons). */
    val appIcon: Int = 0,
    /** Ya se ofreció el permiso de gestionar multimedia (para no insistir). */
    val manageMediaOffered: Boolean = false,
)

/** Grupo de álbumes (como las carpetas de álbumes de Samsung). */
@Serializable
data class VaultItem(
    /** Negativo, para no chocar con los ids de MediaStore. */
    val id: Long,
    /** Nombre del archivo dentro de la bóveda. */
    val file: String,
    val name: String,
    val mime: String,
    val video: Boolean,
    val date: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long,
    /** Carpeta de la que salió, para devolverlo ahí. */
    val album: String,
    val albumName: String,
)

@Serializable
data class AlbumGroup(
    val id: Long,
    val name: String,
    /** Rutas relativas de los álbumes (p. ej. "DCIM/Camera/"). */
    val albums: List<String> = emptyList(),
)

/** Preferencias que no guarda Android: los álbumes se identifican por su ruta relativa. */
@Serializable
data class AlbumPrefs(
    /** Elementos archivados (por id de MediaStore): no salen en Imágenes, sí en su álbum. */
    val archived: Set<Long> = emptySet(),
    /** Orden personalizado. Los que no están van al final, del más reciente al más antiguo. */
    val order: List<String> = emptyList(),
    val groups: List<AlbumGroup> = emptyList(),
    /** Álbumes creados que aún no tienen nada: Android no conoce carpetas vacías. */
    val empty: List<String> = emptyList(),
)
