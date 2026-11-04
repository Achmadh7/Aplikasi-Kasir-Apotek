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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// Hijau semantik untuk angka laba (tidak ada peran hijau di colorScheme).
val ExpressiveGreen = Color(0xFF7EDC9A)

// Apakah aplikasi sedang dalam mode gelap (pilihan user di pengaturan tema,
// bukan pengaturan sistem HP). Dipakai warna yang harus beda per tema.
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Hijau angka laba/untung — terang di mode gelap, pekat di mode terang supaya terbaca. */
@Composable
fun profitGreen(): Color = if (LocalIsDarkTheme.current) ExpressiveGreen else Color(0xFF2E7D32)

/** Amber peringatan (label hitung mundur jatuh tempo PBF "N hari lagi" /
 *  "Besok jatuh tempo") — mode gelap #FFB74D (amber muda, jelas di latar
 *  gelap); mode terang #8C5700 (amber pekat) karena #FFB74D nyaris tidak
 *  terbaca di kartu terang (laporan Achmad 2026-10-31). Nilai terang lama
 *  #B26A00 kontrasnya hanya 3,5-4,2:1 di permukaan kartu terang (di bawah
 *  batas keterbacaan 4,5:1), jadi digelapkan lagi 2026-11-04 menjadi
 *  #8C5700 (4,9-6,0:1 di semua permukaan terang). */
@Composable
fun warningAmber(): Color = if (LocalIsDarkTheme.current) Color(0xFFFFB74D) else Color(0xFF8C5700)

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
// Fidelity — dipilih Achmad 2026-11-01 dari gambar pratinjau palet
// (gaya ini setia ke warna biji: wadah primer pekat kebiruan; keluarga
// tertiary-nya kecokelatan bawaan algoritma Fidelity). Nilai di bawah ini keluaran
// generator resminya (bukan racikan tangan); pustaka generator tidak
// dipasang sebagai dependensi karena versi terkininya menuntut
// Kotlin/Compose lebih baru daripada tumpukan aplikasi ini.
private val SeedLightColors = lightColorScheme(
    primary = Color(0xFF00497D),
    surfaceTint = Color(0xFF0061A4),
    primaryContainer = Color(0xFF0061A4),
    onPrimaryContainer = Color(0xFFC0DBFF),
    primaryFixed = Color(0xFFD1E4FF),
    primaryFixedDim = Color(0xFF9FCAFF),
    onPrimaryFixed = Color(0xFF001D36),
    onPrimaryFixedVariant = Color(0xFF00497D),
    secondary = Color(0xFF4A607C),
    secondaryContainer = Color(0xFFC8DFFF),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF4C627E),
    secondaryFixed = Color(0xFFD1E4FF),
    secondaryFixedDim = Color(0xFFB1C8E8),
    onSecondaryFixed = Color(0xFF021D35),
    onSecondaryFixedVariant = Color(0xFF324863),
    tertiary = Color(0xFF713700),
    tertiaryContainer = Color(0xFF944A00),
    onTertiaryContainer = Color(0xFFFFCEAF),
    tertiaryFixed = Color(0xFFFFDCC6),
    tertiaryFixedDim = Color(0xFFFFB784),
    onTertiaryFixed = Color(0xFF301400),
    onTertiaryFixedVariant = Color(0xFF713700),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDDE3EE),
    onSurfaceVariant = Color(0xFF414750),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceDim = Color(0xFFD8DAE0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF93000A),
    onPrimary = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    inversePrimary = Color(0xFF9FCAFF),
    scrim = Color(0xFF000000),
    outlineVariant = Color(0xFFC1C7D2),
    outline = Color(0xFF717782),
)

private val SeedDarkColors = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    surfaceTint = Color(0xFF9FCAFF),
    primaryContainer = Color(0xFF0061A4),
    onPrimaryContainer = Color(0xFFC0DBFF),
    primaryFixed = Color(0xFFD1E4FF),
    primaryFixedDim = Color(0xFF9FCAFF),
    onPrimaryFixed = Color(0xFF001D36),
    onPrimaryFixedVariant = Color(0xFF00497D),
    secondary = Color(0xFFB1C8E8),
    secondaryContainer = Color(0xFF324863),
    onSecondary = Color(0xFF1A324B),
    onSecondaryContainer = Color(0xFFA0B7D6),
    secondaryFixed = Color(0xFFD1E4FF),
    secondaryFixedDim = Color(0xFFB1C8E8),
    onSecondaryFixed = Color(0xFF021D35),
    onSecondaryFixedVariant = Color(0xFF324863),
    tertiary = Color(0xFFFFB784),
    tertiaryContainer = Color(0xFF944A00),
    onTertiaryContainer = Color(0xFFFFCEAF),
    tertiaryFixed = Color(0xFFFFDCC6),
    tertiaryFixedDim = Color(0xFFFFB784),
    onTertiaryFixed = Color(0xFF301400),
    onTertiaryFixedVariant = Color(0xFF713700),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF414750),
    onSurfaceVariant = Color(0xFFC1C7D2),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    surfaceBright = Color(0xFF36393E),
    surfaceDim = Color(0xFF111418),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    onPrimary = Color(0xFF003258),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF0061A4),
    scrim = Color(0xFF000000),
    outlineVariant = Color(0xFF414750),
    outline = Color(0xFF8B919C),
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

// ---- Bahasa aplikasi (Indonesia / Inggris) ----
// Pilihan bahasa dipilih dari sheet Lainnya (di bawah Mode Gelap/Terang),
// tersimpan di SharedPreferences "apotek_prefs" kunci "app_language", dan
// MainActivity menyalinnya ke AppLang.code saat aplikasi berjalan. Semua
// teks tampilan mengambil string lewat tr() supaya ikut berubah seketika
// saat bahasa diganti; format tanggal memakai appLocale().
object AppLang {
    var code by mutableStateOf("id")
}

/** Ambil string sesuai bahasa aktif: tr("Simpan", "Save"). */
fun tr(id: String, en: String): String = if (AppLang.code == "en") en else id

/** Locale format tanggal sesuai bahasa aktif. */
private fun appLocale(): Locale = if (AppLang.code == "en") Locale.ENGLISH else ID_LOCALE

// ---- Format tanggal Indonesia (Asia/Jakarta), seperti web app ----

private val JAKARTA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
private val ID_LOCALE: Locale = Locale("id", "ID")

/** "Minggu, 25 Oktober 2026" — tanggal hari ini. */
fun todayLabelID(): String =
    SimpleDateFormat("EEEE, d MMMM yyyy", appLocale()).apply { timeZone = JAKARTA_TZ }.format(Date())

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
    return SimpleDateFormat("d MMM, HH.mm", appLocale()).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "14.30" — jam saja (untuk baris Transaksi: semua transaksi di kartu ini hari ini). */
fun formatTimeID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("HH.mm", ID_LOCALE).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "25 Okt 2030" — tanggal kadaluarsa. */
fun formatDateID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("d MMM yyyy", appLocale()).apply { timeZone = JAKARTA_TZ }.format(d)
}

/** "Minggu, 25 Oktober 2026" — tanggal lengkap untuk invoice. */
fun formatFullDateID(s: String?): String {
    val d = parseISODate(s) ?: return s ?: "-"
    return SimpleDateFormat("EEEE, d MMMM yyyy", appLocale()).apply { timeZone = JAKARTA_TZ }.format(d)
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
fun marginTextID(pct: Double): String = String.format(appLocale(), "%,.1f", pct)



