package id.apotek.lestari.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Material 3 Expressive — Apotek Lestari (identitas teal #00696D dipertahankan)
// - Palet lebih hidup/jenuh: primer lebih terang dan kontras dengan latar gelap.
// - Bentuk jauh lebih besar (kartu 28dp, tombol pill, chip pill).
// - Tipografi lebih besar dan tebal untuk judul & angka.
// - Gerakan pegas (spring) yang lentur untuk perubahan ukuran konten.
// ---------------------------------------------------------------------------

// Material 3 color system — seed pharmacy teal #00696D, same as web app theme.css
val TealPrimary = Color(0xFF00696D)
val TealPrimaryDark = Color(0xFF5BD6DB)

// Aksen ekspresif tambahan
val ExpressiveAmber = Color(0xFFFFD97A)
val ExpressiveAmberContainer = Color(0xFF4A3F00)
val ExpressiveGreen = Color(0xFF7EDC9A)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FE3E8),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF3E6B6A),
    secondaryContainer = Color(0xFFB8E6E4),
    onSecondaryContainer = Color(0xFF06201F),
    tertiary = Color(0xFF8A6D00),
    tertiaryContainer = Color(0xFFFFE9A8),
    onTertiaryContainer = Color(0xFF2E2300),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFEFF7F5),
    surfaceVariant = Color(0xFFDCE8E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE4F0EE),
    surfaceContainer = Color(0xFFD8E8E6),
    surfaceContainerHigh = Color(0xFFC9DDDA),
    surfaceContainerHighest = Color(0xFFB8D2CE),
    onSurface = Color(0xFF101817),
    onSurfaceVariant = Color(0xFF3F4948),
    outlineVariant = Color(0xFFBEC9C7)
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9DF1F5),
    secondary = Color(0xFF9BD3D2),
    secondaryContainer = Color(0xFF2B4A48),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = ExpressiveAmber,
    tertiaryContainer = ExpressiveAmberContainer,
    onTertiaryContainer = Color(0xFFFFECB3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0F0E),
    surface = Color(0xFF0A0F0E),
    surfaceVariant = Color(0xFF1D2725),
    surfaceContainerLowest = Color(0xFF070C0B),
    surfaceContainerLow = Color(0xFF121C1A),
    surfaceContainer = Color(0xFF1B2623),
    surfaceContainerHigh = Color(0xFF253431),
    surfaceContainerHighest = Color(0xFF31413D),
    onSurface = Color(0xFFE4EAE8),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outlineVariant = Color(0xFF3F4948)
)

// Bentuk ekspresif: sudut jauh lebih besar di semua ukuran.
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Tipografi ekspresif: judul & angka lebih besar dan lebih tebal.
val ExpressiveTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
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

// ---- Frosted glass (kaca buram) + Material 3 Expressive ----
// Panel kaca: permukaan tembus pandang + garis tepi tipis terang + bayangan lembut.
// Warna mengikuti colorScheme (onSurface/primary) supaya benar di mode gelap/terang.
// Bentuk besar & pegas ekspresif tetap dipertahankan; dua kartu aksen teal solid
// dibiarkan sebagai penegas (teks gelap di atasnya harus tetap terbaca).
@Composable
fun glassPanelColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
@Composable
fun glassPanelStrongColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
@Composable
fun glassBorderColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
@Composable
fun glassTealColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
@Composable
fun glassTealBorderColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

/** Kartu kaca: panel tembus pandang + garis tepi + bayangan lembut, sudut ekspresif 28dp. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = glassPanelColor(),
    borderColor: Color = glassBorderColor(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

/** Latar gradasi lembut (semburat teal di atas) supaya panel kaca punya latar untuk dipantulkan. */
@Composable
fun glassBackgroundBrush(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.background
    )
)

// Tampilan mengikuti web app (mode gelap) apa pun pengaturan sistem HP.
@Composable
fun ApotikLestariTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content
    )
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
