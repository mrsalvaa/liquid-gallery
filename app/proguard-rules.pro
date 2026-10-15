# kotlinx.serialization trae sus propias reglas; mantenemos los modelos por claridad.
-keep,includedescriptorclasses class dev.liquidgallery.data.**$$serializer { *; }
-keepclassmembers class dev.liquidgallery.data.** {
    *** Companion;
}
