# Liquid Gallery

[English](README.md) | **Español**

Se instala en el móvil como **Galería** (o *Gallery*, según el idioma).

Galería para Android libre y sin rastreo, inspirada en la de Samsung y con la misma interfaz de
cristal líquido que [Liquid Clock](../clock): superficies que refractan lo que tienen detrás
(gracias a [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)) y botones que se deforman como
una gota al pulsarlos.

**No pide permiso de internet**, no tiene anuncios ni telemetría. Publicada bajo GPL-3.0.

## Funciones

**Imágenes**: cuadrícula agrupada por días (o por meses con 6 u 8 columnas). Pellizca para cambiar
el número de columnas. Mantén pulsado y arrastra para seleccionar varias; toca la fecha para
seleccionar el día entero. Barra lateral para desplazarte rápido por fechas.

**Visor**: desliza entre fotos, zoom con dos dedos o doble toque (carga por teselas, así que no se
queda sin memoria con fotos enormes), vídeos con controles propios, GIF animados. Desliza hacia
abajo para cerrar y hacia arriba para ver los detalles (cámara, ISO, apertura, tamaño...).

**Álbumes**: Cámara siempre primero, Favoritos y Vídeos, crear álbumes, mover y copiar entre ellos,
ocultarlos, agruparlos en grupos de álbumes y ordenarlos a mano, por nombre, por cantidad o por fecha.

**Papelera y favoritos**: son los de Android, así que se comparten con otras galerías. La papelera
borra sola lo que lleva 30 días.

**Búsqueda sin conexión**: por nombre, álbum, fecha («octubre», «2024», «ayer») o tipo («vídeo»,
«captura», «gif», «favorito»).

**Editor**: recortar con proporciones (libre, original, 1:1, 4:3, 16:9...), girar, voltear,
brillo, contraste, saturación, calidez y filtros. Mantén pulsada la foto para compararla con la
original. Se guarda como copia o sobrescribiendo la original.

**Abrir desde otras apps**: también funciona como visor de fotos y vídeos para otras apps.

## Personalización

En **Ajustes**: tema (sistema, claro u oscuro), fondo en movimiento o color de énfasis (los mismos
de Liquid Clock), columnas de la cuadrícula y comportamiento de los vídeos (empezar sin sonido,
repetir).

## Permisos

- **Fotos y vídeos**: para mostrarlos. En Android 14 puedes darle acceso solo a algunas.
- **Gestionar contenido multimedia** (opcional): sin él, Android pide confirmación cada vez que
  borras, mueves o marcas un favorito. Se activa desde **Más** o **Ajustes**.

## Compilar

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleRelease
```

La clave de firma de release se lee de `~/.android-keys/liquid-gallery.properties` (o de
`-PsigningProps=...` / `LIQUID_GALLERY_SIGNING`); si no existe, se firma con la de debug.

## Librerías

Todas libres: Jetpack Compose, [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass),
[Coil](https://github.com/coil-kt/coil), [Telephoto](https://github.com/saket/telephoto),
[Media3 ExoPlayer](https://github.com/androidx/media) y ExifInterface.
