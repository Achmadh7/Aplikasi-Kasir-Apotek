package id.apotek.lestari.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Material 3 Expressive RESMI Google (Material3 1.5.0-alpha08). Sejak
// 2026-11-01 (permintaan Achmad) aplikasi memakai bawaan resmi sepenuhnya:
// tipografi dan bentuk bawaan MaterialExpressiveTheme, MotionScheme
// expressive, dan skema warna yang digenerate algoritma resmi Google
// (Material Color Utilities) dari warna biji — lihat SeedLightColors.
// Palet biru medis, tipografi Poppins, dan bentuk kustom sebelumnya sudah
// dihapus; warna di semua layar memakai peran colorScheme.
// ---------------------------------------------------------------------------

// Hijau & amber semantik (angka laba dan peringatan) yang DIHARMONISASI ke
// warna primer skema memakai Blend.harmonize — cara resmi Material Color
// Utilities menambahkan warna kustom ke dalam skema warna (pilihan Achmad
// 2026-11-01, supaya warna semantik tidak nabrak dengan skema): rona kedua
// warna ditarik mendekati biru biji #0061A4 sehingga satu keluarga dengan
// seluruh skema. Nilai di bawah adalah keluaran harmonisasi resminya dari
// warna dasar yang sebelumnya dipakai mentah.
private val ProfitGreenLight = Color(0xFF007E4E) // harmonisasi #2E7D32
private val ProfitGreenDark = Color(0xFF64DDB3) // harmonisasi #7EDC9A
private val WarningAmberLight = Color(0xFFBB6324) // harmonisasi #B26A00
private val WarningAmberDark = Color(0xFFFFB578) // harmonisasi #FFB74D

// Apakah aplikasi sedang dalam mode gelap (pilihan user di pengaturan tema,
// bukan pengaturan sistem HP). Dipakai warna yang harus beda per tema.
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Hijau angka laba/untung — versi harmonis skema (lihat konstanta di atas):
 *  pekat di mode terang, terang di mode gelap supaya terbaca. */
@Composable
fun profitGreen(): Color = if (LocalIsDarkTheme.current) ProfitGreenDark else ProfitGreenLight

/** Amber peringatan (label hitung mundur jatuh tempo PBF "N hari lagi" /
 *  "Besok jatuh tempo") — mode gelap #FFB74D (amber muda, jelas di latar
 *  gelap); mode terang #B26A00 (amber pekat) karena #FFB74D nyaris tidak
 *  terbaca di kartu terang (laporan Achmad 2026-10-31). */
@Composable
fun warningAmber(): Color = if (LocalIsDarkTheme.current) WarningAmberDark else WarningAmberLight

/** Pegas ekspresif: lentur (dampingRatio ~0.7) untuk animateContentSize & transisi. */
fun <T> expressiveSpring() = spring<T>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow
)

// ---- Token desain: satu sistem kartu & jarak (revamp Achmad 2026-10-31) ----
// Semua kartu konten memakai ExpressiveCard berbentuk shapes.extraLarge
// resmi dari tema; padding isi kartu standar 16dp; jarak antar kartu dalam
// daftar layar CardGap (Beranda memakai 20dp — pilihan Achmad dari
// screenshot, pengecualian sadar).
val CardGap = 16.dp

// Skala spasi internal resmi: XS 4, S 8, M 12, L 16, XL 24 (dp). Spasi
// vertikal layar & isi kartu memakai tangga ini; nilai lama di luar skala
// (2/6/10/20) diselaraskan ke anak tangga terdekat.
val SpaceXS = 4.dp
val SpaceS = 8.dp
val SpaceM = 12.dp
val SpaceL = 16.dp
val SpaceXL = 24.dp

