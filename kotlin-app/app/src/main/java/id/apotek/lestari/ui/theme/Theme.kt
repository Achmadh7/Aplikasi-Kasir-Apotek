package id.apotek.lestari.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import id.apotek.lestari.R
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

// Hijau semantik & aksen Forest Emerald (Mix Opsi 1 + Opsi 3).
val ExpressiveGreen = Color(0xFF6EE7B7)
val ForestMint = Color(0xFF6EE7B7)
val ForestMintSoft = Color(0xFFA7F3D0)
val ForestMintMuted = Color(0xFFD1FAE5)
val ForestMintLight = Color(0xFFECFDF5)
val ForestEmeraldAccent = Color(0xFF10B981)
val ForestOnAccent = Color(0xFF052E16)
val ForestErrorSoft = Color(0xFFFCA5A5)
val ForestErrorDarkBg = Color(0xFF7F1D1D)
val ForestInkDark = Color(0xFF111827)
val ForestHeroLightStart = Color(0xFF142E27)
val ForestHeroLightEnd = Color(0xFF1B4338)
val ForestHeroDarkStart = Color(0xFF16332B)
val ForestHeroDarkEnd = Color(0xFF1F4D40)
val ForestLoginBgTop = Color(0xFF0D1B16)
val ForestLoginBgBottom = Color(0xFF070D0B)
val ForestStatusOnline = Color(0xFF15803D)
val ForestStatusOffline = Color(0xFFD97706)

// Status stok solid & chart accent
val StockSafeBg = Color(0xFF43A047)
val StockMediumBg = Color(0xFF0284C7)
val StockLowBg = Color(0xFFB45309)
val DonutPrimaryAccent = Color(0xFF40C4FF)

// Warna cetak/pratinjau struk (selalu kertas terang agar mudah dibaca & dibagi)
val InvoiceInkToken = Color(0xFF111111)
val InvoiceMutedToken = Color(0xFF6B7280)
val InvoiceBoxBgToken = Color(0xFFF4F4F5)
val InvoiceBlueToken = Color(0xFF0061A4)
val InvoicePaidBgToken = Color(0xFFE6F4EA)
val InvoicePaidFgToken = Color(0xFF137333)
val InvoiceDashToken = Color(0xFFD1D5DB)

// Apakah aplikasi sedang dalam mode gelap (pilihan user di pengaturan tema,
// bukan pengaturan sistem HP). Dipakai warna yang harus beda per tema.
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/** Gradasi hero Forest Emerald (Beranda, Saldo Kas, header layar). */
@Composable
fun forestHeroBrush(): Brush = Brush.linearGradient(
    colors = if (LocalIsDarkTheme.current) {
        listOf(ForestHeroDarkStart, ForestHeroDarkEnd)
    } else {
        listOf(ForestHeroLightStart, ForestHeroLightEnd)
    }
)

/** Hijau angka laba/untung — terang di mode gelap, pekat di mode terang supaya terbaca. */
@Composable
fun profitGreen(): Color = if (LocalIsDarkTheme.current) ExpressiveGreen else Color(0xFF15803D)

@Composable
fun profitBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF143528) else Color(0xFFDCFCE7)

@Composable
fun netProfitBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF163428) else Color(0xFFDFF2E4)

@Composable
fun netProfitOn(): Color = if (LocalIsDarkTheme.current) Color(0xFFF0FAF3) else Color(0xFF1B4332)

@Composable
fun netProfitLabel(): Color = if (LocalIsDarkTheme.current) Color(0xFFA0DCC0) else Color(0xFF2E7D32)

@Composable
fun infoBlue(): Color = if (LocalIsDarkTheme.current) Color(0xFF7DD3FC) else Color(0xFF0369A1)

@Composable
fun infoBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF123247) else Color(0xFFE0F2FE)

/** Amber peringatan (pengingat stok/ED & jatuh tempo PBF). */
@Composable
fun warningAmber(): Color = if (LocalIsDarkTheme.current) Color(0xFFFBBF24) else Color(0xFFD97706)

/** Teks amber kontras tinggi (WCAG AA/AAA) di atas amberBg(). */
@Composable
fun warningAmberText(): Color = if (LocalIsDarkTheme.current) Color(0xFFFBBF24) else Color(0xFF92400E)

@Composable
fun amberBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF36260A) else Color(0xFFFEF3C7)

// Skala radius seragam lintas layar (grid 4dp & nested radius)
val RadiusSmall = 8.dp
val RadiusHeader = 16.dp
val RadiusCard = 16.dp
val RadiusHero = 20.dp

// Tipografi Plus Jakarta Sans senada web-app + tabular numerals (tnum) untuk angka Rupiah
val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private val BaseTypography = Typography()
val ApotikTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = PlusJakartaSansFontFamily, fontFeatureSettings = "tnum"),
)

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
    shape: Shape = RoundedCornerShape(RadiusCard),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
}

