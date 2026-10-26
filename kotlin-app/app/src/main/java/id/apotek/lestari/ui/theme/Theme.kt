package id.apotek.lestari.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Material 3 color system — seed pharmacy teal #00696D, same as web app theme.css
val TealPrimary = Color(0xFF00696D)
val TealPrimaryDark = Color(0xFF7ED3D8)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF1F5),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF06201F),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFF5FAF9),
    onSurface = Color(0xFF171D1C),
    onSurfaceVariant = Color(0xFF3F4948),
    outlineVariant = Color(0xFFBEC9C7)
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9DF1F5),
    secondary = Color(0xFFB0CCCB),
    secondaryContainer = Color(0xFF334B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFFFD97A),
    tertiaryContainer = Color(0xFF4A3F00),
    onTertiaryContainer = Color(0xFFFFECB3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    surface = Color(0xFF0E1514),
    surfaceVariant = Color(0xFF1D2725),
    surfaceContainerLowest = Color(0xFF0B1210),
    surfaceContainerLow = Color(0xFF141D1B),
    surfaceContainer = Color(0xFF1D2725),
    surfaceContainerHigh = Color(0xFF232F2C),
    surfaceContainerHighest = Color(0xFF2E3A37),
    onSurface = Color(0xFFDEE4E2),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outlineVariant = Color(0xFF3F4948)
)

// Tampilan mengikuti web app (mode gelap) apa pun pengaturan sistem HP.
@Composable
fun ApotikLestariTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

fun formatIDR(n: Int): String = "Rp " + "%,d".format(java.util.Locale("id", "ID"), n).replace(',', '.')

// Stock status rule (same as web): >20 Aman, ==20 Sedang, <20 Rendah, 0 Habis
fun stockLabel(stock: Int): String = when {
    stock <= 0 -> "Habis"
    stock < 20 -> "Rendah"
    stock == 20 -> "Sedang"
    else -> "Aman"
}

// ---- Format tanggal Indonesia (Asia/Jakarta), seperti web app ----

private val JAKARTA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
private val ID_LOCALE: Locale = Locale("id", "ID")

/** "Minggu, 25 Oktober 2026" — tanggal hari ini. */
fun todayLabelID(): String =
    SimpleDateFormat("EEEE, d MMMM yyyy", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(Date())

/** Parse tanggal ISO dari server ("2026-10-25T23:53:00.000Z" atau "2026-10-25"). */
fun parseISODate(s: String?): Date? {
    if (s.isNullOrBlank()) return null
    val value = s.trim()
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        try {
            val f = SimpleDateFormat(p, Locale.US)
            if (p.contains("T")) f.timeZone = TimeZone.getTimeZone("UTC")
            f.isLenient = false
            return f.parse(value)
        } catch (_: Exception) {
        }
    }
    return null
}

/** "25 Okt, 23.53" — seperti web app. */
fun formatDateTimeID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("d MMM, HH.mm", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "25 Okt 2030" — tanggal kadaluarsa. */
fun formatDateID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("d MMM yyyy", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** Kunci "yyyy-MM-dd" sebuah tanggal di zona Asia/Jakarta. */
fun dayKeyID(d: Date): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = JAKARTA_TZ }.format(d)

/** Kunci "yyyy-MM" sebuah tanggal di zona Asia/Jakarta. */
fun monthKeyID(d: Date): String =
    SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = JAKARTA_TZ }.format(d)

/** Jumlah hari sampai tanggal kadaluarsa (negatif = sudah lewat). Null jika tanggal kosong/tidak valid. */
fun daysUntilExpiry(expiry: String?): Int? {
    val d = parseISODate(expiry) ?: return null
    val today = Calendar.getInstance(JAKARTA_TZ).apply {
        time = Date()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val exp = Calendar.getInstance(JAKARTA_TZ).apply {
        time = d
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return ((exp.timeInMillis - today.timeInMillis) / 86400000L).toInt()
}

/** "40,9" — margin persen dengan koma desimal Indonesia. */
fun marginTextID(pct: Double): String = String.format(ID_LOCALE, "%,.1f", pct)
