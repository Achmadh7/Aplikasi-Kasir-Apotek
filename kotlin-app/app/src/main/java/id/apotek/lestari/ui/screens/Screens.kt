@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.BtPrinter
import id.apotek.lestari.ui.buildReceiptBytes
import id.apotek.lestari.ui.getPairedPrinters
import id.apotek.lestari.ui.hasBluetoothPermission
import id.apotek.lestari.ui.isBluetoothEnabled
import id.apotek.lestari.ui.loadSavedPrinter
import id.apotek.lestari.ui.printReceiptBitmap
import id.apotek.lestari.ui.printReceiptToPrinter
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.savePrinter
import id.apotek.lestari.ui.shareReceiptBitmap
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

// ---------- Animasi Material 3 Expressive ----------


/** Jeda kemunculan bertahap per item daftar (maks. 400ms). */
fun entranceDelay(index: Int) = (index * 40).coerceAtMost(400)


/** Angka berhitung naik dari 0 (dipakai Pemasukan Hari Ini & Laba Kotor). */
@Composable
fun AnimatedAmount(value: Int, style: TextStyle, color: Color, format: (Int) -> String = { formatIDR(it) }) {
    var shown by remember { mutableStateOf(0) }
    val animated by animateIntAsState(
        targetValue = shown,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "amount"
    )
    LaunchedEffect(value) { shown = value }
    Text(format(animated), color = color, style = style, fontWeight = FontWeight.Bold)
}


/** Skala pegas saat ditekan (tombol utama & stepper Kasir). */
@Composable
fun pressBounceScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = expressiveSpring(),
        label = "pressBounce"
    ).value
}


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
                if (kredit) tr("Kredit", "Credit") else "Cash",
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
// Masuk/Keluar di sheet Catat Kas memakai biru tua & merah tua pekat
// (peran skema terlalu pucat di mode gelap untuk penanda masuk/keluar),
// dengan teks putih di atasnya.
val KasMasukBlue = Color(0xFF0061A4)


val KasKeluarRed = Color(0xFFD32F2F)


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
    // Gaya lembut (soft tonal) — pilihan Achmad 2026-11-01 dari gambar
    // pratinjau chip: wadah netral surfaceContainerHigh untuk semua
    // status; status dibaca dari warna teks + ikon kecil di depan.
    // (Di skema Fidelity, wadah container berwarna tampil terlalu pekat.)
    val fg = when (label) {
        "Aman" -> MaterialTheme.colorScheme.primary
        "Sedang" -> MaterialTheme.colorScheme.tertiary
        "Rendah" -> warningAmber()
        else -> MaterialTheme.colorScheme.error
    }
    val statusIcon = when (label) {
        "Aman" -> Icons.Default.CheckCircle
        "Sedang" -> Icons.Default.Tonality
        "Rendah" -> Icons.Default.Warning
        else -> Icons.Default.Cancel
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = if (stock <= 0) Modifier.pulseKritis() else Modifier,
        label = {
            Text(
                if (showCount) "${tr("Stok", "Stock")} $stock • $displayLabel" else displayLabel,
                style = if (large) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        },
        leadingIcon = {
            Icon(statusIcon, null, modifier = Modifier.size(16.dp))
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

// ---------- Beranda ----------


// Kolom cari ramping berbentuk pil (BasicTextField + decorationBox), dipakai Kasir & Stok.
// OutlinedTextField tidak dipakai di sini karena tinggi paksa yang kecil (44dp)
// membuat teksnya terpotong — OutlinedTextField Material 3 mencadangkan ruang
// untuk label dan tinggi minimalnya ~56dp. Dengan BasicTextField, padding dan
// posisi teks diatur sendiri sehingga tidak ada yang terpotong.
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    // Garis tepi (input line) pill dibuat putih di mode gelap supaya jelas
    // terlihat (permintaan Achmad — latar pill dikembalikan gelap seperti
    // semula, bukan putih). Mode terang tidak berubah.
    val isDark = LocalIsDarkTheme.current
    val borderColor = if (isDark) {
        Color.White
    } else if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(44.dp)
            .onFocusEvent { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Surface(
                // Latar surfaceContainer (bukan surface) supaya pill terlihat jelas
                // di atas latar layar — surface sama dengan latar (#0A0E14 di mode
                // gelap) sehingga pill sebelumnya hanya dibatasi garis tipis.
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, borderColor),
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
