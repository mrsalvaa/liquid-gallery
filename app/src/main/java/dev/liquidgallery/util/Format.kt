package dev.liquidgallery.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale.forLanguageTag("es-ES")
private val dayThisYear = DateTimeFormatter.ofPattern("EEE, d MMM", es)
private val dayOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", es)
private val monthFmt = DateTimeFormatter.ofPattern("LLLL 'de' yyyy", es)
private val fullFmt = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", es)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", es)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yyyy", es)

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun String.capitalized() = replaceFirstChar { it.titlecase(es) }

/** Cabecera de un día: "Hoy", "Ayer", "Lun, 13 oct" o "Lun, 13 oct 2024". */
fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> (if (day.year == today.year) dayThisYear else dayOtherYear).format(day).replace(".", "").capitalized()
}

/** Cabecera de un mes: "Octubre de 2026". */
fun monthLabel(day: LocalDate): String = monthFmt.format(day).capitalized()

/** "Miércoles, 15 de octubre de 2026". */
fun fullDate(ms: Long): String = fullFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())).capitalized()

fun timeOf(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun shortDate(ms: Long): String = shortFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())).replace(".", "")

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
    return if (i == 0) "$bytes B" else String.format(es, "%.1f %s", v, units[i])
}

/** "1.234 elementos". */
fun countLabel(n: Int, one: String = "elemento", many: String = "elementos"): String =
    "${String.format(es, "%,d", n)} ${if (n == 1) one else many}"
