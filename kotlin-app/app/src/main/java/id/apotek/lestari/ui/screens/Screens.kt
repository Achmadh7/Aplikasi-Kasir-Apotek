@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.theme.*
import java.util.Date

// Berkas bersama ui.screens: konstanta, helper, dan komponen kecil lintas layar.

val repo = Repository()


val CATEGORIES = listOf("Umum","Obat Bebas","Obat Bebas Terbatas","Obat Keras","Antibiotik","Vitamin","Pereda Nyeri","Obat Batuk & Flu","Pencernaan","Kulit","Mata","Lainnya")


val UNITS = listOf("Strip","Botol","Vial","Ampul","Box","Tablet","Kapsul","Tube","Drop","Sachet","Pcs")


// Kelas lebar jendela (HP/tablet, portrait/landscape) untuk layout adaptif.
// Compact < 600dp, Medium 600-840dp, Expanded > 840dp. Nilai disediakan dari
// MainActivity lewat CompositionLocal agar tidak mengubah signature screen.
val LocalWindowWidthClass = compositionLocalOf { WindowWidthSizeClass.Compact }


/** Jumlah kolom grid adaptif menurut kelas lebar jendela. */
@Composable
fun adaptiveColumns(compact: Int, medium: Int, expanded: Int): Int = when (LocalWindowWidthClass.current) {
    WindowWidthSizeClass.Medium -> medium
    WindowWidthSizeClass.Expanded -> expanded
    else -> compact
}


/** Lebar maksimal bottom sheet: selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet. */
@Composable
fun adaptiveSheetMaxWidth() = when {
    // Tablet: layar lebar (Expanded) atau tablet potret (Medium + potret).
    LocalWindowWidthClass.current == WindowWidthSizeClass.Expanded -> 640.dp
    LocalWindowWidthClass.current == WindowWidthSizeClass.Medium &&
        LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE -> 640.dp
    // HP potret: sheet selebar layar (640dp = batas default Material 3,
    // efektif selebar layar di HP) — 360dp terlalu ramping di HP yang
    // layarnya lebih lebar dari 360dp.
    LocalWindowWidthClass.current == WindowWidthSizeClass.Compact &&
        LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE -> 640.dp
    // HP lanskap: tetap 360dp supaya tidak terlalu lebar.
    else -> 360.dp
}

// ---------- Header nama layar N2 solid gelap premium (pilihan Achmad
// 2026-11-10, menggantikan K3 gradasi) — MODE GELAP tetap: batang navy
// nyaris hitam (satu keluarga dengan gerbang masuk M2), garis tepi
// halus, ikon menu dalam kotak tonal navy #1B385C berikon biru muda
// (I2, pilihan Achmad 2026-11-10), judul putih ExtraBold. MODE LIGHT
// = H6 (pilihan Achmad 2026-11-12 dari papan H1-H6): kartu biru muda
// #D3E3F3 tanpa garis tepi, judul navy #0B1526, subjudul #3D5A75,
// kotak ikon putih berikon #2E5A88. Dipakai semua layar selain Beranda
// (Beranda memakai varian logo + tombol awan sinkron, ikut H6) dan
// Lainnya (identitas L1). -------

/** Warna batang header N2 (tetap di dua mode). */
val WarnaHeaderN2 = Color(0xFF0B1526)

@Composable
fun KartuHeaderPremium(judul: String, ikon: ImageVector, subjudul: String? = null) {
    val gelap = LocalIsDarkTheme.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (gelap) WarnaHeaderN2 else Color(0xFFD3E3F3)),
        border = if (gelap) BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = if (gelap) Color(0xFF1B385C) else Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(46.dp)) {
                Icon(ikon, null, tint = if (gelap) Color(0xFF9FCAFF) else Color(0xFF2E5A88), modifier = Modifier.padding(11.dp).fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(judul, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (gelap) Color.White else WarnaHeaderN2)
                if (subjudul != null) {
                    Text(subjudul, style = MaterialTheme.typography.bodySmall, color = if (gelap) Color.White.copy(alpha = 0.7f) else Color(0xFF3D5A75))
                }
            }
        }
    }
}


// ---------- Animasi terpilih (dikembalikan Achmad 2026-11-10: angka,
// centang sukses, denyut KRITIS; selebihnya aplikasi tanpa animasi) ----------


/** Angka berhitung naik dari 0 tiap kali layar ditampilkan (dipakai Pemasukan Hari Ini, Saldo Kas &
 *  Masuk/Keluar Keuangan, Laba). maxLines/overflow opsional untuk angka
 *  di kotak sempit (Ellipsis seperti Text polos sebelumnya). */