// ---- Material 3 Expressive penuh (tanpa kaca) ----
// Kartu memakai permukaan tonal solid (surfaceContainer) dari colorScheme,
// tanpa tembus pandang dan tanpa garis tepi — sesuai Material 3 Expressive.
// Bentuk bawaan: shapes.extraLarge resmi dari tema.
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
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
// Skema warna RESMI hasil generate algoritma Material Color Utilities
// (spesifikasi warna 2025) dari warna biji biru medis #0061A4, gaya
// Tonal Spot — diganti dari gaya Expressive pada 2026-11-01 atas
// permintaan Achmad karena gaya Expressive terasa terlalu jenuh dan
// warnanya saling nabrak; Tonal Spot adalah gaya standar Material You
// yang jauh lebih kalem dengan biji yang sama. Nilai di bawah ini keluaran
// generator resminya (bukan racikan tangan); pustaka generator tidak
// dipasang sebagai dependensi karena versi terkininya menuntut
// Kotlin/Compose lebih baru daripada tumpukan aplikasi ini.
private val SeedLightColors = lightColorScheme(
    primary = Color(0xFF3F6188),
    onPrimary = Color(0xFFF7F9FF),
    primaryContainer = Color(0xFFACCFFC),
    onPrimaryContainer = Color(0xFF21466B),
    inversePrimary = Color(0xFFACCFFC),
    secondary = Color(0xFF546071),
    onSecondary = Color(0xFFF7F9FF),
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF465363),
    tertiary = Color(0xFF615A84),
    onTertiary = Color(0xFFFCF7FF),
    tertiaryContainer = Color(0xFFD5CBFC),
    onTertiaryContainer = Color(0xFF49426A),
    background = Color(0xFFF9F9FE),
    onBackground = Color(0xFF2E333A),
    surface = Color(0xFFF9F9FE),
    onSurface = Color(0xFF2E333A),
    surfaceVariant = Color(0xFFDEE2EC),
    onSurfaceVariant = Color(0xFF5A5F67),
    surfaceTint = Color(0xFF3F6188),
    inverseSurface = Color(0xFF0C0E12),
    inverseOnSurface = Color(0xFF9B9DA1),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    outline = Color(0xFF767B83),
    outlineVariant = Color(0xFFAEB2BB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9F9FE),
    surfaceDim = Color(0xFFD6DAE3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFEBEEF5),
    surfaceContainerHigh = Color(0xFFE5E8F0),
    surfaceContainerHighest = Color(0xFFDEE2EC),
    primaryFixed = Color(0xFFACCFFC),
    primaryFixedDim = Color(0xFF9EC1ED),
    onPrimaryFixed = Color(0xFF053256),
    onPrimaryFixedVariant = Color(0xFF2B4F75),
    secondaryFixed = Color(0xFFD7E3F8),
    secondaryFixedDim = Color(0xFFC9D5E9),
    onSecondaryFixed = Color(0xFF344050),
    onSecondaryFixedVariant = Color(0xFF505C6D),
    tertiaryFixed = Color(0xFFD5CBFC),
    tertiaryFixedDim = Color(0xFFC7BEEE),
    onTertiaryFixed = Color(0xFF352F56),
    onTertiaryFixedVariant = Color(0xFF524B74),
)

private val SeedDarkColors = darkColorScheme(
    primary = Color(0xFFAFC9EB),
    onPrimary = Color(0xFF28425E),
    primaryContainer = Color(0xFF3B5472),
    onPrimaryContainer = Color(0xFFD3E5FF),
    inversePrimary = Color(0xFF48617F),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF354151),
    secondaryContainer = Color(0xFF303C4C),
    onSecondaryContainer = Color(0xFFB3C0D4),
    tertiary = Color(0xFFE3DBFF),
    onTertiary = Color(0xFF524B74),
    tertiaryContainer = Color(0xFFD5CBFC),
    onTertiaryContainer = Color(0xFF49426A),
    background = Color(0xFF0C0E12),
    onBackground = Color(0xFFE1E5EF),
    surface = Color(0xFF0C0E12),
    onSurface = Color(0xFFE1E5EF),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFFA7ABB4),
    surfaceTint = Color(0xFFAFC9EB),
    inverseSurface = Color(0xFFF9F9FE),
    inverseOnSurface = Color(0xFF535559),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF71767E),
    outlineVariant = Color(0xFF434850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF272C33),
    surfaceDim = Color(0xFF0C0E12),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF101418),
    surfaceContainer = Color(0xFF161A1F),
    surfaceContainerHigh = Color(0xFF1C2026),
    surfaceContainerHighest = Color(0xFF21262D),
    primaryFixed = Color(0xFFBFD9FD),
    primaryFixedDim = Color(0xFFB2CBEE),
    onPrimaryFixed = Color(0xFF1F3A56),
    onPrimaryFixedVariant = Color(0xFF3D5674),
    secondaryFixed = Color(0xFFD7E3F8),
    secondaryFixedDim = Color(0xFFC9D5E9),
    onSecondaryFixed = Color(0xFF344050),
    onSecondaryFixedVariant = Color(0xFF505C6D),
    tertiaryFixed = Color(0xFFD5CBFC),
    tertiaryFixedDim = Color(0xFFC7BEEE),
    onTertiaryFixed = Color(0xFF352F56),
    onTertiaryFixedVariant = Color(0xFF524B74),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApotikLestariTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        // Tema Expressive RESMI Google (Material3 1.5): MaterialExpressiveTheme
        // + MotionScheme.expressive() — skema gerak pegas resmi berlaku ke
        // seluruh komponen. Warna memakai skema hasil generate algoritma
        // resmi dari warna biji (SeedLightColors/SeedDarkColors).
        // Tipografi & bentuk TIDAK diisi supaya memakai bawaan resmi
        // MaterialExpressiveTheme.
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) SeedDarkColors else SeedLightColors,
            motionScheme = MotionScheme.expressive(),
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



