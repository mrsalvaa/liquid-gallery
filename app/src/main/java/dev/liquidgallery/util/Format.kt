package dev.liquidgallery.util

import android.text.format.DateFormat
import dev.liquidgallery.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatos de fecha del idioma del teléfono; se rehacen si cambia. */
private class Formats(val locale: Locale) {
    private fun best(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    val dayThisYear = best("EEEdMMM")
    val dayOtherYear = best("EEEdMMMyyyy")
    val month = best("LLLLyyyy")
    val full = best("EEEEdMMMMyyyy")
    val time = best("Hm")
    val short = best("dMMMyyyy")
}

private var formats = Formats(Locale.getDefault())

private fun formats(): Formats {
    val locale = Locale.getDefault()
    if (formats.locale != locale) formats = Formats(locale)
    return formats
}

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun String.capitalized() = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** Cabecera de un día: "Hoy", "Ayer", "Lun, 13 oct" o "Lun, 13 oct 2024". */
fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> str(R.string.today)
    today.minusDays(1) -> str(R.string.yesterday)
    else -> formats().let { if (day.year == today.year) it.dayThisYear else it.dayOtherYear }.format(day).replace(".", "").capitalized()
}

/** Cabecera de un mes: "Octubre de 2026". */
fun monthLabel(day: LocalDate): String = formats().month.format(day).capitalized()

/** "Miércoles, 15 de octubre de 2026". */
fun fullDate(ms: Long): String = formats().full.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())).capitalized()

fun timeOf(ms: Long): String = formats().time.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun shortDate(ms: Long): String = formats().short.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())).replace(".", "")

/** "0:07", "12:34" o "1:02:03". */
fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "2,4 MB". */
fun fileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", v, units[i])
}

/** "1.234 elementos". */
fun countLabel(n: Int, id: Int = R.plurals.items): String =
    plural(id, n, String.format(Locale.getDefault(), "%,d", n))