@Composable
fun AnimatedAmount(value: Int, style: TextStyle, color: Color, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, format: (Int) -> String = { formatIDR(it) }) {
    // format HARUS tetap jadi param terakhir: LabaScreen memakai trailing
    // lambda `{ "$it" }` untuk format — param baru ditaruh SEBELUM format.
    // remember (bukan rememberSaveable): saat pindah tab komposisi layar
    // dimusnahkan, jadi kembali ke tab ini `shown` mulai dari 0 lagi dan
    // angka menghitung naik seperti awal (permintaan Achmad 2026-11-16).
    var shown by remember { mutableStateOf(0) }
    val animated by animateIntAsState(
        targetValue = shown,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "amount"
    )
    LaunchedEffect(value) { shown = value }
    Text(format(animated), color = color, style = style, fontWeight = FontWeight.Bold, maxLines = maxLines, overflow = overflow)
}


/** Skala tombol saat ditekan — selalu 1f sejak semua animasi dihapus
 *  (2026-11-10); fungsi dipertahankan supaya pemanggil tidak berubah. */
@Composable
fun pressBounceScale(interactionSource: MutableInteractionSource): Float = 1f


/** Tombol stepper +/- Kasir dengan pantulan saat ditekan. */
@Composable
/** Tombol −/+ di keranjang: ringkas (32dp) supaya muat satu baris dengan nama, harga, dan hapus. */
fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressBounceScale(interactionSource)
    IconButton(onClick = onClick, interactionSource = interactionSource, modifier = Modifier.size(48.dp).graphicsLayer(scaleX = scale, scaleY = scale)) {
        Icon(icon, description, modifier = Modifier.size(20.dp))
    }
}


/** Chip penanda cara bayar di kartu pembelian Faktur PBF. Gaya lembut
 *  (soft tonal) — pilihan Achmad 2026-11-01 dari gambar pratinjau chip:
 *  wadah netral surfaceContainerHigh; "Cash" teks + ikon biru primer,
 *  "Kredit" teks + ikon jam tertiary supaya pembelian belum lunas
 *  langsung terlihat. */
@Composable
fun PurchasePaymentChip(paymentType: String) {
    val kredit = paymentType == "KREDIT"
    val fg = if (kredit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val icon = if (kredit) Icons.Default.Schedule else Icons.Default.Money
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                if (kredit) tr("Kredit", "Credit") else tr("Tunai", "Cash"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        },
        leadingIcon = {
            Icon(icon, null, modifier = Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = fg,
            leadingIconContentColor = fg,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledLabelColor = fg,
            disabledLeadingIconContentColor = fg
        ),
        border = null
    )
}


/** Chip penanda cara bayar di kartu Riwayat Penjualan (QRIS/Transfer;
 *  Tunai tidak memakai chip). Gaya lembut (soft tonal) — pilihan Achmad
 *  2026-11-01 dari gambar pratinjau chip: wadah netral
 *  surfaceContainerHigh, teks + ikon biru primer; ikon mengikuti metode
 *  (QR untuk QRIS, bank untuk Transfer). */
@Composable
fun SalePaymentChip(paymentMethod: String) {
    val icon = when (paymentMethod) {
        "QRIS" -> Icons.Default.QrCode
        "Transfer" -> Icons.Default.AccountBalance
        else -> Icons.Default.Money
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                paymentMethod,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        },
        leadingIcon = {
            Icon(icon, null, modifier = Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledLabelColor = MaterialTheme.colorScheme.primary,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary
        ),
        border = null
    )
}


/** Sisa hari menuju tanggal jatuh tempo (negatif = sudah lewat); null bila
 *  tanggalnya kosong/rusak. Dihitung dari kunci hari "yyyy-MM-dd" via tengah
 *  malam UTC supaya tidak bergeser zona waktu. */
fun dueDaysLeft(dueDate: String): Long? {
    val due = utcMidnightMillis(dueDate) ?: return null
    val today = utcMidnightMillis(dayKeyID(Date())) ?: return null
    return (due - today) / 86_400_000L
}


/** Pilihan harga satuan di keranjang Kasir: "Ecer" (harga jual biasa) atau
 *  "Grosir" (harga jual grosir). FilterChip resmi Material 3. */
@Composable
fun PriceModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}


/** Centang sukses pembayaran: lingkaran membesar dengan pegas, centang
 *  tergambar. Ukuran bisa diperbesar (fase sukses pembayaran memakai
 *  120.dp); padding dan tebal garis diskalakan mengikuti ukuran. */