// Palet Forest Emerald (Mix Opsi 1 + Opsi 3, senada web app Apotek Lestari).
private val SeedLightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    surfaceTint = Color(0xFF15803D),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    primaryFixed = Color(0xFFDCFCE7),
    primaryFixedDim = Color(0xFFA7F3D0),
    onPrimaryFixed = Color(0xFF052E16),
    onPrimaryFixedVariant = Color(0xFF14532D),
    secondary = Color(0xFF1B4338),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF142E27),
    secondaryFixed = Color(0xFFD1FAE5),
    secondaryFixedDim = Color(0xFFA7F3D0),
    onSecondaryFixed = Color(0xFF142E27),
    onSecondaryFixedVariant = Color(0xFF1B4338),
    tertiary = Color(0xFFD97706),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    tertiaryFixed = Color(0xFFFEF3C7),
    tertiaryFixedDim = Color(0xFFFDE68A),
    onTertiaryFixed = Color(0xFF451A03),
    onTertiaryFixedVariant = Color(0xFF92400E),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF142E27),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF142E27),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF3F4F6),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEBE8E0),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF991B1B),
    onPrimary = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF142E27),
    inverseOnSurface = Color(0xFFECFDF5),
    inversePrimary = Color(0xFF6EE7B7),
    scrim = Color(0xFF000000),
    outlineVariant = Color(0xFFE5E7EB),
    outline = Color(0xFF9CA3AF),
)

private val SeedDarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    surfaceTint = Color(0xFF10B981),
    primaryContainer = Color(0xFF1E3A30),
    onPrimaryContainer = Color(0xFF6EE7B7),
    primaryFixed = Color(0xFFDCFCE7),
    primaryFixedDim = Color(0xFF6EE7B7),
    onPrimaryFixed = Color(0xFF052E16),
    onPrimaryFixedVariant = Color(0xFF14532D),
    secondary = Color(0xFF6EE7B7),
    secondaryContainer = Color(0xFF1E3A30),
    onSecondary = Color(0xFF052E16),
    onSecondaryContainer = Color(0xFFA7F3D0),
    secondaryFixed = Color(0xFFD1FAE5),
    secondaryFixedDim = Color(0xFFA7F3D0),
    onSecondaryFixed = Color(0xFF142E27),
    onSecondaryFixedVariant = Color(0xFF1B4338),
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF36260A),
    onTertiaryContainer = Color(0xFFFDE68A),
    tertiaryFixed = Color(0xFFFEF3C7),
    tertiaryFixedDim = Color(0xFFFBBF24),
    onTertiaryFixed = Color(0xFF451A03),
    onTertiaryFixedVariant = Color(0xFF92400E),
    background = Color(0xFF0E1714),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0E1714),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF20302B),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF0A110F),
    surfaceContainerLow = Color(0xFF14201C),
    surfaceContainer = Color(0xFF17231F),
    surfaceContainerHigh = Color(0xFF20302B),
    surfaceContainerHighest = Color(0xFF283B35),
    surfaceBright = Color(0xFF2A3E38),
    surfaceDim = Color(0xFF0E1714),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF451A1A),
    onError = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFCA5A5),
    onPrimary = Color(0xFF052E16),
    inverseSurface = Color(0xFFECFDF5),
    inverseOnSurface = Color(0xFF142E27),
    inversePrimary = Color(0xFF15803D),
    scrim = Color(0xFF000000),
    outlineVariant = Color(0x1AFFFFFF),
    outline = Color(0xFF4B635B),
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
            typography = ApotikTypography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

fun formatIDR(n: Int): String = "Rp " + "%,d".format(java.util.Locale("id", "ID"), n).replace(',', '.')

// R44 (audit 2026-11-15): overload Long — total keranjang dihitung dengan
// Long supaya harga x qty tidak meluber; pemakaian Int lama tidak berubah.
fun formatIDR(n: Long): String = "Rp " + "%,d".format(java.util.Locale("id", "ID"), n).replace(',', '.')

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

// R44 (audit 2026-11-15): overload Long untuk selisih/kembalian yang
// dihitung dari total Long (mis. pilihan nominal di sheet Kasir).
fun formatCompactIDR(n: Long): String {
    if (n == 0L) return "0"
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
fun appLocale(): Locale = if (AppLang.code == "en") Locale.ENGLISH else ID_LOCALE

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
            // M7 (audit 2026-11-16): UTC untuk SEMUA pola — pola tanpa
            // "T" (tanggal saja / jam tanpa T) sebelumnya memakai zona
            // perangkat, jadi hasilnya bergeser bila zona perangkat
            // bukan WIB. Semua tanggal di aplikasi ini UTC (lihat nowIso).
            f.timeZone = TimeZone.getTimeZone("UTC")
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



