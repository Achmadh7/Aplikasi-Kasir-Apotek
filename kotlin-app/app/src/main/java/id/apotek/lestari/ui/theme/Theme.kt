package id.apotek.lestari.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import id.apotek.lestari.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Material 3 Expressive — Apotek Lestari (identitas biru medis #0061A4)
// - Palet lebih hidup/jenuh: primer lebih terang dan kontras dengan latar gelap.
// - Bentuk jauh lebih besar (kartu 28dp, tombol pill, chip pill).
// - Tipografi lebih besar dan tebal untuk judul & angka.
// - Gerakan pegas (spring) yang lentur untuk perubahan ukuran konten.
// ---------------------------------------------------------------------------

// Material 3 color system — seed biru medis #0061A4 (web app tetap teal)
val BluePrimary = Color(0xFF0061A4)
val BluePrimaryDark = Color(0xFFA1C9FF)

// Aksen ekspresif tambahan
val ExpressiveAmber = Color(0xFFFFD97A)
val ExpressiveAmberContainer = Color(0xFF4A3F00)
val ExpressiveGreen = Color(0xFF7EDC9A)

// Apakah aplikasi sedang dalam mode gelap (pilihan user di pengaturan tema,
// bukan pengaturan sistem HP). Dipakai warna yang harus beda per tema.
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Hijau angka laba/untung — terang di mode gelap, pekat di mode terang supaya terbaca. */
@Composable
fun profitGreen(): Color = if (LocalIsDarkTheme.current) ExpressiveGreen else Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF8A6D00),
    tertiaryContainer = Color(0xFFFFE9A8),
    onTertiaryContainer = Color(0xFF2E2300),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFF7F9FF),
    surfaceVariant = Color(0xFFDEE3EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF1F8),
    surfaceContainer = Color(0xFFE2E8F0),
    surfaceContainerHigh = Color(0xFFD6DEE8),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    onSurface = Color(0xFF111418),
    onSurfaceVariant = Color(0xFF43474E),
    outlineVariant = Color(0xFFC3C7CF)
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF00497C),
    onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = ExpressiveAmber,
    tertiaryContainer = ExpressiveAmberContainer,
    onTertiaryContainer = Color(0xFFFFECB3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0E14),
    surface = Color(0xFF0A0E14),
    surfaceVariant = Color(0xFF1D242E),
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Color(0xFF12161D),
    surfaceContainer = Color(0xFF1B2027),
    surfaceContainerHigh = Color(0xFF262D36),
    surfaceContainerHighest = Color(0xFF313A45),
    onSurface = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFFBCC3CF),
    outlineVariant = Color(0xFF43474E)
)

// Bentuk ekspresif: sudut jauh lebih besar di semua ukuran.
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Font Poppins (OFL, gratis) untuk judul layar — file .ttf diunduh saat build
// (lihat downloadPoppinsFonts di build.gradle.kts) ke build/generated/res/font.
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

// Tipografi ekspresif: judul & angka lebih besar dan lebih tebal.
val ExpressiveTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
)

/** Pegas ekspresif: lentur (dampingRatio ~0.7) untuk animateContentSize & transisi. */
fun <T> expressiveSpring() = spring<T>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow
)

// ---- Material 3 Expressive penuh (tanpa kaca) ----
// Kartu memakai permukaan tonal solid (surfaceContainer) dari colorScheme,
// tanpa tembus pandang dan tanpa garis tepi — sesuai Material 3 Expressive.
// Bentuk besar (28dp) & pegas ekspresif dipertahankan; dua kartu aksen biru
// solid dibiarkan sebagai penegas (teks gelap di atasnya harus tetap terbaca).
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

// Tampilan mengikuti web app (mode gelap) apa pun pengaturan sistem HP.
@Composable
fun ApotikLestariTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content
        )
    }
}

fun formatIDR(n: Int): String = "Rp " + "%,d".format(java.util.Locale("id", "ID"), n).replace(',', '.')

/** "100000" -> "100.000" — pemisah ribuan (titik) untuk input nominal. */
fun formatDecimal(digits: String): String {
    val d = digits.filter { it.isDigit() }
    if (d.isEmpty()) return ""
    val clean = d.trimStart('0').ifEmpty { "0" }
    return clean.reversed().chunked(3).joinToString(".").reversed()
}

/**
 * Format nominal uang (pemisah ribuan) sambil menjaga posisi kursor tetap benar.
 * Dipakai di onValueChange input uang (Harga Beli/Jual, Jumlah kas).
 * Kalau hanya pakai formatDecimal() pada String biasa, kursor meloncat
 * (ke akhir / ke tengah angka) setiap kali "." ditambah atau dihapus,
 * sehingga sulit mengedit angka di tengah.
 */
fun formatDecimalFieldValue(value: TextFieldValue): TextFieldValue {
    val digits = value.text.filter { it.isDigit() }
    if (digits.isEmpty()) return TextFieldValue("", TextRange(0))
    val formatted = formatDecimal(digits)
    val digitsBeforeCursor = value.text.take(value.selection.start).count { it.isDigit() }
    var cursor = formatted.length
    var seen = 0
    for (i in formatted.indices) {
        if (seen >= digitsBeforeCursor) { cursor = i; break }
        if (formatted[i].isDigit()) seen++
    }
    return TextFieldValue(formatted, TextRange(cursor.coerceIn(0, formatted.length)))
}

/** "100.000" -> 100000 — buang pemisah ribuan sebelum diubah ke angka. */
fun parseDecimal(formatted: String): Int {
    val digits = formatted.filter { it.isDigit() }
    if (digits.isEmpty()) return 0
    // Parse sebagai Long lalu clamp ke rentang Int: angka yang overflow Int
    // tidak lagi diam-diam jadi 0, melainkan dibatasi ke Int.MAX_VALUE.
    val v = digits.toLongOrNull() ?: return Int.MAX_VALUE
    return v.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/** "150rb" / "2,5jt" — nilai ringkas untuk label diagram. */
fun formatCompactIDR(n: Int): String {
    if (n == 0) return "0"
    val abs = kotlin.math.abs(n)
    val sign = if (n < 0) "-" else ""
    if (abs < 1000) return "$sign$abs"
    val (v, suffix) = if (abs < 1_000_000) abs / 1000.0 to "rb" else abs / 1_000_000.0 to "jt"
    val s = String.format(java.util.Locale("id", "ID"), "%.1f", v).trimEnd('0').trimEnd(',')
    return "$sign$s$suffix"
}

// Aturan status stok — skala tetap (keputusan Achmad 2026-10-29, menggantikan
// stok minimum): 0 Habis, 1-9 Rendah, 10-15 Sedang, 16+ Aman. Fungsi ini
// dipakai bersama oleh Stok, Kasir, Beranda, dan Pengingat supaya konsisten.
fun stockLabel(stock: Int): String = when {
    stock <= 0 -> "Habis"
    stock < 10 -> "Rendah"
    stock <= 15 -> "Sedang"
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

/** "14.30" — jam saja (untuk baris Transaksi: semua transaksi di kartu ini hari ini). */
fun formatTimeID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("HH.mm", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "25 Okt 2030" — tanggal kadaluarsa. */
fun formatDateID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("d MMM yyyy", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "Minggu, 25 Oktober 2026" — tanggal lengkap untuk invoice. */
fun formatFullDateID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("EEEE, d MMMM yyyy", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
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