@Composable
fun AnimatedCheckmark(size: Dp = 56.dp) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val circleScale by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = expressiveSpring(),
        label = "checkCircle"
    )
    val trim by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = 120, easing = FastOutSlowInEasing),
        label = "checkTrim"
    )
    val checkColor = MaterialTheme.colorScheme.primary
    val scaleF = size.value / 56f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.size(size).graphicsLayer(scaleX = circleScale, scaleY = circleScale)
    ) {
        Canvas(Modifier.fillMaxSize().padding((14 * scaleF).dp)) {
            // this.size = ukuran kanvas (DrawScope); parameter `size: Dp`
            // menaungi nama itu, jadi harus ditulis eksplisit lewat this.
            val w = this.size.width
            val h = this.size.height
            val stroke = (7 * scaleF).dp.toPx()
            val t1 = (trim * 2f).coerceIn(0f, 1f)
            val t2 = (trim * 2f - 1f).coerceIn(0f, 1f)
            if (t1 > 0f) {
                drawLine(
                    checkColor,
                    Offset(0.22f * w, 0.52f * h),
                    Offset(lerp(0.22f, 0.45f, t1) * w, lerp(0.52f, 0.72f, t1) * h),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            if (t2 > 0f) {
                drawLine(
                    checkColor,
                    Offset(0.45f * w, 0.72f * h),
                    Offset(lerp(0.45f, 0.80f, t2) * w, lerp(0.72f, 0.30f, t2) * h),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}


/** Denyut halus untuk lencana KRITIS (stok 0). */
@Composable
fun Modifier.pulseKritis(): Modifier {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(0.65f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            alpha.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer(alpha = alpha.value)
}


// Warna aksen layar memakai peran colorScheme resmi tema (lihat Theme.kt).
// Satu pengecualian atas permintaan Achmad (2026-11-01): pilihan Kas
// Masuk di sheet Catat Kas memakai biru tua pekat (peran skema terlalu
// pucat di mode gelap untuk penanda masuk), dengan teks putih di atasnya.
// Kas Keluar memakai colorScheme.error (R28, audit 2026-11-15 — unifikasi
// merah #BA1A1A, 2026-11-14), bukan lagi merah hardcode.
val KasMasukBlue = Color(0xFF0061A4)


@Composable
fun DemoBanner() {
    // Mode offline: semua data tersimpan di HP (Room), tidak ada banner demo.
}


@Composable
fun StockBadge(stock: Int, showCount: Boolean = true, large: Boolean = false) {
    val label = stockLabel(stock)
    // Teks badge mengikuti bahasa aktif; `label` sendiri tetap berbahasa
    // Indonesia karena dipakai sebagai kunci logika warna/ikon di bawah.
    val displayLabel = when (label) {
        "Aman" -> tr("Aman", "Safe")
        "Sedang" -> tr("Sedang", "Medium")
        "Rendah" -> tr("Rendah", "Low")
        else -> tr("Habis", "Out of stock")
    }
    // ST1 Kartu Baris (2026-11-13, pilihan Achmad dari papan ST1-ST6):
    // pil SOLID berwarna status dengan teks putih — sesuai mock ST1.
    // Warna dibuat tetap di kedua tema supaya kontras teks putih terjaga
    // (error tema #BA1A1A di kedua mode); "Habis" memakai
    // colorScheme.error (R28, audit 2026-11-15), bukan merah hardcode.
    // Animasi pulseKritis saat stok habis tetap dipertahankan.
    val bg = when (label) {
        "Aman" -> Color(0xFF43A047)
        "Sedang" -> Color(0xFF0284C7)
        "Rendah" -> Color(0xFFE3B341)
        else -> MaterialTheme.colorScheme.error
    }
    val statusIcon = when (label) {
        "Aman" -> Icons.Default.CheckCircle
        "Sedang" -> Icons.Default.Tonality
        "Rendah" -> Icons.Default.Warning
        else -> Icons.Default.Cancel
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = if (stock <= 0) Modifier.pulseKritis() else Modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(statusIcon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (showCount) "${tr("Stok", "Stock")} $stock • $displayLabel" else displayLabel,
                style = if (large) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ---------- Beranda ----------


// Kolom cari (BasicTextField + decorationBox), dipakai Kasir & Stok.
// V1 (pilihan Achmad 2026-11-16): kotak 20dp surfaceContainerHighest
// tanpa garis tepi — senada kotak stat P3 / baris K1 / shortcut R1,
// bukan lagi pill 22dp. Tombol X di kanan muncul saat ada teks,
// untuk hapus pencarian. Tinggi 48dp.
// OutlinedTextField tidak dipakai di sini karena tinggi paksa yang kecil
// membuat teksnya terpotong — OutlinedTextField Material 3 mencadangkan
// ruang untuk label dan tinggi minimalnya ~56dp. Dengan BasicTextField,
// padding dan posisi teks diatur sendiri sehingga tidak ada yang terpotong.
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(48.dp),
        decorationBox = { innerTextField ->
            Surface(
                // Latar surfaceContainerHighest tanpa garis tepi, senada
                // kotak stat P3 / baris K1 / shortcut R1.
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                tr("Cari obat…", "Search medicines…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                Icons.Default.Close, tr("Hapus pencarian", "Clear search"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}


@Composable
fun SortToggle(sortAz: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (sortAz) "A-Z" else "Z-A", style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Kasir ----------
