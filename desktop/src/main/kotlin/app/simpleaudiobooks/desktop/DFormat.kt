package app.simpleaudiobooks.desktop

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Zeiten, Dauern und Daten in der Sprache der Oberfläche. Monats- und Wochentagsnamen stehen hier selbst, damit
 * die Pakete ohne die Sprachdaten des JDK auskommen.
 */
object DFormat {

    private val monthsDe = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.")
    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val daysDe = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val daysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val de get() = Tr.language == "de"

    /** "1:02:03" oder "2:03". */
    fun clock(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** "5 h 3 min", "42 min", "unter 1 min". */
    fun duration(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0) + 30_000) / 60_000
        val h = minutes / 60
        val m = minutes % 60
        return when {
            minutes == 0L -> Tr.get("dur_less_than_min")
            h == 0L -> Tr.get("dur_min", m)
            m == 0L -> Tr.get("dur_h", h)
            else -> Tr.get("dur_h_min", h, m)
        }
    }

    /** "1,25×" auf Deutsch, "1.25×" auf Englisch. */
    fun speed(speed: Float): String {
        val r = Math.round(speed * 100) / 100.0
        var t = if (r == Math.floor(r)) String.format(Locale.ROOT, "%.1f", r) else String.format(Locale.ROOT, "%.2f", r).trimEnd('0')
        if (de) t = t.replace('.', ',')
        return t + "×"
    }

    fun percent(fraction: Float): String = "${(fraction * 100).toInt().coerceIn(0, 100)} %"

    fun date(millis: Long): String {
        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return date(d)
    }

    fun date(d: LocalDate): String =
        if (de) "${d.dayOfMonth}. ${monthsDe[d.monthValue - 1]} ${d.year}" else "${monthsEn[d.monthValue - 1]} ${d.dayOfMonth}, ${d.year}"

    fun dateTime(millis: Long): String {
        val z = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val time = String.format(Locale.ROOT, "%02d:%02d", z.hour, z.minute)
        return date(z.toLocalDate()) + ", " + time
    }

    fun weekday(d: LocalDate): String = (if (de) daysDe else daysEn)[d.dayOfWeek.value - 1]
}
