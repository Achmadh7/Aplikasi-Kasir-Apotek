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

private val repo = Repository()
val CATEGORIES = listOf("Umum","Obat Bebas","Obat Bebas Terbatas","Obat Keras","Antibiotik","Vitamin","Pereda Nyeri","Obat Batuk & Flu","Pencernaan","Kulit","Mata","Lainnya")
val UNITS = listOf("Strip","Botol","Vial","Ampul","Box","Tablet","Kapsul","Tube","Drop","Sachet","Pcs")

// Kelas lebar jendela (HP/tablet, portrait/landscape) untuk layout adaptif.
// Compact < 600dp, Medium 600-840dp, Expanded > 840dp. Nilai disediakan dari
// MainActivity lewat CompositionLocal agar tidak mengubah signature screen.
val LocalWindowWidthClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/** Jumlah kolom grid adaptif menurut kelas lebar jendela. */
@Composable
private fun adaptiveColumns(compact: Int, medium: Int, expanded: Int): Int = when (LocalWindowWidthClass.current) {
    WindowWidthSizeClass.Medium -> medium
    WindowWidthSizeClass.Expanded -> expanded
    else -> compact
}

/** Lebar maksimal bottom sheet: selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet. */
@Composable
private fun adaptiveSheetMaxWidth() = when {
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
private fun entranceDelay(index: Int) = (index * 40).coerceAtMost(400)

/** Angka berhitung naik dari 0 (dipakai Pemasukan Hari Ini & Laba Kotor). */
@Composable
private fun AnimatedAmount(value: Int, style: TextStyle, color: Color, format: (Int) -> String = { formatIDR(it) }) {
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
private fun pressBounceScale(interactionSource: MutableInteractionSource): Float {
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
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
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
private fun PurchasePaymentChip(paymentType: String) {
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
private fun SalePaymentChip(paymentMethod: String) {
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
private fun dueDaysLeft(dueDate: String): Long? {
    val due = utcMidnightMillis(dueDate) ?: return null
    val today = utcMidnightMillis(dayKeyID(Date())) ?: return null
    return (due - today) / 86_400_000L
}

/** Pilihan harga satuan di keranjang Kasir: "Ecer" (harga jual biasa) atau
 *  "Grosir" (harga jual grosir). FilterChip resmi Material 3. */
@Composable
private fun PriceModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/** Centang sukses pembayaran: lingkaran membesar dengan pegas, centang tergambar. */
@Composable
private fun AnimatedCheckmark() {
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.size(56.dp).graphicsLayer(scaleX = circleScale, scaleY = circleScale)
    ) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 7.dp.toPx()
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
private fun Modifier.pulseKritis(): Modifier {
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
private val KasMasukBlue = Color(0xFF0061A4)
private val KasKeluarRed = Color(0xFFD32F2F)

@Composable
fun DemoBanner() {
    // Mode offline: semua data tersimpan di HP (Room), tidak ada banner demo.
}

@Composable
private fun StockBadge(stock: Int, showCount: Boolean = true, large: Boolean = false) {
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

@Composable
fun BerandaScreen(onGoStok: () -> Unit, onGoPengingat: () -> Unit, onGoLaba: () -> Unit, onGoRiwayat: () -> Unit) {
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(dashboard) { if (dashboard != null && !listEntered) listEntered = true }
    var salesTab by remember { mutableIntStateOf(0) }
    var outOfStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var lowStockMeds by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var expiringMeds by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var dueBills by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        (repo.getDashboard() as? ApiResult.Success)?.let { dashboard = it.data }
        val meds = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
        outOfStockNames = meds.filter { it.stock <= 0 }.map { it.name }.sorted()
        // Stok menipis = Rendah (1-9 pada skala status baru) — diurutkan dari
        // yang paling sedikit supaya yang paling mendesak tampil duluan.
        // Stok 0 tidak ikut di sini karena sudah tampil di bagian Habis.
        lowStockMeds = meds.filter { it.stock in 1..9 }
            .sortedBy { it.stock }
            .map { it.name to it.stock }
        val in30Key = dayKeyID(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        // Tanpa batas bawah: obat yang sudah kadaluarsa ikut tampil di sini
        // (konsisten dengan expiringCount di Dashboard).
        expiringMeds = meds.filter { it.expiryDate != null && it.expiryDate <= in30Key }
            .sortedBy { it.expiryDate }
            .map { it.name to it.expiryDate!! }
        // Tagihan PBF: pembelian Kredit yang jatuh temponya <= 7 hari lagi
        // atau sudah lewat (pilihan Achmad 2026-10-30, saran no. 1) —
        // diurutkan dari jatuh tempo paling awal.
        val purchases = (repo.listPurchases() as? ApiResult.Success)?.data?.purchases.orEmpty()
        dueBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isNotBlank() && (dueDaysLeft(p.dueDate) ?: Long.MAX_VALUE) <= 7L
        }.sortedBy { it.dueDate }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        // Jarak antar kartu diperbesar 12 -> 20dp (permintaan Achmad
        // 2026-10-30 dari screenshot Beranda — kartu-kartu tampak terlalu rapat).
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = rememberLogoBitmap()
                if (logo != null) {
                    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                        Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp).fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text("Apotek Lestari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(todayLabelID(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(SpaceS))
            Text(tr("Penjualan Hari Ini", "Today's Sales"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            }
            }
        }
        dashboard?.let { d ->
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Kartu Pemasukan Hari Ini — hero: ExpressiveCard resmi
                // (primaryContainer) + insight vs kemarin.
                val diff = d.todayIn - d.yesterdayIn
                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                        .animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("PEMASUKAN HARI INI", "TODAY'S INCOME"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = CircleShape) {
                                Icon(Icons.Default.TrendingUp, null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        AnimatedAmount(d.todayIn, MaterialTheme.typography.headlineLarge, Color.White)
                        Spacer(Modifier.height(SpaceXS))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("${d.totalSalesToday} transaksi penjualan", "${d.totalSalesToday} sales transactions"), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(SpaceS))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                diff > 0 -> {
                                    Icon(Icons.Default.TrendingUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Naik ${formatIDR(diff)} dari kemarin", "Up ${formatIDR(diff)} from yesterday"), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                diff < 0 -> {
                                    Icon(Icons.Default.TrendingDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Turun ${formatIDR(-diff)} dari kemarin", "Down ${formatIDR(-diff)} from yesterday"), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                else -> {
                                    Text(tr("Sama seperti kemarin", "Same as yesterday"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Shortcut ke menu yang tidak lagi ada di bar bawah (Laba, Pengingat, Riwayat).
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard(label = tr("Laba", "Profit"), icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f), onClick = onGoLaba)
                    ShortcutCard(label = tr("Pengingat", "Reminders"), icon = Icons.Default.NotificationsActive, modifier = Modifier.weight(1f), onClick = onGoPengingat)
                    ShortcutCard(label = tr("Riwayat", "History"), icon = Icons.Default.ReceiptLong, modifier = Modifier.weight(1f), onClick = onGoRiwayat)
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(tr("Pengingat", "Reminders"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(SpaceS))
                        // Stok menipis: nama obat + sisa stok, ditulis sama seperti
                        // stok habis (titik & nama merah) supaya sama-sama terlihat mendesak.
                        if (lowStockMeds.isNotEmpty()) {
                            lowStockMeds.take(5).forEach { (name, stock) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tr("Sisa: $stock", "Left: $stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (lowStockMeds.size > 5) Text(tr("+${lowStockMeds.size - 5} lainnya", "+${lowStockMeds.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        // Stok habis: nama obat + keterangan "Habis".
                        if (outOfStockNames.isNotEmpty()) {
                            outOfStockNames.take(5).forEach { name ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tr("Habis", "Out of stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (outOfStockNames.size > 5) Text(tr("+${outOfStockNames.size - 5} lainnya", "+${outOfStockNames.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        if (d.expiringCount > 0) Text(tr("• ${d.expiringCount} obat dekat kadaluarsa", "• ${d.expiringCount} medicines expiring soon"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        if (expiringMeds.isNotEmpty()) {
                            expiringMeds.take(5).forEach { (name, exp) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatDateID(exp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (expiringMeds.size > 5) Text(tr("+${expiringMeds.size - 5} lainnya", "+${expiringMeds.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        // Tagihan PBF mendekati jatuh tempo: nama PBF ditulis
                        // lengkap + sisa hari di kanan (permintaan Achmad
                        // 2026-10-30 supaya informasinya lengkap), maks. 3.
                        if (dueBills.isNotEmpty()) {
                            Text(tr("• ${dueBills.size} tagihan PBF mendekati jatuh tempo", "• ${dueBills.size} PBF bills nearing due date"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                            dueBills.take(3).forEach { p ->
                                val daysLeft = dueDaysLeft(p.dueDate) ?: 0L
                                val (dueLabel, dueColor) = when {
                                    daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                                    daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                                    daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                                    else -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(dueColor, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.pbfName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dueLabel, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = dueColor)
                                }
                            }
                            if (dueBills.size > 3) Text(tr("+${dueBills.size - 3} lainnya", "+${dueBills.size - 3} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        if (d.lowStockCount == 0 && d.expiringCount == 0 && dueBills.isEmpty()) Text(tr("Semua stok dan kadaluarsa aman.", "All stock and expiry dates are safe."), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        if (d.lowStockCount > 0 || d.expiringCount > 0 || dueBills.isNotEmpty()) {
                            Spacer(Modifier.height(SpaceS))
                            TextButton(onClick = onGoPengingat) { Text(tr("Lihat Pengingat", "View Reminders"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)) }
                        }
                        Spacer(Modifier.height(SpaceS))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(SpaceS))
                        Text(tr("${d.totalMedicines} jenis obat terdaftar di Stok", "${d.totalMedicines} medicines registered in Stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Kartu uang: Pengeluaran & Selisih Kas Hari Ini disusun
                // VERTIKAL selebar kartu (label di atas, angka di bawah) —
                // diubah dari sejajar setengah lebar (saran asisten, dipilih
                // Achmad 2026-10-30) supaya teksnya tidak kepotong.
                // Selisih Kas Bulan Ini di bawah garis pemisah.
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Column {
                            Text(tr("Pengeluaran Hari Ini", "Today's Expenses"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(SpaceXS))
                            Text(formatIDR(d.todayOut), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(SpaceM))
                        Column {
                            Text(tr("Selisih Kas Hari Ini", "Today's Cash Difference"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(SpaceXS))
                            Text(formatIDR(d.todayProfit), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = if (d.todayProfit >= 0) profitGreen() else MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(SpaceM))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(SpaceS))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Selisih Kas Bulan Ini", "This Month's Cash Difference"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium)
                            Text(formatIDR(d.monthProfit), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = if (d.monthProfit >= 0) profitGreen() else MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(SpaceXS))
                        Text(tr("Masuk ${formatIDR(d.monthIn)} • Keluar ${formatIDR(d.monthOut)}", "In ${formatIDR(d.monthIn)} • Out ${formatIDR(d.monthOut)}"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Penjualan Hari Ini: Obat Terjual dan Transaksi dalam satu kartu bertab (compact).
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                            listOf(tr("Obat Terjual", "Items Sold") to 0, tr("Transaksi", "Transactions") to 1).forEach { (label, idx) ->
                                val selected = salesTab == idx
                                Surface(
                                    onClick = { salesTab = idx },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        if (salesTab == 0) {
                        if (d.bestSellers.isEmpty()) {
                            Text(tr("Belum ada obat terjual hari ini.", "No items sold today yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            d.bestSellers.forEachIndexed { index, b ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${b.qty} item • ${formatIDR(b.revenue)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        } else {
                        if (d.recentSales.isEmpty()) {
                            Text(tr("Belum ada penjualan hari ini.", "No sales today yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            d.recentSales.forEachIndexed { index, s ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatTimeID(s.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatIDR(s.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        }
                    }
                }
                }
            }
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }
}

@Composable
fun ShortcutCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExpressiveCard(shape = RoundedCornerShape(20.dp), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(SpaceS))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

class CartItem(val medicine: Medicine, qty: Int) {
    var qty by mutableStateOf(qty)
    // Pilihan harga per item di keranjang: ecer (bawaan) atau grosir
    // (permintaan Achmad 2026-10-29). Harga satuan yang berlaku dihitung
    // dari pilihan ini; Repository.createSale menerapkan aturan yang sama
    // saat harga disimpan ke penjualan.
    var useWholesale by mutableStateOf(false)
    val unitPrice: Int
        get() = if (useWholesale && medicine.wholesalePrice > 0) medicine.wholesalePrice else medicine.sellPrice
}

/**
 * Millis tengah malam UTC untuk tanggal "yyyy-MM-dd" — dipakai sebagai
 * initialSelectedDateMillis DatePicker. DatePicker memakai UTC, sedangkan
 * parseISODate memakai timezone default HP untuk pola "yyyy-MM-dd", sehingga
 * tanggal yang dipilih sebelumnya bisa geser 1 hari di zona WIB.
 */
private fun utcMidnightMillis(dateKey: String): Long? {
    if (dateKey.isBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(dateKey)?.time
    } catch (_: Exception) {
        null
    }
}

/** Pemilih tanggal maksimum hari ini — untuk tanggal yang tidak boleh dari
 * masa depan (tanggal entri Catat Kas & Tanggal Beli PBF; audit 2026-11-02,
 * M3: entri bertanggal depan terhitung di Saldo Kas tapi tak terlihat di
 * filter Hari Ini/Bulan Ini). Tanggal kedaluwarsa & jatuh tempo TIDAK
 * memakai ini karena memang berada di masa depan. */
private object NoFutureDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val todayMillis = utcMidnightMillis(dayKeyID(Date())) ?: return true
        return utcTimeMillis <= todayMillis
    }
}

/** "yyyy-MM-dd" (WIB) -> string ISO UTC ("2026-10-27T00:00:00.000Z") untuk createdAt entri kas. */
private fun dayKeyToIsoUtc(dayKey: String): String? {
    val millis = utcMidnightMillis(dayKey) ?: return null
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}

// Kolom cari ramping berbentuk pil (BasicTextField + decorationBox), dipakai Kasir & Stok.
// OutlinedTextField tidak dipakai di sini karena tinggi paksa yang kecil (44dp)
// membuat teksnya terpotong — OutlinedTextField Material 3 mencadangkan ruang
// untuk label dan tinggi minimalnya ~56dp. Dengan BasicTextField, padding dan
// posisi teks diatur sendiri sehingga tidak ada yang terpotong.
@Composable
private fun SearchField(
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
private fun SortToggle(sortAz: Boolean, onToggle: () -> Unit) {
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

@Composable
fun KasirScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    var search by rememberSaveable { mutableStateOf("") }
    var sortAz by remember { mutableStateOf(true) }
    // Pesan umpan balik di bawah kolom cari (mis. stok tidak cukup saat obat
    // diketuk). Dulu juga dipakai hasil scan barcode; scan kamera dihapus
    // atas permintaan Achmad 2026-10-29.
    var cartMsg by remember { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("Tunai") }
    // TextFieldValue (bukan Int) + formatDecimalFieldValue supaya kursor tidak
    // meloncat saat mengetik nominal; TextFieldValue punya Saver bawaan.
    var uangDiterima by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var payMsg by rememberSaveable { mutableStateOf("") }
    var payCount by remember { mutableIntStateOf(0) }
    // Struk terakhir disimpan sebagai id saja, lalu dimuat ulang dari repo.
    var lastSaleId by rememberSaveable { mutableIntStateOf(0) }
    var lastSale by remember { mutableStateOf<Sale?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val receiptLayer = rememberGraphicsLayer()
    // Keranjang survive rotasi lewat pasangan (medicineId, qty) yang saveable;
    // cart sendiri dibangun ulang dari daftar obat begitu tersedia.
    var savedCart by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val cart = remember { mutableStateListOf<CartItem>() }
    // Penanda pemulihan awal keranjang sudah selesai (daftar obat pertama
    // kali tersedia). Selama false, penulisan savedCart ditahan supaya data
    // tersimpan tidak tertimpa keranjang kosong sebelum sempat dipulihkan.
    var cartInitialized by remember { mutableStateOf(false) }
    // Obat yang jumlahnya sedang diubah lewat dialog ketik angka (null = dialog tutup).
    var qtyEditTarget by remember { mutableStateOf<CartItem?>(null) }
    val scope = rememberCoroutineScope()
    // Menutup sheet Kasir DENGAN animasi: sheetState.hide() menganimasikan
    // sheet turun terlebih dahulu, baru sheet dilepas dari komposisi.
    // Menyetel showSheet = false secara langsung membuat sheet hilang
    // sekejap tanpa animasi (dilaporkan Achmad 2026-11-01 pada tombol
    // Tutup setelah pembayaran selesai).
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) showSheet = false
        }
    }
    // Supaya field Uang Diterima otomatis tergulir ke atas keyboard saat diketuk.
    val amountBringIntoView = remember { BringIntoViewRequester() }
    // Animasi "terbang ke keranjang": saat obat ditambahkan, ikon obat melayang
    // dari kartu obat yang diketuk ke bar keranjang. Posisi dicatat dalam
    // koordinat window lalu diubah ke koordinat lokal Box root (dikurangi
    // posisi window Box root). cardCenters = pusat kartu obat per id obat.
    var rootWindowPos by remember { mutableStateOf(Offset.Zero) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var cartBarCenter by remember { mutableStateOf<Offset?>(null) }
    val cardCenters = remember { mutableStateMapOf<Int, Offset>() }
    var flyFrom by remember { mutableStateOf<Offset?>(null) }
    var flyTo by remember { mutableStateOf(Offset.Zero) }
    var flyId by remember { mutableIntStateOf(0) }
    // Density dibaca di sini (saat komposisi) supaya bisa dipakai di dalam
    // handler klik untuk menghitung posisi fallback target.
    val flyDensity = LocalDensity.current
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    // Pulihkan keranjang dari data tersimpan, sekali saja, begitu daftar
    // obat pertama kali tersedia. Sesudah itu cartInitialized menyala dan
    // penulisan savedCart di bawah mengambil alih.
    LaunchedEffect(medicines) {
        if (medicines.isNotEmpty() && !cartInitialized) {
            if (cart.isEmpty() && savedCart.isNotEmpty()) {
                // Format lama (berpasangan id+qty) tidak berkelipatan 3 —
                // buang saja daripada memulihkan keranjang yang salah tafsir.
                if (savedCart.size % 3 == 0) {
                    val byId = medicines.associateBy { it.id }
                    savedCart.chunked(3).forEach { triple ->
                        if (triple.size == 3) byId[triple[0]]?.let { med ->
                            cart.add(CartItem(med, triple[1]).also { ci -> ci.useWholesale = triple[2] == 1 })
                        }
                    }
                }
            }
            cartInitialized = true
        }
    }
    // Simpan isi keranjang (id + qty + pilihan grosir) setiap kali berubah —
    // TERMASUK saat kosong, supaya keranjang yang dikosongkan manual tidak
    // bangkit lagi setelah rotasi atau pindah tab (audit 2026-11-02, M1:
    // sebelumnya penulisan diskip saat kosong, jadi isi lama bertahan di
    // savedCart dan dipulihkan lagi). Pilihan grosir ikut disimpan (audit
    // N2, 2026-10-30): tanpa itu, item yang dipulihkan diam-diam kembali ke
    // harga Ecer dan total berubah sementara isian Uang Diterima tetap.
    val cartSnapshot = cart.map { Triple(it.medicine.id, it.qty, it.useWholesale) }
    LaunchedEffect(cartSnapshot, cartInitialized) {
        if (cartInitialized) {
            savedCart = cartSnapshot.flatMap { listOf(it.first, it.second, if (it.third) 1 else 0) }
        }
    }
    // Muat ulang struk terakhir dari id tersimpan (untuk restore setelah rotasi).
    LaunchedEffect(lastSaleId) {
        if (lastSaleId != 0 && lastSale?.id != lastSaleId) {
            lastSale = (repo.listSales() as? ApiResult.Success)?.data?.sales?.firstOrNull { it.id == lastSaleId }
        }
    }
    val filtered = medicines.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) || (it.barcode?.contains(search) == true) }
    val sorted = if (sortAz) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
    val total = cart.sumOf { it.unitPrice * it.qty }
    val cartQty = cart.sumOf { it.qty }
    val paid = parseDecimal(uangDiterima.text)
    val change = if (method == "Tunai") (paid - total).coerceAtLeast(0) else 0
    val canPay = cart.isNotEmpty() && (method != "Tunai" || paid >= total)
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                rootWindowPos = it.positionInWindow()
                rootSizePx = it.size
            }
    ) {
        // Daftar obat memenuhi layar; keranjang & pembayaran pindah ke bottom sheet.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.PointOfSale, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Kasir", "Cashier"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        DemoBanner()
        SearchField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth()
        )
        if (cartMsg.isNotEmpty()) {
            Spacer(Modifier.height(SpaceS))
            Text(cartMsg, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(SpaceL))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${filtered.size} obat", "${filtered.size} medicines"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SortToggle(sortAz = sortAz, onToggle = { sortAz = !sortAz })
        }
        Spacer(Modifier.height(SpaceS))
        Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
            sorted.forEachIndexed { index, m ->
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                val out = m.stock <= 0
                ExpressiveCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                        .onGloballyPositioned { cardCenters[m.id] = it.boundsInWindow().center }
                        .clickable(enabled = !out) {
                        val ex = cart.firstOrNull { it.medicine.id == m.id }
                        if (ex != null && ex.qty >= m.stock) {
                            cartMsg = tr("Stok ${m.name} hanya ${m.stock} — tidak bisa ditambah lagi", "Only ${m.stock} of ${m.name} in stock — can't add more")
                        } else {
                            lastSale = null
                            lastSaleId = 0
                            payMsg = ""
                            if (ex != null) ex.qty++ else cart.add(CartItem(m, 1))
                            // Picu animasi terbang ke keranjang: dari pusat kartu
                            // obat ini ke pusat bar keranjang (koordinat lokal Box).
                            // Kalau posisi bar keranjang belum tercatat (keranjang
                            // masih kosong saat obat pertama ditambah), pakai
                            // perkiraan tengah-bawah layar sebagai target.
                            val start = cardCenters[m.id]
                            if (start != null) {
                                val fallbackTarget = Offset(
                                    rootSizePx.width / 2f,
                                    rootSizePx.height - with(flyDensity) { 64.dp.toPx() }
                                )
                                flyFrom = start - rootWindowPos
                                flyTo = (cartBarCenter ?: fallbackTarget) - rootWindowPos
                                flyId++
                            }
                        }
                    }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (out) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(SpaceXS))
                            Text("${m.category} • ${m.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceXS))
                            Text(formatIDR(m.sellPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        // Keterangan stok di Kasir sedikit diperbesar
                        // (permintaan Achmad 2026-11-01); di Stok tetap biasa.
                        StockBadge(m.stock, large = true)
                    }
                }
                }
            }
        }
            // Ruang untuk bar keranjang yang nempel di bawah, supaya obat terakhir tetap bisa digulir ke atasnya.
            if (cart.isNotEmpty()) Spacer(Modifier.height(96.dp))
        }

        // Bar keranjang nempel di bawah: total + tombol Bayar (membuka bottom sheet).
        AnimatedVisibility(
            visible = cart.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .onGloballyPositioned { cartBarCenter = it.boundsInWindow().center }
                    .clickable { showSheet = true }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(cartQty) {
                        val cartPop = remember { Animatable(1.4f) }
                        LaunchedEffect(cartQty) { cartPop.animateTo(1f, expressiveSpring()) }
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.graphicsLayer(scaleX = cartPop.value, scaleY = cartPop.value)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$cartQty", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$cartQty item", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { showSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(48.dp)
                    ) { Text(tr("Bayar", "Pay"), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }

        // Overlay animasi terbang ke keranjang: ikon obat melayang dari kartu
        // yang diketuk ke bar keranjang, mengecil & memudar. Overlay ini tidak
        // menghalangi sentuhan (tidak ada clickable di dalamnya).
        // Posisi dianimasikan lewat progress 0f -> 1f + lerp manual, bukan
        // Animatable<Offset> — Offset.VectorConverter tidak ter-resolve di
        // versi Compose ini (itu penyebab error build). coroutineScope
        // menunggu ketiga animasi selesai dulu sebelum overlay dihapus —
        // tanpa itu, flyFrom = null langsung jalan dan overlay hilang
        // sebelum animasi terlihat.
        if (flyFrom != null) {
            key(flyId) {
                val start = flyFrom!!
                val end = flyTo
                val iconHalfPx = with(flyDensity) { 22.dp.toPx() }
                val progress = remember { Animatable(0f) }
                val scale = remember { Animatable(1f) }
                val alpha = remember { Animatable(1f) }
                LaunchedEffect(flyId) {
                    coroutineScope {
                        launch { progress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing)) }
                        launch { scale.animateTo(0.4f, animationSpec = tween(500)) }
                        launch { alpha.animateTo(0f, animationSpec = tween(500)) }
                    }
                    flyFrom = null
                }
                val current = Offset(
                    x = start.x + (end.x - start.x) * progress.value,
                    y = start.y + (end.y - start.y) * progress.value
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (current.x - iconHalfPx).roundToInt(),
                                    (current.y - iconHalfPx).roundToInt()
                                )
                            }
                            .size(44.dp)
                            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Medication, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet: keranjang + pembayaran (dan struk setelah bayar berhasil).
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = closeSheet,
            sheetState = sheetState,
            // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar
            // di HP potret, 360dp di HP lanskap, 640dp di tablet.
            sheetMaxWidth = adaptiveSheetMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (cart.isNotEmpty()) tr("Keranjang (${cart.sumOf { it.qty }})", "Cart (${cart.sumOf { it.qty }})") else tr("Keranjang", "Cart"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = closeSheet) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                if (cart.isNotEmpty()) {
                    cart.forEachIndexed { index, ci ->
                        // Dua baris per item: (nama | total baris), lalu
                        // (pilihan Ecer/Grosir | stepper jumlah | hapus | harga
                        // satuan). Pilihan harga ditaruh persis di samping tombol
                        // -/+ jumlah, dan ikon hapus di sebelah kanan tombol +
                        // (keduanya sesuai permintaan Achmad 2026-10-29).
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ci.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(formatIDR(ci.unitPrice * ci.qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.End)
                            }
                            Spacer(Modifier.height(SpaceXS))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pilihan harga hanya muncul bila harga grosir
                                // obat ini sudah diatur (> 0) di data obat.
                                if (ci.medicine.wholesalePrice > 0) {
                                    PriceModeChip(tr("Ecer", "Retail"), selected = !ci.useWholesale) { ci.useWholesale = false }
                                    Spacer(Modifier.width(6.dp))
                                    PriceModeChip(tr("Grosir", "Wholesale"), selected = ci.useWholesale) { ci.useWholesale = true }
                                    Spacer(Modifier.width(12.dp))
                                }
                                StepperButton(Icons.Default.Remove, tr("Kurangi", "Decrease")) { if (ci.qty > 1) ci.qty-- else cart.remove(ci) }
                                // Animasi "pop" angka jumlah HANYA saat jumlah berubah
                                // SETELAH komposisi pertama. Dulu key(ci.qty) +
                                // Animatable(1.35f) membuat pop berjalan di semua
                                // baris tepat saat animasi buka sheet berjalan —
                                // salah satu sumber jitter saat membuka keranjang.
                                val qtyPop = remember { Animatable(1f) }
                                var qtyPopArmed by remember { mutableStateOf(false) }
                                LaunchedEffect(ci.qty) {
                                    if (qtyPopArmed) {
                                        qtyPop.snapTo(1.35f)
                                        qtyPop.animateTo(1f, expressiveSpring())
                                    } else {
                                        qtyPopArmed = true
                                    }
                                }
                                // Angka jumlah bisa diketuk untuk ketik jumlah langsung
                                // (pill abu-abu menandakan bisa diketuk).
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(40.dp).clickable { qtyEditTarget = ci }
                                ) {
                                    Text(
                                        "${ci.qty}",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.width(44.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically).graphicsLayer(scaleX = qtyPop.value, scaleY = qtyPop.value),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                StepperButton(Icons.Default.Add, tr("Tambah", "Increase")) { if (ci.qty < ci.medicine.stock) ci.qty++ }
                                Spacer(Modifier.width(8.dp))
                                // Hapus: keluarkan obat dari keranjang (di kanan
                                // tombol + jumlah).
                                IconButton(onClick = { cart.remove(ci) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Delete, tr("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text("@ ${formatIDR(ci.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < cart.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                Spacer(Modifier.height(SpaceS))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Total bayar", "Total to pay"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(formatIDR(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(Modifier.height(SpaceL))
                Text(tr("METODE BAYAR", "PAYMENT METHOD"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpaceS))
                // Metode bayar: SingleChoiceSegmentedButtonRow resmi Material 3
                // (menggantikan pil meluncur rakitan manual).
                val methods = listOf(
                    "Tunai" to Icons.Default.Money,
                    "QRIS" to Icons.Default.QrCode,
                    "Transfer" to Icons.Default.AccountBalance
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    methods.forEachIndexed { index, (m, icon) ->
                        SegmentedButton(
                            selected = method == m,
                            onClick = { method = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                            icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (m == "Tunai") tr("Tunai", "Cash") else m, maxLines = 1) }
                    }
                }
                AnimatedContent(
                    targetState = method,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(250)) + slideInVertically(animationSpec = tween(250)) { it / 4 }) togetherWith
                        (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 4 })
                    },
                    label = "methodContent"
                ) { targetMethod ->
                    if (targetMethod == "Tunai") {
                        Column {
                            Spacer(Modifier.height(SpaceL))
                            Text(tr("UANG DITERIMA", "CASH RECEIVED"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceS))
                            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                                listOf(20000 to "Rp20.000", 50000 to "Rp50.000", 100000 to "Rp100.000").forEach { (nominal, label) ->
                                    OutlinedButton(
                                        onClick = {
                                            val formatted = formatDecimal((parseDecimal(uangDiterima.text) + nominal).toString())
                                            uangDiterima = TextFieldValue(formatted, TextRange(formatted.length))
                                        },
                                        shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) { Text(label, maxLines = 1) }
                                }
                                Button(
                                    onClick = {
                                        val formatted = formatDecimal(total.toString())
                                        uangDiterima = TextFieldValue(formatted, TextRange(formatted.length))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) { Text(tr("Uang Pas", "Exact Amount"), maxLines = 1) }
                            }
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Ketuk nominal untuk menambah, atau Uang Pas untuk jumlah persis.", "Tap an amount to add, or Exact Amount for the precise total."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceS))
                            OutlinedTextField(
                                value = uangDiterima,
                                onValueChange = { uangDiterima = formatDecimalFieldValue(it) },
                                label = { Text(tr("cth: 50.000", "e.g. 50.000")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .bringIntoViewRequester(amountBringIntoView)
                                    .onFocusEvent { if (it.isFocused) scope.launch { amountBringIntoView.bringIntoView() } }
                            )
                            AnimatedVisibility(visible = change > 0) {
                                Column {
                                    Spacer(Modifier.height(SpaceS))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr("Kembalian", "Change"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(formatIDR(change), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column {
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Dibayar penuh via $targetMethod sebesar ${formatIDR(total)}.", "Paid in full via $targetMethod, ${formatIDR(total)}."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Pratinjau struk sebelum bayar — kasir bisa cek isi, total,
                // metode, dan kembalian dulu; ikut berubah saat keranjang,
                // metode, atau uang diterima diubah.
                if (cart.isNotEmpty()) {
                    Spacer(Modifier.height(SpaceL))
                    Text(tr("PRATINJAU STRUK", "RECEIPT PREVIEW"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceS))
                    ReceiptPreviewCard(
                        items = cart,
                        total = total,
                        method = method,
                        paid = paid,
                        change = change
                    )
                }
                Spacer(Modifier.height(SpaceL))
                val bayarInteraction = remember { MutableInteractionSource() }
                val bayarScale = pressBounceScale(bayarInteraction)
                Button(
                    onClick = {
                        scope.launch {
                            val amt = if (method == "Tunai") paid else total
                            val result = repo.createSale(CreateSaleArgs(cart.map { CreateSaleItemArgs(it.medicine.id, it.qty, it.useWholesale) }, method, amt))
                            if (result is ApiResult.Success) {
                                payMsg = tr("Pembayaran $method berhasil. Total ${formatIDR(total)}", "Payment successful. Total ${formatIDR(total)}") + if (method == "Tunai") tr(" • Kembalian ${formatIDR(change)}", " • Change ${formatIDR(change)}") else ""
                                payCount++
                                cart.clear()
                                savedCart = emptyList()
                                uangDiterima = TextFieldValue("")
                                val after = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
                                medicines = after
                                val fresh = (repo.listSales() as? ApiResult.Success)?.data?.sales.orEmpty()
                                lastSale = fresh.firstOrNull { it.id == result.data.id }
                                lastSaleId = result.data.id
                            } else {
                                // Gagal: keranjang & uang diterima dibiarkan utuh supaya
                                // kasir bisa mencoba lagi tanpa mengulang input.
                                payMsg = tr("Pembayaran gagal: ", "Payment failed: ") + ((result as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                            }
                        }
                    },
                    enabled = canPay,
                    interactionSource = bayarInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer(scaleX = bayarScale, scaleY = bayarScale)
                ) { Text(tr("Bayar (${formatIDR(total)})", "Pay (${formatIDR(total)})"), style = MaterialTheme.typography.titleMedium) }
                }
                // Sengaja TANPA AnimatedVisibility: animasi mengembangnya
                // bertabrakan dengan animasi tinggi sheet (pegas draggable)
                // sehingga gerak sheet terasa jitter setelah pembayaran.
                if (payMsg.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(SpaceS))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            key(payCount) { AnimatedCheckmark() }
                            Spacer(Modifier.width(10.dp))
                            Text(payMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        }
                    }
                }
                // Sengaja TANPA AnimatedVisibility (alasan sama seperti blok
                // pesan sukses di atas): blok struk ini tinggi — animasi
                // mengembangnya membuat sheet jitter saat struk muncul dan
                // saat sheet ditutup dari tampilan struk.
                if (lastSale != null) {
                    Column {
                        Spacer(Modifier.height(SpaceL))
                        lastSale?.let {
                            InvoiceDetailCard(sale = it, graphicsLayer = receiptLayer)
                            Spacer(Modifier.height(SpaceM))
                            ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = it.invoiceNo, sale = it)
                            Spacer(Modifier.height(SpaceM))
                            OutlinedButton(
                                onClick = closeSheet,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(tr("Tutup", "Close")) }
                        }
                    }
                }
            }
        }
    }
    // Dialog ubah jumlah langsung: ketuk angka jumlah di keranjang, lalu ketik
    // angka pasti — tidak perlu mengetuk + berulang kali. Jumlah 0 mengeluarkan
    // obat dari keranjang (sama seperti − saat jumlah 1); jumlah di atas stok
    // dijepit ke stok (sama seperti tombol +).
    if (qtyEditTarget != null) {
        val target = qtyEditTarget!!
        var qtyText by remember { mutableStateOf("${target.qty}") }
        var qtyError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { qtyEditTarget = null },
            title = { Text(tr("Ubah Jumlah", "Change Quantity")) },
            text = {
                Column {
                    Text(target.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(SpaceXS))
                    Text(tr("Stok tersedia: ${target.medicine.stock} ${target.medicine.unit}", "Stock available: ${target.medicine.stock} ${target.medicine.unit}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceM))
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }; qtyError = "" },
                        label = { Text(tr("Jumlah", "Quantity")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (qtyError.isNotEmpty()) {
                        Spacer(Modifier.height(SpaceXS))
                        Text(qtyError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = qtyText.toIntOrNull()
                        if (v == null) {
                            qtyError = tr("Masukkan angka yang valid.", "Enter a valid number.")
                        } else {
                            val clamped = minOf(v, target.medicine.stock)
                            if (clamped <= 0) cart.remove(target) else target.qty = clamped
                            qtyEditTarget = null
                        }
                    }
                ) { Text(tr("Simpan", "Save"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { qtyEditTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

// ---------- Stok Obat ----------

@Composable
fun StokScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("az") } // az | za | low (stok terendah)
    // Mode pilih banyak (hapus massal): kartu menampilkan checkbox, ketuk kartu
    // untuk centang; tombol Hapus muncul di bar seleksi. Disimpan sebagai
    // Map id -> obat (bukan cuma id) supaya hapus massal + Urungkan tetap
    // benar walau daftar berubah karena pencarian/filter selama memilih.
    var selectMode by remember { mutableStateOf(false) }
    var selectedMeds by remember { mutableStateOf<Map<Int, Medicine>>(emptyMap()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var msg by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Medicine?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var listEntered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importSummary by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    fun reload() { scope.launch { (repo.listMedicines(search) as? ApiResult.Success)?.let { medicines = it.data.medicines } } }
    // File Excel dibuka/disimpan lewat Storage Access Framework — tanpa izin penyimpanan.
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelHelper.MIME_XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = medicines
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelHelper.templateBytes(current)) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }
            }
            msg = if (result.isSuccess) tr("Template Excel berhasil disimpan (${if (current.isEmpty()) 3 else current.size} obat).", "Excel template saved (${if (current.isEmpty()) 3 else current.size} medicines).")
            else tr("Gagal menyimpan template Excel: ", "Failed to save the Excel template: ") + (result.exceptionOrNull()?.message ?: "")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelHelper.MIME_XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = medicines
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelHelper.exportBytes(current)) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }.isSuccess
            }
            msg = if (ok) tr("Export Excel berhasil: ${current.size} obat.", "Excel export saved: ${current.size} medicines.") else tr("Gagal menyimpan export Excel.", "Failed to save the Excel export.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Tidak bisa membaca file")
                    ExcelHelper.parseImport(bytes)
                }
                val response = repo.bulkImport(BulkImportArgs(parsed.items))
                if (response is ApiResult.Success) {
                    val r = response.data
                    importSummary = buildString {
                        append(tr("${r.inserted} obat baru ditambahkan, ${r.updated} diperbarui", "${r.inserted} new medicines added, ${r.updated} updated"))
                        if (r.skipped > 0) append(tr(", ${r.skipped} baris dilewati (nama kosong)", ", ${r.skipped} rows skipped (empty name)"))
                        if (parsed.skippedRows.isNotEmpty()) append(tr("\nBaris dilewati: ${parsed.skippedRows.joinToString(", ")}", "\nRows skipped: ${parsed.skippedRows.joinToString(", ")}"))
                        append(tr("\nTotal baris diproses: ${r.total}", "\nTotal rows processed: ${r.total}"))
                    }
                    showImportDialog = true
                    reload()
                } else {
                    msg = tr("Import gagal: ", "Import failed: ") + ((response as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                }
            } catch (e: Exception) {
                msg = tr("Import gagal: ", "Import failed: ") + (e.message ?: "")
            }
        }
    }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listState = rememberLazyListState()
    // FAB Tambah Obat: sembunyi saat daftar digulir ke bawah, muncul lagi
    // saat digulir ke atas (permintaan Achmad 2026-10-31).
    var fabVisible by remember { mutableStateOf(true) }
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                fabVisible = when {
                    index == 0 && offset == 0 -> true
                    index > lastScrollIndex -> false
                    index < lastScrollIndex -> true
                    offset > lastScrollOffset + 8 -> false
                    offset < lastScrollOffset - 8 -> true
                    else -> fabVisible
                }
                lastScrollIndex = index
                lastScrollOffset = offset
            }
    }
    // Muat awal + setiap kali layar kembali tampil (resumeKey): langsung
    // tanpa menunggu. Dulu muat awal ikut antre di debounce pencarian di
    // bawah, jadi menu Stok terasa delay saat dibuka (perbaikan 2026-11-01).
    LaunchedEffect(resumeKey) { reload() }
    // Pencarian di-debounce: satu reload 300ms setelah ketikan terakhir,
    // supaya coroutine pencarian lama tidak saling mendahului hasil terbaru.
    // Jalan pertama dilewati karena muat awal sudah ditangani efek di atas.
    var searchEffectReady by remember { mutableStateOf(false) }
    LaunchedEffect(search) {
        if (!searchEffectReady) { searchEffectReady = true; return@LaunchedEffect }
        delay(300)
        reload()
    }
    // Daftar muncul bertahap begitu data pertama kali tiba; tidak berulang saat scroll.
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    // Ringkasan + filter status stok (Aman >20, Sedang ==20, Rendah <20, Habis 0).
    val countAman = medicines.count { stockLabel(it.stock) == "Aman" }
    val countSedang = medicines.count { stockLabel(it.stock) == "Sedang" }
    val countRendah = medicines.count { stockLabel(it.stock) == "Rendah" }
    val countHabis = medicines.count { stockLabel(it.stock) == "Habis" }
    val shown = when (statusFilter) {
        "aman" -> medicines.filter { stockLabel(it.stock) == "Aman" }
        "sedang" -> medicines.filter { stockLabel(it.stock) == "Sedang" }
        "rendah" -> medicines.filter { stockLabel(it.stock) == "Rendah" }
        "habis" -> medicines.filter { stockLabel(it.stock) == "Habis" }
        else -> medicines
    }
    val sorted = when (sortMode) {
        "za" -> shown.sortedByDescending { it.name.lowercase() }
        // Stok terendah dulu (habis/menipis paling atas); nama A-Z sebagai
        // penyeimbang supaya urutan stabil bila stoknya sama.
        "low" -> shown.sortedWith(compareBy({ it.stock }, { it.name.lowercase() }))
        else -> shown.sortedBy { it.name.lowercase() }
    }
    Box(Modifier.fillMaxSize()) {
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(CardGap),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
    ) {
        item {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Medication, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Stok Obat", "Medicine Stock"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(tr("${medicines.size} jenis obat tercatat", "${medicines.size} medicines recorded"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            SearchField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth()
            )
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(SpaceS))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(SpaceL))
            // Kartu Import / Export / Template Excel — isi banyak obat sekaligus.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(Icons.Default.TableChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import / Export Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(tr("Isi banyak obat sekaligus lewat file .xlsx", "Add many medicines at once via an .xlsx file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(SpaceM))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { templateLauncher.launch("Template-Import-Stok-Obat.xlsx") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Template", maxLines = 1)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf(ExcelHelper.MIME_XLSX)) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
                                exportLauncher.launch("Stok-Obat-$stamp.xlsx")
                            },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            // Ringkasan stok: total jenis, menipis (Rendah), habis.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${medicines.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(tr(" jenis obat", " medicines"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countRendah", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                Text(tr(" menipis", " low"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countHabis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(tr(" habis", " out"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                // Tombol Pilih/Batal (mode pilih banyak) + tombol urut 3 mode
                // (A-Z -> Z-A -> Terendah -> kembali ke A-Z).
                OutlinedButton(
                    onClick = {
                        if (selectMode) { selectMode = false; selectedMeds = emptyMap() }
                        else selectMode = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(if (selectMode) Icons.Default.Close else Icons.Default.Checklist, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selectMode) tr("Batal", "Cancel") else tr("Pilih", "Select"), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        sortMode = when (sortMode) {
                            "az" -> "za"
                            "za" -> "low"
                            else -> "az"
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (sortMode) {
                            "za" -> "Z-A"
                            "low" -> tr("Terendah", "Lowest")
                            else -> "A-Z"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(SpaceS))
            // Filter status stok (menggantikan legenda; jumlah per status terlihat di chip).
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "all" to tr("Semua (${medicines.size})", "All (${medicines.size})"),
                    "aman" to tr("Aman ($countAman)", "Safe ($countAman)"),
                    "sedang" to tr("Sedang ($countSedang)", "Medium ($countSedang)"),
                    "rendah" to tr("Rendah ($countRendah)", "Low ($countRendah)"),
                    "habis" to tr("Habis ($countHabis)", "Out ($countHabis)")
                ).forEach { (k, l) ->
                    FilterChip(selected = statusFilter == k, onClick = { statusFilter = k }, label = { Text(l) })
                }
            }
            // Bar seleksi (hanya di mode pilih banyak): jumlah terpilih + Hapus massal.
            if (selectMode) {
                Spacer(Modifier.height(SpaceS))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (selectedMeds.isEmpty()) tr("Ketuk kartu obat untuk memilih.", "Tap a medicine card to select.")
                        else tr("${selectedMeds.size} obat dipilih", "${selectedMeds.size} medicines selected"),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { showBulkDeleteConfirm = true },
                        enabled = selectedMeds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Hapus (${selectedMeds.size})", "Delete (${selectedMeds.size})"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(SpaceXS))
            }
        }
        if (sorted.isEmpty()) {
            item {
                Text(
                    when {
                        medicines.isEmpty() -> tr("Belum ada obat. Ketuk \"Tambah Obat\" atau Import Excel untuk mengisi stok.", "No medicines yet. Tap \"Add Medicine\" or Import Excel to fill the stock.")
                        statusFilter == "habis" -> tr("Tidak ada obat yang habis.", "No medicines are out of stock.")
                        statusFilter == "rendah" -> tr("Tidak ada obat yang stoknya rendah.", "No medicines have low stock.")
                        statusFilter == "sedang" -> tr("Tidak ada obat yang stoknya sedang.", "No medicines have medium stock.")
                        statusFilter == "aman" -> tr("Tidak ada obat yang stoknya aman.", "No medicines have safe stock.")
                        else -> tr("Tidak ada obat yang cocok.", "No matching medicines.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        }
        itemsIndexed(sorted) { index, m ->
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            ExpressiveCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().then(
                    if (selectMode) Modifier.clickable {
                        selectedMeds = if (selectedMeds.containsKey(m.id)) selectedMeds - m.id else selectedMeds + (m.id to m)
                    } else Modifier
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Di mode pilih banyak: checkbox ganti tombol Edit/Hapus,
                    // dan ketuk kartu = centang/batal centang.
                    if (selectMode) {
                        Checkbox(
                            checked = selectedMeds.containsKey(m.id),
                            onCheckedChange = {
                                selectedMeds = if (selectedMeds.containsKey(m.id)) selectedMeds - m.id else selectedMeds + (m.id to m)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            StockBadge(m.stock, showCount = false)
                        }
                        Spacer(Modifier.height(SpaceXS))
                        Text(
                            tr("${m.category} • ${m.unit} • Jual ${formatIDR(m.sellPrice)} • Beli ${formatIDR(m.buyPrice)}", "${m.category} • ${m.unit} • Sell ${formatIDR(m.sellPrice)} • Buy ${formatIDR(m.buyPrice)}") +
                                if (m.expiryDate != null) " • Exp ${formatDateID(m.expiryDate)}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${m.stock}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (m.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(m.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!selectMode) {
                        IconButton(onClick = { editing = m; showForm = true }) { Icon(Icons.Default.Edit, tr("Edit obat", "Edit medicine")) }
                        IconButton(onClick = { deleteTarget = m }) {
                            Icon(Icons.Default.Delete, tr("Hapus obat", "Delete medicine"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            }
        }
    }
        // FAB Tambah Obat: melayang di kanan bawah; naik sedikit saat
        // Snackbar tampil supaya tombol aksinya (mis. Urungkan) tidak
        // tertutup.
        val fabBottomPadding by animateDpAsState(
            targetValue = if (snackbarHostState.currentSnackbarData != null) 84.dp else 16.dp,
            label = "fabBottomPadding"
        )
        AnimatedVisibility(
            visible = fabVisible,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = fabBottomPadding),
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(250)) { it / 2 },
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(250)) { it / 2 }
        ) {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("Tambah Obat", "Add Medicine"), style = MaterialTheme.typography.titleMedium) }
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (showForm) MedicineFormDialog(editing = editing, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
    if (showImportDialog && importSummary != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(tr("Import Excel Selesai", "Excel Import Complete")) },
            text = { Text(importSummary!!) },
            confirmButton = { TextButton(onClick = { showImportDialog = false }) { Text("OK") } }
        )
    }
    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Hapus Obat?", "Delete Medicine?")) },
            text = { Text(tr("Hapus ${target.name} dari stok?", "Remove ${target.name} from stock?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            repo.deleteMedicine(target.id)
                            reload()
                            val result = snackbarHostState.showSnackbar(
                                message = tr("Obat ${target.name} dihapus.", "${target.name} deleted."),
                                actionLabel = tr("Urungkan", "Undo"),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                repo.createMedicine(
                                    MedicineArgs(
                                        target.name, target.category, target.unit,
                                        target.buyPrice, target.sellPrice, target.stock,
                                        target.expiryDate, target.barcode,
                                        wholesalePrice = target.wholesalePrice
                                    )
                                )
                                reload()
                                msg = "Obat ${target.name} dikembalikan."
                            }
                        }
                    }
                ) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    // Konfirmasi hapus massal: hapus semua yang terpilih, lalu Snackbar
    // "Urungkan" membuat ulang obat-obat itu (pola yang sama seperti hapus satuan).
    if (showBulkDeleteConfirm) {
        val count = selectedMeds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(tr("Hapus $count Obat?", "Delete $count Medicines?")) },
            text = { Text(tr("Hapus $count obat yang dipilih dari stok?", "Remove the $count selected medicines from stock?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirm = false
                        val toDelete = selectedMeds.values.toList()
                        selectMode = false
                        selectedMeds = emptyMap()
                        scope.launch {
                            toDelete.forEach { repo.deleteMedicine(it.id) }
                            reload()
                            val result = snackbarHostState.showSnackbar(
                                message = tr("${toDelete.size} obat dihapus.", "${toDelete.size} medicines deleted."),
                                actionLabel = tr("Urungkan", "Undo"),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                toDelete.forEach {
                                    repo.createMedicine(
                                        MedicineArgs(
                                            it.name, it.category, it.unit,
                                            it.buyPrice, it.sellPrice, it.stock,
                                            it.expiryDate, it.barcode,
                                            wholesalePrice = it.wholesalePrice
                                        )
                                    )
                                }
                                reload()
                                msg = "${toDelete.size} obat dikembalikan."
                            }
                        }
                    }
                ) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

// Pemilih opsi berupa bottom sheet dengan daftar radio (dipakai Kategori & Satuan di form Stok).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(options) { opt ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(opt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = opt == selected, onClick = { onSelect(opt) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun MedicineFormDialog(editing: Medicine?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var category by remember(editing) { mutableStateOf(editing?.category ?: "Umum") }
    var unit by remember(editing) { mutableStateOf(editing?.unit ?: "Strip") }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showUnitSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Ketukan pada field read-only ditangkap lewat interactionSource:
    // Modifier.clickable di OutlinedTextField tidak jalan karena field
    // menelan ketukan untuk fokus. Pola ini sama seperti dropdown bawaan.
    val categoryInteraction = remember { MutableInteractionSource() }
    val unitInteraction = remember { MutableInteractionSource() }
    val expiryInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(categoryInteraction) {
        categoryInteraction.interactions.collect { if (it is PressInteraction.Press) showCategorySheet = true }
    }
    LaunchedEffect(unitInteraction) {
        unitInteraction.interactions.collect { if (it is PressInteraction.Press) showUnitSheet = true }
    }
    LaunchedEffect(expiryInteraction) {
        expiryInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    var buy by remember(editing) { mutableStateOf(TextFieldValue(formatDecimal(editing?.buyPrice?.toString() ?: ""))) }
    var sell by remember(editing) { mutableStateOf(TextFieldValue(formatDecimal(editing?.sellPrice?.toString() ?: ""))) }
    // Harga jual grosir: boleh kosong (= belum diatur, nilai 0); ditampilkan
    // kosong bila 0 supaya tidak tertukar dengan harga beneran.
    var grosir by remember(editing) { mutableStateOf(TextFieldValue(formatDecimal(editing?.wholesalePrice?.takeIf { it > 0 }?.toString() ?: ""))) }
    var stock by remember(editing) { mutableStateOf(editing?.stock?.toString() ?: "") }
    var expiry by remember(editing) { mutableStateOf(editing?.expiryDate ?: "") }
    var formError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet.
        sheetMaxWidth = adaptiveSheetMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) tr("Tambah Obat", "Add Medicine") else tr("Edit Obat", "Edit Medicine"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedTextField(name, { name = it }, label = { Text(tr("Nama Obat", "Medicine Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = category, onValueChange = {}, label = { Text(tr("Kategori", "Category")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih kategori") },
                interactionSource = categoryInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = unit, onValueChange = {}, label = { Text(tr("Satuan", "Unit")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = tr("Pilih satuan", "Pick a unit")) },
                interactionSource = unitInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(buy, { buy = formatDecimalFieldValue(it) }, label = { Text(tr("Harga Beli", "Buy Price")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sell, { sell = formatDecimalFieldValue(it) }, label = { Text(tr("Harga Jual", "Sell Price")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(grosir, { grosir = formatDecimalFieldValue(it) }, label = { Text(tr("Harga Jual Grosir (boleh kosong)", "Wholesale Sell Price (optional)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(stock, { stock = it.filter { c -> c.isDigit() } }, label = { Text(tr("Stok", "Stock")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = if (expiry.isBlank()) "" else formatDateID(expiry),
                onValueChange = {},
                label = { Text(tr("Tanggal Kadaluarsa (boleh kosong)", "Expiry Date (optional)")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                interactionSource = expiryInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceS))
            if (formError.isNotEmpty()) {
                Text(formError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f).height(52.dp)) { Text(tr("Batal", "Cancel")) }
                Button(
                    onClick = {
                        scope.launch {
                            val cat = category.ifBlank { "Umum" }
                            val unt = unit.ifBlank { "Strip" }
                            val b = parseDecimal(buy.text)
                            val s = parseDecimal(sell.text)
                            val st = stock.toIntOrNull() ?: 0
                            val exp = expiry.ifBlank { null }
                            // Barcode tidak lagi diisi lewat formulir (kolomnya
                            // dihapus atas permintaan Achmad 2026-10-29); saat edit,
                            // barcode lama (mis. dari impor Excel) dipertahankan
                            // supaya pencocokan impor berikutnya tidak rusak.
                            val bc = editing?.barcode
                            val result = if (editing == null) {
                                repo.createMedicine(MedicineArgs(name, cat, unt, b, s, st, exp, bc, wholesalePrice = parseDecimal(grosir.text)))
                            } else {
                                repo.updateMedicine(UpdateMedicineArgs(editing.id, name, cat, unt, b, s, st, exp, bc, wholesalePrice = parseDecimal(grosir.text)))
                            }
                            if (result is ApiResult.Success) {
                                onSaved()
                            } else {
                                formError = (result as? ApiResult.Error)?.message ?: tr("Gagal menyimpan obat.", "Failed to save the medicine.")
                            }
                        }
                    },
                    enabled = name.isNotBlank() && parseDecimal(buy.text) > 0 && parseDecimal(sell.text) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(tr("Simpan", "Save")) }
            }
        }
    }
    if (showDatePicker) {
        // Tanggal dinormalisasi ke tengah malam UTC supaya DatePicker tidak
        // menggeser tanggal 1 hari (parseISODate memakai timezone HP).
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(expiry))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { expiry = dayKeyID(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { expiry = ""; showDatePicker = false }) { Text(tr("Hapus", "Clear")) }
                TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) }
            }
        ) {
            // DatePicker bertumpuk kalau tinggi layar terbatas (lanskap):
            // bungkus dengan scroll vertikal supaya DatePicker mengukur tinggi
            // alaminya dan dialog bisa digulir, bukan memampatkan isinya.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
    if (showCategorySheet) {
        // Nilai lama yang tidak ada di daftar tetap ditampilkan di atas agar tidak hilang.
        val options = if (category in CATEGORIES) CATEGORIES else listOf(category) + CATEGORIES
        OptionPickerSheet(title = tr("Kategori", "Category"), options = options, selected = category, onSelect = { category = it; showCategorySheet = false }, onDismiss = { showCategorySheet = false })
    }
    if (showUnitSheet) {
        val options = if (unit in UNITS) UNITS else listOf(unit) + UNITS
        OptionPickerSheet(title = tr("Satuan", "Unit"), options = options, selected = unit, onSelect = { unit = it; showUnitSheet = false }, onDismiss = { showUnitSheet = false })
    }
}

// ---------- Keuangan ----------

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(entries) { if (entries.isNotEmpty() && !listEntered) listEntered = true }
    var cashBalance by remember { mutableStateOf<CashBalance?>(null) }
    var period by remember { mutableStateOf("today") } // today | month | all — awalnya "Hari Ini" (permintaan Achmad 2026-10-31), bukan "Semua"
    var typeFilter by remember { mutableStateOf("all") } // all | in | out
    var showForm by remember { mutableStateOf(false) }
    var formType by remember { mutableStateOf("in") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            (repo.listCashEntries() as? ApiResult.Success)?.let { entries = it.data.entries }
            // Saldo dihitung dari SEMUA entri (bukan 200 terbaru saja).
            (repo.getCashBalance() as? ApiResult.Success)?.let { cashBalance = it.data }
        }
    }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) { reload() }
    val todayKey = dayKeyID(Date())
    val monthKey = monthKeyID(Date())
    val periodEntries = entries.filter { e ->
        val d = parseISODate(e.createdAt)
        when (period) {
            "today" -> d != null && dayKeyID(d) == todayKey
            "month" -> d != null && monthKeyID(d) == monthKey
            else -> true
        }
    }
    val masuk = periodEntries.filter { it.type == "in" }.sumOf { it.amount }
    val keluar = periodEntries.filter { it.type == "out" }.sumOf { it.amount }
    val shown = periodEntries.filter { typeFilter == "all" || it.type == typeFilter }
    // Saldo hero memakai total agregat (semua entri); bila gagal dimuat, pakai
    // jumlah periode terpilih sebagai fallback supaya layar tetap menampilkan angka.
    val saldoIn = cashBalance?.totalIn ?: masuk
    val saldoOut = cashBalance?.totalOut ?: keluar
    val saldo = cashBalance?.balance ?: (masuk - keluar)
    // Arus kas mengikuti periode yang dipilih, kecuali "Hari Ini" yang tetap
    // menampilkan 7 hari terakhir supaya jelas (1 batang saja di tengah kartu
    // terlihat kosong dan tidak memberi konteks tren).
    val jakartaTz = TimeZone.getTimeZone("Asia/Jakarta")
    val cal = Calendar.getInstance(jakartaTz).apply {
        time = Date()
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayStartMillis = cal.timeInMillis
    val dayCount = when (period) {
        // "today" -> 7 hari terakhir (bukan 1 hari saja); "Bulan Ini"/"Semua"
        // tetap mengikuti periode.
        "today" -> 7
        "month" -> cal.get(Calendar.DAY_OF_MONTH)
        else -> {
            // "all": dari hari entri pertama sampai hari ini (maks 365 hari).
            val firstDay = entries.mapNotNull { parseISODate(it.createdAt) }.minOfOrNull { it.time }
                ?.let { t ->
                    Calendar.getInstance(jakartaTz).apply {
                        time = Date(t)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } ?: todayStartMillis
            (((todayStartMillis - firstDay) / (24L * 60 * 60 * 1000)).toInt() + 1).coerceIn(1, 365)
        }
    }
    val dailyFlow = (dayCount - 1 downTo 0).map { back ->
        val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
        val key = dayKeyID(c.time)
        // Label: nama hari ("Sen") bila grafik pendek (<= 7 batang, tiap hari
        // muncul sekali); tanggal ("27 Okt") bila grafik panjang supaya tidak ambigu.
        val label = if (dayCount <= 7) {
            SimpleDateFormat("EEE", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(c.time)
        } else {
            SimpleDateFormat("d MMM", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(c.time)
        }
        val dayEntries = entries.filter { e -> parseISODate(e.createdAt)?.let { dayKeyID(it) == key } == true }
        Triple(label, dayEntries.filter { it.type == "in" }.sumOf { it.amount }, dayEntries.filter { it.type == "out" }.sumOf { it.amount })
    }
    val periodIn = dailyFlow.sumOf { it.second }
    val periodOut = dailyFlow.sumOf { it.third }
    val maxFlow = dailyFlow.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1) ?: 1
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Keuangan", "Finance"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf("today" to tr("Hari Ini", "Today"), "month" to tr("Bulan Ini", "This Month"), "all" to tr("Semua", "All")).forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(SpaceL))
            Button(
                onClick = { formType = "in"; showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Catat Kas", "Record Cash"), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(SpaceL))
            // Kartu Saldo Kas — warna gradasi biru di-revert ke kartu datar seperti semula;
            // bar proporsi + compact dipertahankan.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(tr("SALDO KAS", "CASH BALANCE"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(SpaceS))
                    AnimatedAmount(saldo, MaterialTheme.typography.headlineLarge, if (saldo < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(SpaceS))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SouthWest, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("MASUK", "IN"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatIDR(saldoIn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NorthEast, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("KELUAR", "OUT"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatIDR(saldoOut), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Bar proporsi Masuk (biru) vs Keluar (merah) dari total keseluruhan — tipis (compact).
                    Spacer(Modifier.height(SpaceS))
                    val totalFlow = saldoIn + saldoOut
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                        if (totalFlow > 0) {
                            if (saldoIn > 0) Box(Modifier.weight(saldoIn.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                            if (saldoOut > 0) Box(Modifier.weight(saldoOut.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.error))
                        }
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            // Grafik arus kas — judul + isi mengikuti periode yang dipilih.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val chartTitle = when (period) {
                        "today" -> tr("Arus Kas 7 Hari Terakhir", "Cash Flow - Last 7 Days")
                        "month" -> tr("Arus Kas Bulan Ini", "Cash Flow - This Month")
                        else -> tr("Arus Kas Semua", "Cash Flow - All Time")
                    }
                    Text(chartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(SpaceS))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Masuk", "In"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Keluar", "Out"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            tr("Masuk Rp ${formatCompactIDR(periodIn)} • Keluar Rp ${formatCompactIDR(periodOut)}", "In Rp ${formatCompactIDR(periodIn)} • Out Rp ${formatCompactIDR(periodOut)}"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(SpaceL))
                    if (periodIn + periodOut == 0) {
                        Text(
                            when (period) {
                                "today" -> tr("Belum ada arus kas 7 hari terakhir.", "No cash flow in the last 7 days.")
                                "month" -> tr("Belum ada arus kas bulan ini.", "No cash flow this month.")
                                else -> tr("Belum ada arus kas.", "No cash flow yet.")
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // <= 7 batang memenuhi lebar kartu; lebih dari itu bisa
                        // digulir horizontal (kolom tetap 44dp) dan otomatis
                        // terbuka di ujung terbaru (kanan).
                        val scrollable = dailyFlow.size > 7
                        val chartScroll = rememberScrollState()
                        if (scrollable) {
                            LaunchedEffect(period, dailyFlow.size) {
                                withFrameNanos { }
                                chartScroll.scrollTo(chartScroll.maxValue)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(chartScroll) else Modifier),
                            horizontalArrangement = Arrangement.spacedBy(SpaceXS),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            dailyFlow.forEachIndexed { barIndex, (label, dayIn, dayOut) ->
                                val isToday = barIndex == dailyFlow.lastIndex
                                Column(
                                    modifier = if (scrollable) Modifier.width(44.dp) else Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                                        key(period, periodIn, periodOut) {
                                            val grow = remember { Animatable(0f) }
                                            // Jepit jeda animasi supaya grafik panjang
                                            // tidak butuh waktu lama untuk selesai tumbuh.
                                            LaunchedEffect(period, periodIn, periodOut) { grow.animateTo(1f, tween(durationMillis = 600, delayMillis = (barIndex * 70).coerceAtMost(500), easing = FastOutSlowInEasing)) }
                                            val inH = ((110f * dayIn / maxFlow * grow.value).coerceIn(0f, 110f)).let { if (dayIn > 0) it.coerceAtLeast(4f) else 0f }
                                            val outH = ((110f * dayOut / maxFlow * grow.value).coerceIn(0f, 110f)).let { if (dayOut > 0) it.coerceAtLeast(4f) else 0f }
                                            Box(Modifier.width(10.dp).height(inH.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                            Box(Modifier.width(10.dp).height(outH.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                        }
                                    }
                                    Spacer(Modifier.height(SpaceS))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(SpaceS))
                        Text(
                            if (scrollable) tr("Geser untuk melihat hari lain • Biru = masuk • Merah = keluar • Label hari ini ditebalkan", "Scroll to see other days • Blue = in • Red = out • Today's label is bold")
                            else tr("Biru = masuk • Merah = keluar • Label hari ini ditebalkan", "Blue = in • Red = out • Today's label is bold"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf("all" to tr("Semua", "All"), "in" to tr("Masuk", "In"), "out" to tr("Keluar", "Out")).forEach { (k, l) ->
                    FilterChip(selected = typeFilter == k, onClick = { typeFilter = k }, label = { Text(l) })
                }
            }
            }
        }
        itemsIndexed(shown) { index, e ->
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            val isIn = e.type == "in"
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White, shape = CircleShape) {
                        Icon(
                            if (isIn) Icons.Default.SouthWest else Icons.Default.NorthEast, null,
                            tint = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.category} • ${formatDateTimeID(e.createdAt)} • ${if (e.source == "sale") tr("Otomatis", "Automatic") else "Manual"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        (if (isIn) "+ " else "- ") + formatIDR(e.amount),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            }
        }
    }
    if (showForm) CashEntryFormSheet(type = formType, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
}

@Composable
private fun CashEntryFormSheet(type: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    // Jenis bisa diganti dari dalam form lewat pil geser Masuk/Keluar (gaya
    // pemilih metode pembayaran di Kasir); parameter type = pilihan awal.
    var entryType by remember { mutableStateOf(type) }
    val isIn = entryType == "in"
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(TextFieldValue("")) }
    // Tanggal entri (WIB), default hari ini; hanya dikirim ke repo bila user
    // mengubahnya — bila tidak, repo memakai waktu sekarang (createdAt = null).
    var entryDate by remember { mutableStateOf(dayKeyID(Date())) }
    var dateChanged by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    val dateInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dateInteraction) {
        dateInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet.
        sheetMaxWidth = adaptiveSheetMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Catat Kas", "Record Cash"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Pilihan Masuk/Keluar: SingleChoiceSegmentedButtonRow resmi
            // Material 3 (menggantikan pil meluncur rakitan manual). Warna
            // aktif tetap biru tua (Masuk) / merah tua (Keluar) pilihan
            // Achmad, dengan teks putih.
            val kasTypes = listOf(
                Triple("in", tr("Kas Masuk", "Cash In"), Icons.Default.SouthWest),
                Triple("out", tr("Kas Keluar", "Cash Out"), Icons.Default.NorthEast)
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kasTypes.forEachIndexed { index, (t, label, icon) ->
                    val activeColor = if (t == "in") KasMasukBlue else KasKeluarRed
                    SegmentedButton(
                        selected = entryType == t,
                        onClick = { entryType = t },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kasTypes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = activeColor,
                            activeContentColor = Color.White,
                            activeBorderColor = activeColor
                        ),
                        icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label, maxLines = 1) }
                }
            }
            OutlinedTextField(category, { category = it }, label = { Text(tr("Kategori", "Category")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(tr("Keterangan", "Description")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = formatDecimalFieldValue(it) }, label = { Text(tr("Jumlah (Rp)", "Amount (Rp)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = formatDateID(entryDate),
                onValueChange = {},
                label = { Text(tr("Tanggal", "Date")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                interactionSource = dateInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            if (formError.isNotEmpty()) {
                Text(formError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(tr("Batal", "Cancel")) }
                Button(
                    onClick = {
                        scope.launch {
                            val result = repo.createCashEntry(
                                CreateCashEntryArgs(
                                    entryType,
                                    category.ifBlank { "Lainnya" },
                                    description.ifBlank { if (isIn) "Kas masuk" else "Kas keluar" },
                                    parseDecimal(amount.text),
                                    createdAt = if (dateChanged) dayKeyToIsoUtc(entryDate) else null
                                )
                            )
                            if (result is ApiResult.Success) {
                                onSaved()
                            } else {
                                formError = (result as? ApiResult.Error)?.message ?: tr("Gagal menyimpan entri kas.", "Failed to save the cash entry.")
                            }
                        }
                    },
                    enabled = parseDecimal(amount.text) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIn) KasMasukBlue else KasKeluarRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(tr("Simpan", "Save")) }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(entryDate), selectableDates = NoFutureDates)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        entryDate = dayKeyID(Date(it))
                        dateChanged = true
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) } }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

// ---------- Laporan Laba ----------

@Composable
fun LabaScreen() {
    var report by remember { mutableStateOf<ProfitReport?>(null) }
    // Pesan kegagalan muat (audit 2026-11-02, M2): tanpa ini, kegagalan
    // laporan membuat LoadingIndicator berputar selamanya.
    var loadError by remember { mutableStateOf<String?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(report) { if (report != null && !listEntered) listEntered = true }
    var period by remember { mutableStateOf("month") }
    // Muat ulang laporan setiap kali layar Laba kembali tampil (ON_RESUME),
    // supaya penjualan terbaru dari Kasir ikut terhitung — bukan hanya saat
    // periode berubah. Sebelumnya laporan hanya dimuat saat periode berubah,
    // jadi penjualan yang dibuat setelah layar Laba dibuka tidak masuk.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(period, resumeKey) {
        when (val r = repo.getProfitReport(period)) {
            is ApiResult.Success -> { report = r.data; loadError = null }
            is ApiResult.Error -> loadError = r.message
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Laporan Laba", "Profit Report"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("today" to tr("Hari Ini", "Today"), "month" to tr("Bulan Ini", "This Month"), "last30" to tr("30 Hari", "Last 30 Days"), "all" to tr("Semua", "All")).forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
            }
            }
        }
        report?.let { r ->
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("LABA KOTOR", "GROSS PROFIT"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    "Margin ${marginTextID(r.marginPct)}%", color = Color.White,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        AnimatedAmount(r.grossProfit, MaterialTheme.typography.headlineLarge, Color.White)
                        Spacer(Modifier.height(SpaceXS))
                        Text(tr("${r.transactionCount} transaksi • ${r.itemsSold} item terjual", "${r.transactionCount} transactions • ${r.itemsSold} items sold"), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceL)) {
                        // Grid statistik adaptif: HP portrait 2 kolom, tablet/landscape 3 kolom,
                        // tablet lebar 4 kolom (satu baris).
                        val statsCols = adaptiveColumns(compact = 2, medium = 3, expanded = 4)
                        val stats = listOf(
                            Triple(Icons.Default.Receipt, tr("PENJUALAN", "REVENUE"), formatIDR(r.revenue)),
                            Triple(Icons.Default.Inventory, tr("MODAL (HPP)", "COST (COGS)"), formatIDR(r.hpp)),
                            Triple(Icons.Default.NorthEast, tr("BIAYA OPERASIONAL", "OPERATING COSTS"), formatIDR(r.operationalExpense)),
                            Triple(Icons.Default.Savings, tr("ESTIMASI LABA BERSIH", "ESTIMATED NET PROFIT"), formatIDR(r.netProfit))
                        )
                        stats.chunked(statsCols).forEach { rowStats ->
                            Row(Modifier.fillMaxWidth()) {
                                rowStats.forEach { (icon, label, value) ->
                                    LabaStat(icon, label, value, if (label == tr("ESTIMASI LABA BERSIH", "ESTIMATED NET PROFIT")) (if (r.netProfit < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else null, Modifier.weight(1f))
                                }
                                repeat(statsCols - rowStats.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                }
            }
            if (r.costMissingQty > 0) {
                item {
                    AnimatedVisibility(
                        visible = listEntered,
                        enter = fadeIn(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) +
                            slideInVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) { it / 3 } +
                            expandVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)),
                        exit = fadeOut()
                    ) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                tr("Ada ${r.costMissingQty} item terjual yang harga belinya tidak diketahui (obat sudah dihapus dari stok), jadi laba untuk item tersebut terhitung lebih besar dari seharusnya.", "${r.costMissingQty} sold items have an unknown buy price (the medicine was deleted from stock), so their profit is counted higher than it should be."),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        // Judul grafik mengikuti periode; "Hari Ini" menampilkan
                        // 7 hari terakhir (judulnya pun demikian).
                        val chartTitle = when (period) {
                            "today" -> tr("Laba Kotor 7 Hari Terakhir", "Gross Profit - Last 7 Days")
                            "month" -> tr("Laba Kotor Bulan Ini", "Gross Profit - This Month")
                            "last30" -> tr("Laba Kotor 30 Hari Terakhir", "Gross Profit - Last 30 Days")
                            else -> tr("Laba Kotor Semua", "Gross Profit - All Time")
                        }
                        Text(chartTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(SpaceL))
                        val daily = r.daily
                        if (daily.isEmpty()) {
                            Text(tr("Belum ada data laba harian.", "No daily profit data yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val maxProfit = daily.maxOf { it.profit }.coerceAtLeast(1)
                            val todayIndex = daily.lastIndex
                            // Grafik mengikuti periode: <= 7 batang memenuhi lebar
                            // kartu; lebih dari itu bisa digulir horizontal supaya
                            // tiap batang tetap terbaca (kolom tetap 44dp).
                            val scrollable = daily.size > 7
                            val chartScroll = rememberScrollState()
                            if (scrollable) {
                                // Buka grafik pada ujung terbaru (kanan) begitu
                                // periode berubah (tunggu satu frame supaya
                                // maxValue sudah terukur).
                                LaunchedEffect(period, daily.size) {
                                    withFrameNanos { }
                                    chartScroll.scrollTo(chartScroll.maxValue)
                                }
                            }
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().then(if (scrollable) Modifier.horizontalScroll(chartScroll) else Modifier),
                                    horizontalArrangement = Arrangement.spacedBy(SpaceXS),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    daily.forEachIndexed { barIndex, day ->
                                        val isMax = day.profit == maxProfit && day.profit > 0
                                        val isToday = barIndex == todayIndex
                                        Column(
                                            modifier = if (scrollable) Modifier.width(44.dp) else Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                formatCompactIDR(day.profit),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isMax) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    day.profit < 0 -> MaterialTheme.colorScheme.error
                                                    isMax -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1
                                            )
                                            Spacer(Modifier.height(SpaceXS))
                                            key(period) {
                                                val grow = remember { Animatable(0f) }
                                                // Jepit jeda animasi supaya grafik panjang
                                                // tidak butuh waktu lama untuk selesai tumbuh.
                                                LaunchedEffect(period) { grow.animateTo(1f, tween(durationMillis = 600, delayMillis = (barIndex * 70).coerceAtMost(500), easing = FastOutSlowInEasing)) }
                                                val barH = ((110f * day.profit / maxProfit * grow.value).coerceIn(0f, 110f)).let { if (day.profit > 0) it.coerceAtLeast(4f) else 0f }
                                                Box(
                                                    Modifier.width(28.dp).height(barH.dp)
                                                        .background(
                                                            if (isMax) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                                        )
                                                )
                                            }
                                            Spacer(Modifier.height(SpaceS))
                                            Text(
                                                day.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(SpaceS))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(SpaceS))
                                Text(
                                    if (scrollable) tr("Geser untuk melihat hari lain • Batang biru pekat = laba tertinggi • Label hari ini ditebalkan", "Scroll to see other days • Dark blue bar = highest profit • Today's label is bold")
                                    else tr("Batang biru pekat = laba tertinggi • Label hari ini ditebalkan", "Dark blue bar = highest profit • Today's label is bold"),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }
            }
        } ?: item {
            val err = loadError
            if (err != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Gagal memuat laporan laba.", "Failed to load the profit report."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(SpaceS))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceS))
                    Text(tr("Keluar dan buka lagi menu ini untuk mencoba kembali.", "Leave and reopen this menu to try again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@Composable
private fun LabaStat(icon: ImageVector, label: String, value: String, valueColor: Color?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(SpaceXS))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- Obat Terlaris ----------

@Composable
fun ObatTerlarisScreen() {
    var sellers by remember { mutableStateOf<List<BestSeller>?>(null) }
    // Pesan kegagalan muat (audit 2026-11-02, M2): tanpa ini, kegagalan
    // tampil sebagai daftar kosong tanpa penjelasan.
    var loadError by remember { mutableStateOf<String?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(sellers) { if (!sellers.isNullOrEmpty() && !listEntered) listEntered = true }
    var period by remember { mutableStateOf("month") }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME), supaya
    // penjualan terbaru dari Kasir ikut terhitung.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(period, resumeKey) {
        when (val r = repo.getBestSellers(period)) {
            is ApiResult.Success -> { sellers = r.data; loadError = null }
            is ApiResult.Error -> loadError = r.message
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Leaderboard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Obat Terlaris", "Best Sellers"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("today" to tr("Hari Ini", "Today"), "month" to tr("Bulan Ini", "This Month"), "last30" to tr("30 Hari", "Last 30 Days"), "all" to tr("Semua", "All")).forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
        }
        val sellersError = loadError
        if (sellers == null && sellersError != null) {
            item {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Gagal memuat data terlaris.", "Failed to load best-seller data."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(SpaceS))
                        Text(sellersError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(SpaceS))
                        Text(tr("Keluar dan buka lagi menu ini untuk mencoba kembali.", "Leave and reopen this menu to try again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        sellers?.let { list ->
            if (list.isEmpty()) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Leaderboard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Belum ada penjualan pada periode ini.", "No sales in this period yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val totalQty = list.sumOf { it.qty }
                val maxQty = list.maxOf { it.qty }.coerceAtLeast(1)
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("ITEM TERJUAL", "ITEMS SOLD"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(SpaceXS))
                                AnimatedAmount(totalQty, MaterialTheme.typography.titleLarge, Color.White) { "$it item" }
                            }
                        }
                    }
                }
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            list.forEachIndexed { index, s ->
                                AnimatedVisibility(
                                    visible = listEntered,
                                    enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                                        slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                                        expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                                    exit = fadeOut()
                                ) {
                                Column {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = CircleShape, modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                "${index + 1}",
                                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                                color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(SpaceS))
                                        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))) {
                                            Box(Modifier.fillMaxWidth(s.qty.toFloat() / maxQty).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    // Jumlah item terjual kini di kanan menggantikan
                                    // nominal omzet (permintaan Achmad 2026-11-01).
                                    Text(tr("${s.qty} item terjual", "${s.qty} items sold"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                if (index < list.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                                }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Pengingat ----------

@Composable
fun PengingatScreen(onGoStok: () -> Unit, onGoPbf: () -> Unit) {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var dueBills by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var window by remember { mutableStateOf(90) }
    var filter by remember { mutableStateOf("all") } // all | expiry | low | critical
    var listEntered by remember { mutableStateOf(false) }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME, pola yang sama
    // seperti Laba) supaya tagihan yang baru ditandai lunas di Faktur PBF
    // langsung hilang dari sini.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines }
        // Tagihan PBF: pembelian Kredit yang jatuh temponya <= 7 hari lagi
        // atau sudah lewat (pilihan Achmad 2026-10-30, saran no. 1) —
        // diurutkan dari jatuh tempo paling awal (paling mendesak di atas).
        val purchases = (repo.listPurchases() as? ApiResult.Success)?.data?.purchases.orEmpty()
        dueBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isNotBlank() && (dueDaysLeft(p.dueDate) ?: Long.MAX_VALUE) <= 7L
        }.sortedBy { it.dueDate }
    }
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    val expired = medicines.filter { (daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE) < 0 }
    val withinWindow = medicines.filter { val d = daysUntilExpiry(it.expiryDate); d != null && d >= 0 && d <= window }
    val low = medicines.filter { it.stock in 1..9 }
    val critical = medicines.filter { it.stock == 0 }
    val priority = medicines.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        m.stock < 10 || (d != null && d <= window)
    }.sortedWith(
        compareBy<Medicine> { if (it.stock == 0) 0 else 1 }
            .thenBy { daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE }
            .thenBy { it.stock }
    )
    val shown = priority.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        when (filter) {
            "expiry" -> d != null && d <= window
            "low" -> m.stock in 1..9
            "critical" -> m.stock == 0
            else -> true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Pengingat", "Reminders"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            Text(tr("MASA KADALUARSA", "EXPIRY WINDOW"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf(30, 60, 90).forEach { w ->
                    FilterChip(selected = window == w, onClick = { window = w }, label = { Text(tr("$w Hari", "$w Days")) })
                }
            }
        }
        item {
            // Ringkasan 2x2: empat kartu kecil (ikon + angka besar + label),
            // supaya tiap angka lebih menonjol dan layarnya tidak monoton.
            Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM)) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.EventBusy, "EXPIRED", "${expired.size}", if (expired.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth().padding(16.dp))
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.Schedule, "≤ $window HARI", "${withinWindow.size}", MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM)) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.Inventory, tr("RENDAH", "LOW"), "${low.size}", MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().padding(16.dp))
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.PriorityHigh, tr("HABIS", "OUT"), "${critical.size}", if (critical.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
        }
        if (dueBills.isNotEmpty()) {
            item {
                // Seksi tagihan PBF: nama PBF ditulis lengkap + nominal +
                // sisa hari (permintaan Achmad 2026-10-30 supaya informasinya
                // lengkap). Kartu bisa diketuk untuk membuka Faktur PBF.
                Text(tr("TAGIHAN PBF JATUH TEMPO", "PBF BILLS DUE"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpaceS))
                Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                    dueBills.forEach { p ->
                        val daysLeft = dueDaysLeft(p.dueDate) ?: 0L
                        val (dueLabel, dueColor) = when {
                            daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                            daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                            daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                            else -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                        }
                        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().clickable { onGoPbf() }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                        Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.pbfName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(SpaceS))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = dueColor, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Jatuh tempo ${formatDateID(p.dueDate)} • $dueLabel", "Due ${formatDateID(p.dueDate)} • $dueLabel"), style = MaterialTheme.typography.bodySmall, color = dueColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            // Istilah stok di Pengingat disamakan dengan menu Stok (keputusan
            // Achmad 2026-10-29): stok 1-9 disebut "Rendah" dan stok 0 disebut
            // "Habis" — bukan lagi "Stok Menipis"/"Kritis" — supaya informasi
            // di kedua menu konsisten. Label pendek juga membuat keempat chip
            // filter muat satu baris tanpa terpotong; scroll horizontal
            // dipertahankan sebagai cadangan untuk layar yang sangat sempit.
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("all" to tr("Semua", "All"), "expiry" to tr("Kadaluwarsa", "Expiry"), "low" to tr("Rendah", "Low"), "critical" to tr("Habis", "Out")).forEach { (k, l) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                }
            }
        }
        itemsIndexed(shown) { index, m ->
            val d = daysUntilExpiry(m.expiryDate)
            val isCritical = m.stock == 0
            // Saran restock: jumlah untuk mencapai batas Aman (16) pada skala
            // status baru; hanya ditampilkan bila sarannya > 0.
            val suggest = (16 - m.stock).coerceAtLeast(0)
            val showSuggest = suggest > 0
            // Keterangan digabung: kalau obat bebarengan bermasalah stok
            // (habis/menipis) DAN kadaluarsa, kedua keterangan ditulis supaya
            // informasinya jelas (sebelumnya hanya satu yang tampil karena
            // rantai if/else berhenti di kondisi pertama yang cocok).
            val stockNote = when {
                isCritical -> if (showSuggest) tr("Stok habis — restock segera, disarankan $suggest ${m.unit} (target stok Aman 16).", "Out of stock — restock soon, suggested $suggest ${m.unit} (Safe stock target 16).") else tr("Stok habis — restock segera.", "Out of stock — restock soon.")
                m.stock in 1..9 -> if (showSuggest) tr("Stok rendah — disarankan restock $suggest ${m.unit} (target stok Aman 16).", "Low stock — suggested restock $suggest ${m.unit} (Safe stock target 16).") else tr("Stok rendah — restock segera.", "Low stock — restock soon.")
                else -> null
            }
            val expiryNote = when {
                d != null && d < 0 -> tr("Sudah kadaluarsa — jangan dijual, segera pisahkan dari stok.", "Expired — do not sell it, separate it from stock right away.")
                d != null -> tr("Kedaluwarsa dalam $d hari — prioritaskan penjualan.", "Expires in $d days — prioritize selling it.")
                else -> null
            }
            val advice = listOfNotNull(stockNote, expiryNote).joinToString(" ")
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(
                                if (isCritical) Icons.Default.ShoppingCart else Icons.Default.Inventory, null,
                                tint = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = if (isCritical) Modifier.pulseKritis() else Modifier) {
                                    Text(
                                        if (isCritical) tr("HABIS", "OUT") else tr("PANTAU", "WATCH"),
                                        color = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                "${m.category} • Stok ${m.stock} ${m.unit} • ${stockLabel(m.stock)}" +
                                    if (m.expiryDate != null) {
                                        " • Exp ${formatDateID(m.expiryDate)}" + if (d != null) {
                                            if (d < 0) tr(" • Lewat ${-d} hari", " • ${-d} days overdue") else tr(" • Sisa $d hari", " • $d days left")
                                        } else ""
                                    } else " • Tanpa tanggal kadaluarsa",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onGoStok, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Inventory, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Stok", "Stock"))
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            advice,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (index < shown.size - 1) {
                Spacer(Modifier.height(SpaceM))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            }
            }
        }
        if (shown.isEmpty()) {
            item { Text(tr("Tidak ada pengingat — semua stok dan kadaluarsa aman.", "No reminders — all stock and expiry dates are safe."), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Text(
                tr("Jumlah restock adalah saran otomatis (target stok Aman, 16). Untuk mengubah stok atau tanggal kadaluarsa, buka obatnya di menu Stok.", "Restock amounts are automatic suggestions (Safe stock target, 16). To change stock or an expiry date, open the medicine in the Stock menu."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderCount(icon: ImageVector, label: String, count: String, countColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = countColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(SpaceXS))
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = countColor)
    }
}

// ---------- Invoice detail (struk) ----------
// Struk putih solid seperti di web app, supaya tetap terbaca di atas latar gelap
// dan enak kalau dicetak/di-screenshot.
private val InvoicePaper = Color.White
private val InvoiceInk = Color(0xFF111111)
private val InvoiceMuted = Color(0xFF6B7280)
private val InvoiceBoxBg = Color(0xFFF4F4F5)
private val InvoiceBlue = Color(0xFF0061A4)
private val InvoicePaidBg = Color(0xFFE6F4EA)
private val InvoicePaidFg = Color(0xFF137333)
private val InvoiceDash = Color(0xFFD1D5DB)

@Composable
private fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = InvoiceDash,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
    }
}

@Composable
private fun InvoiceInfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = InvoiceMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(SpaceXS))
            Text(value, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Pratinjau struk sebelum bayar — tampil di sheet keranjang supaya kasir bisa
// cek isi, total, metode, dan kembalian sebelum pembayaran diproses.
// Formatnya mengikuti kartu struk asli (InvoiceDetailCard) tapi lebih ringkas:
// tanpa nomor invoice & tanggal (belum ada penjualan), dengan lencana PRATINJAU.
@Composable
private fun ReceiptPreviewCard(
    items: List<CartItem>,
    total: Int,
    method: String,
    paid: Int,
    change: Int,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InvoicePaper),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "APOTEK LESTARI", color = InvoiceBlue,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceXS))
            Text(
                "Pratinjau Struk", color = InvoiceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "PRATINJAU", color = InvoiceMuted,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(SpaceL))
            DashedDivider()
            Spacer(Modifier.height(SpaceL))
            Text("RINCIAN OBAT", color = InvoiceMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(SpaceS))
            items.forEachIndexed { index, ci ->
                if (index > 0) DashedDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(color = InvoiceBlue.copy(alpha = 0.12f), shape = CircleShape) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = InvoiceBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ci.medicine.name, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (ci.medicine.category.isNotBlank() || ci.medicine.unit.isNotBlank()) {
                            Text("${ci.medicine.category} • ${ci.medicine.unit}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${ci.qty} × ${formatIDR(ci.unitPrice)} • ${if (ci.useWholesale && ci.medicine.wholesalePrice > 0) "Grosir" else "Ecer"}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatIDR(ci.unitPrice * ci.qty), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(SpaceL))
            Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total (${items.size} item)", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIDR(total), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Metode Bayar", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(method, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (method == "Tunai") {
                        Spacer(Modifier.height(SpaceS))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dibayar", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIDR(paid), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (change > 0) {
                            Spacer(Modifier.height(SpaceS))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(formatIDR(change), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    HorizontalDivider(color = InvoiceDash)
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Bayar", color = InvoiceInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(total), color = InvoiceBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Text(
                "Semoga Cepat Sembuh",
                color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Terima Kasih Atas Kunjungan Anda",
                color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Text(
                "Pratinjau — struk resmi dibuat setelah pembayaran berhasil.",
                color = InvoiceMuted, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InvoiceDetailCard(sale: Sale, modifier: Modifier = Modifier, graphicsLayer: GraphicsLayer? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InvoicePaper),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth().let {
            if (graphicsLayer != null) it.drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            } else it
        }
    ) {
        Column(Modifier.padding(20.dp)) {
            val logo = rememberLogoBitmap()
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = "Logo Apotek Lestari",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(SpaceS))
            }
            Text(
                "APOTEK LESTARI", color = InvoiceBlue,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceXS))
            Text(
                "Invoice Penjualan", color = InvoiceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = InvoicePaidBg, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = InvoicePaidFg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LUNAS", color = InvoicePaidFg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            DashedDivider()
            Spacer(Modifier.height(SpaceL))
            InvoiceInfoBox("NO. INVOICE", sale.invoiceNo, Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpaceS))
            InvoiceInfoBox("TANGGAL", formatFullDateID(sale.createdAt), Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                InvoiceInfoBox("METODE BAYAR", sale.paymentMethod, Modifier.weight(1f))
                InvoiceInfoBox("ISI INVOICE", "${sale.items.size} item", Modifier.weight(1f))
            }
            Spacer(Modifier.height(SpaceL))
            Text("RINCIAN OBAT", color = InvoiceMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(SpaceS))
            sale.items.forEachIndexed { index, it ->
                if (index > 0) DashedDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(color = InvoiceBlue.copy(alpha = 0.12f), shape = CircleShape) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = InvoiceBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.medicineName, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (it.category.isNotBlank() || it.unit.isNotBlank()) {
                            Text("${it.category} • ${it.unit}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${it.qty} × ${formatIDR(it.price)} • ${if (it.priceType == "GROSIR") "Grosir" else "Ecer"}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatIDR(it.subtotal), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(SpaceL))
            Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal (${sale.items.size} item)", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIDR(sale.total), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (sale.paymentMethod == "Tunai") {
                        Spacer(Modifier.height(SpaceS))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dibayar", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIDR(sale.amountPaid), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sale.changeAmount > 0) {
                            Spacer(Modifier.height(SpaceS))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(formatIDR(sale.changeAmount), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    HorizontalDivider(color = InvoiceDash)
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Bayar", color = InvoiceInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(sale.total), color = InvoiceBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Text(
                "Semoga Cepat Sembuh",
                color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Terima Kasih Atas Kunjungan Anda",
                color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Tombol di bawah struk: tangkap kartu invoice jadi gambar, lalu bagikan atau cetak.
// Tombolnya sengaja di luar kartu struk supaya tidak ikut masuk ke gambar struk.
@Composable
private fun ReceiptActionsRow(graphicsLayer: GraphicsLayer, invoiceNo: String, sale: Sale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Cetak struk ke printer thermal Bluetooth (ESC/POS).
    var showPrinterPicker by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BtPrinter>>(emptyList()) }
    var savedPrinter by remember { mutableStateOf(loadSavedPrinter(context)) }

    fun doPrint(printer: BtPrinter) {
        scope.launch {
            val result = printReceiptToPrinter(context, printer.address, buildReceiptBytes(sale))
            result.onSuccess {
                Toast.makeText(context, "Struk terkirim ke ${printer.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "Gagal mencetak: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Izin diberikan — ulangi alur cetak dari awal.
            if (!isBluetoothEnabled(context)) {
                Toast.makeText(context, "Nyalakan Bluetooth dulu", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val saved = loadSavedPrinter(context)
            if (saved != null) {
                doPrint(saved)
            } else {
                val paired = getPairedPrinters(context)
                if (paired.isEmpty()) {
                    Toast.makeText(context, "Tidak ada printer Bluetooth yang terpasang. Pasangkan printer di Pengaturan Bluetooth HP dulu.", Toast.LENGTH_LONG).show()
                } else {
                    pairedPrinters = paired
                    showPrinterPicker = true
                }
            }
        } else {
            Toast.makeText(context, "Izin Bluetooth ditolak — aktifkan di Pengaturan HP", Toast.LENGTH_LONG).show()
        }
    }

    fun startBluetoothPrint() {
        // Android 12+ butuh izin runtime BLUETOOTH_CONNECT untuk terhubung.
        if (!hasBluetoothPermission(context)) {
            btPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        if (!isBluetoothEnabled(context)) {
            Toast.makeText(context, "Nyalakan Bluetooth dulu", Toast.LENGTH_SHORT).show()
            return
        }
        // Printer sudah pernah dipilih: langsung cetak ke printer itu.
        val saved = loadSavedPrinter(context)
        if (saved != null) {
            doPrint(saved)
            return
        }
        // Belum ada printer tersimpan: tampilkan daftar perangkat terpasang.
        val paired = getPairedPrinters(context)
        if (paired.isEmpty()) {
            Toast.makeText(context, "Tidak ada printer Bluetooth yang terpasang. Pasangkan printer di Pengaturan Bluetooth HP dulu.", Toast.LENGTH_LONG).show()
            return
        }
        pairedPrinters = paired
        showPrinterPicker = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            shareReceiptBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap(), invoiceNo)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal menyiapkan struk", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bagikan")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            printReceiptBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap(), invoiceNo)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal mencetak struk", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cetak")
            }
        }
        Spacer(Modifier.height(SpaceS))
        Button(
            onClick = { startBluetoothPrint() },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cetak Struk Bluetooth")
        }
        // Printer yang tersimpan + opsi ganti (supaya bisa pindah printer).
        savedPrinter?.let { sp ->
            Spacer(Modifier.height(SpaceXS))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Printer: ${sp.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val paired = getPairedPrinters(context)
                    if (paired.isEmpty()) {
                        Toast.makeText(context, "Tidak ada printer Bluetooth yang terpasang.", Toast.LENGTH_SHORT).show()
                    } else {
                        pairedPrinters = paired
                        showPrinterPicker = true
                    }
                }) { Text("Ganti") }
            }
        }
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text("Pilih Printer Bluetooth") },
            text = {
                Column {
                    pairedPrinters.forEachIndexed { index, printer ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                savePrinter(context, printer)
                                savedPrinter = printer
                                showPrinterPicker = false
                                doPrint(printer)
                            }.padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(printer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(printer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (savedPrinter?.address == printer.address) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    Text(
                        "Pasangkan printer dulu di Pengaturan Bluetooth HP kalau belum muncul di daftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPicker = false }) { Text("Batal") }
            }
        )
    }
}

// ---------- Riwayat Penjualan ----------



@Composable
fun RiwayatScreen() {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    var salesLimit by remember { mutableIntStateOf(50) }
    var hasMore by remember { mutableStateOf(false) }
    val receiptLayer = rememberGraphicsLayer()
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(salesLimit, resumeKey) {
        (repo.listSales(salesLimit) as? ApiResult.Success)?.let {
            sales = it.data.sales
            // Hasil penuh (= limit) berarti kemungkinan masih ada penjualan lama.
            hasMore = it.data.sales.size >= salesLimit
        }
    }
    LaunchedEffect(sales) { if (sales.isNotEmpty() && !listEntered) listEntered = true }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Riwayat Penjualan", "Sales History"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        DemoBanner()
        if (sales.isEmpty()) Text(tr("Belum ada penjualan tercatat.", "No sales recorded yet."))
        // Kelompokkan penjualan per hari (WIB): header tanggal + total omzet
        // harian, supaya riwayat lebih mudah dipindai daripada daftar datar.
        // dayKeyID/parseISODate sudah dipakai layar Keuangan untuk batas WIB.
        val jakartaTz = TimeZone.getTimeZone("Asia/Jakarta")
        val todayCal = Calendar.getInstance(jakartaTz).apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayKey = dayKeyID(todayCal.time)
        val yesterdayKey = dayKeyID((todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        val groupedSales = sales.groupBy { s -> parseISODate(s.createdAt)?.let { dayKeyID(it) } ?: "" }
        // Urutkan grup dari hari terbaru (pakai createdAt terbesar di grup).
        val sortedGroups = groupedSales.entries.sortedByDescending { (_, list) ->
            list.maxOfOrNull { parseISODate(it.createdAt)?.time ?: 0L } ?: 0L
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceM)) {
            var offset = 0
            sortedGroups.forEach { (dayKey, daySales) ->
                val groupOffset = offset
                offset += daySales.size
                // Header tanggal: label hari + jumlah transaksi & total omzet hari itu.
                item(key = "header-$dayKey") {
                    val dayDate = daySales.firstNotNullOfOrNull { parseISODate(it.createdAt) }
                    val label = when (dayKey) {
                        todayKey -> tr("Hari ini", "Today")
                        yesterdayKey -> tr("Kemarin", "Yesterday")
                        else -> dayDate?.let {
                            SimpleDateFormat("d MMM yyyy", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(it)
                        } ?: tr("Tanpa tanggal", "No date")
                    }
                    val dayTotal = daySales.sumOf { it.total }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${daySales.size} transaksi • ${formatIDR(dayTotal)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(daySales) { index, s ->
                    val globalIndex = groupOffset + index
                    val isOpen = expanded == s.id
                    AnimatedVisibility(
                        visible = listEntered,
                        enter = fadeIn(animationSpec = tween(300, entranceDelay(globalIndex), FastOutSlowInEasing)) +
                            slideInVertically(animationSpec = tween(300, entranceDelay(globalIndex), FastOutSlowInEasing)) { it / 3 } +
                            expandVertically(animationSpec = tween(300, entranceDelay(globalIndex), FastOutSlowInEasing)),
                        exit = fadeOut()
                    ) {
                    ExpressiveCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)).clickable { expanded = if (isOpen) null else s.id }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                    Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${formatDateTimeID(s.createdAt)} • ${s.items.size} item",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Chip cara bayar di tengah antara nomor invoice
                                // dan nominal, seperti Faktur PBF — hanya untuk
                                // QRIS dan Transfer; Tunai tanpa chip (permintaan
                                // Achmad 2026-11-01).
                                if (s.paymentMethod != "Tunai") {
                                    Spacer(Modifier.width(8.dp))
                                    SalePaymentChip(s.paymentMethod)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(formatIDR(s.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Icon(
                                    if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(SpaceL))
                                InvoiceDetailCard(sale = s, graphicsLayer = receiptLayer)
                                Spacer(Modifier.height(SpaceM))
                                ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = s.invoiceNo, sale = s)
                            }
                        }
                    }
                    }
                }
            }
        }
        if (hasMore) {
            Spacer(Modifier.height(SpaceM))
            OutlinedButton(
                onClick = { salesLimit += 50 },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(tr("Tampilkan lebih banyak", "Show more")) }
        }
    }
}






// ---------- Backup & Restore ----------

@Composable
fun BackupScreen() {
    var msg by remember { mutableStateOf("") }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Backup: pilih lokasi simpan, lalu tulis JSON hasil export.
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = repo.exportBackup()
            if (result is ApiResult.Success) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(result.data.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                    }.isSuccess
                }
                msg = if (ok) "Backup berhasil disimpan." else "Gagal menyimpan backup."
            } else {
                msg = "Backup gagal: ${(result as? ApiResult.Error)?.message ?: "tidak diketahui"}"
            }
        }
    }
    // Restore: pilih file backup JSON, lalu ganti seluruh data dengan isinya.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("Tidak bisa membaca file")
                }
                val result = repo.importBackup(json)
                msg = if (result is ApiResult.Success) "Restore berhasil. ${result.data}" else "Restore gagal: ${(result as? ApiResult.Error)?.message ?: "tidak diketahui"}"
            } catch (e: Exception) {
                msg = "Restore gagal: ${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Backup & Restore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Simpan dan pulihkan semua data apotek.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Kenapa backup itu penting?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(SpaceS))
                Text(
                    "Semua data (obat, penjualan, kas) hanya tersimpan di HP ini. Kalau HP hilang, rusak, atau aplikasi terhapus, data tidak bisa dikembalikan. Simpan file backup di tempat aman (mis. Google Drive atau laptop) secara berkala.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Backup Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Simpan salinan semua data ke file .json", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(SpaceM))
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
                        backupLauncher.launch("backup-apotek-lestari-$stamp.json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Backup Data") }
            }
        }
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Restore Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pulihkan data dari file backup .json", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(SpaceS))
                Text(
                    "Perhatian: restore akan MENGGANTI semua data saat ini dengan isi file backup.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(SpaceM))
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Restore Data") }
            }
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(SpaceL))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Pulihkan data dari backup?") },
            text = { Text("Semua data saat ini (obat, penjualan, kas) akan diganti dengan isi file backup. Pastikan kamu memilih file backup yang benar.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoreLauncher.launch(arrayOf("application/json"))
                }) { Text("Pilih File") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Batal") } }
        )
    }
}

/** Tentang Aplikasi: logo, nama, versi, dan deskripsi singkat. */
@Composable
fun TentangScreen() {
    // Versi hardcoded — samakan dengan versionName di build.gradle.kts.
    // (BuildConfig tidak digenerate di setup AGP proyek ini, jadi jangan pakai BuildConfig.VERSION_NAME.)
    val version = "1.0.0"
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Tentang Aplikasi", "About App"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            // Logo + nama + versi, di tengah.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val logo = rememberLogoBitmap()
                if (logo != null) {
                    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(88.dp)) {
                        Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp).fillMaxSize())
                    }
                }
                Spacer(Modifier.height(SpaceM))
                Text("Apotek Lestari", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(SpaceXS))
                Text("Versi $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Aplikasi kasir dan manajemen stok obat untuk Apotek Lestari. Data obat, penjualan, dan kas tersimpan di perangkat.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Versi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(version, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dikembangkan untuk", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Apotek Lestari", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ---------- PBF (catatan pembelian dari PBF — data saja) ----------
// Keputusan Achmad 2026-10-29: menu "PBF" berisi riwayat pembelian obat
// dari PBF, MURNI catatan — tidak mengubah stok, tidak membuat entri kas,
// tidak mengubah harga beli. Ringkasan di atas menjawab "hari ini belanja
// di PBF mana saja dan uang keluar hari ini berapa".

private class PurchaseItemRow {
    var name by mutableStateOf("")
    var unit by mutableStateOf("Strip")
    var qty by mutableStateOf("")
    var price by mutableStateOf(TextFieldValue(""))
}

@Composable
fun PbfScreen() {
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(purchases) { if (purchases.isNotEmpty() && !listEntered) listEntered = true }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Purchase?>(null) }
    var paidTarget by remember { mutableStateOf<Purchase?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Muat ulang setiap kali layar kembali tampil (ON_RESUME), pola yang
    // sama seperti Obat Terlaris.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        (repo.listPurchases() as? ApiResult.Success)?.let { purchases = it.data.purchases }
    }

    // Tanggal pembelian disimpan sebagai kunci hari "yyyy-MM-dd", jadi
    // pengelompokan per hari cukup membandingkan string (tanpa konversi).
    val todayKey = dayKeyID(Date())
    val yesterdayKey = dayKeyID(Date(System.currentTimeMillis() - 86_400_000L))
    val monthPrefix = todayKey.substring(0, 7)
    // Ringkasan belanja hanya menghitung pembelian CASH (permintaan Achmad
    // 2026-10-30): pembelian Kredit belum masuk total Hari Ini/Bulan Ini.
    // Daftar riwayat di bawah tetap menampilkan semuanya.
    val todayPurchases = purchases.filter { it.date == todayKey && it.paymentType == "CASH" }
    val todayTotal = todayPurchases.sumOf { it.total }
    val monthTotal = purchases.filter { it.date.startsWith(monthPrefix) && it.paymentType == "CASH" }.sumOf { it.total }
    val sortedGroups = purchases.groupBy { it.date }.entries.sortedByDescending { it.key }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(SpaceL)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Faktur PBF", "PBF Invoices"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(SpaceL))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(SpaceL))
                DemoBanner()
                // Ringkasan belanja hari ini + bulan ini (kartu biru muda,
                // pola yang sama seperti ringkasan Obat Terlaris).
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("BELANJA HARI INI", "TODAY'S PURCHASES"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceXS))
                            AnimatedAmount(todayTotal, MaterialTheme.typography.titleLarge, Color.White)
                            Text(tr("${todayPurchases.size} pembelian", "${todayPurchases.size} purchases"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(tr("BELANJA BULAN INI", "THIS MONTH'S PURCHASES"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceXS))
                            AnimatedAmount(monthTotal, MaterialTheme.typography.titleLarge, Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(SpaceL))
                Button(
                    onClick = { showForm = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Catat Pembelian", "Record Purchase"))
                }
            }
            if (purchases.isEmpty()) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Belum ada catatan pembelian. Catat belanja dari PBF lewat tombol + Catat Pembelian — ini murni catatan, tidak mengubah stok dan kas.", "No purchase records yet. Record PBF purchases with the + Record Purchase button — these are records only and do not change stock or cash."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            sortedGroups.forEach { (day, dayPurchases) ->
                item(key = "pbf-header-$day") {
                    val label = when (day) {
                        todayKey -> tr("Hari ini", "Today")
                        yesterdayKey -> tr("Kemarin", "Yesterday")
                        else -> formatDateID(day)
                    }
                    val dayTotal = dayPurchases.sumOf { it.total }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("${dayPurchases.size} pembelian • ${formatIDR(dayTotal)}", "${dayPurchases.size} purchases • ${formatIDR(dayTotal)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(dayPurchases, key = { _, item -> "pbf-${item.id}" }) { index, p ->
                    AnimatedVisibility(
                        visible = listEntered,
                        enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                            slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                            expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                        exit = fadeOut()
                    ) {
                    val isOpen = expanded == p.id
                    ExpressiveCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)).clickable { expanded = if (isOpen) null else p.id }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.pbfName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val namesPreview = p.items.take(3).joinToString(", ") { it.medicineName }
                                    val suffix = if (p.items.size > 3) " +${p.items.size - 3} lagi" else ""
                                    Text(tr("${p.items.size} obat • $namesPreview$suffix", "${p.items.size} medicines • $namesPreview$suffix"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                // Chip penanda cara bayar, di tengah antara nama
                                // PBF dan nominal (permintaan Achmad 2026-10-30).
                                PurchasePaymentChip(p.paymentType)
                                Spacer(Modifier.width(10.dp))
                                Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Info jatuh tempo untuk pembelian Kredit yang
                            // tanggalnya diisi (permintaan Achmad 2026-10-30):
                            // tanggal + sisa hari. Merah bila sudah lewat /
                            // hari ini, oranye bila <= 7 hari, abu bila masih
                            // lama. Hilang sendiri setelah Tandai Lunas
                            // (paymentType berubah jadi CASH).
                            if (p.paymentType == "KREDIT" && p.dueDate.isNotBlank()) {
                                val daysLeft = dueDaysLeft(p.dueDate)
                                if (daysLeft != null) {
                                    val (dueLabel, dueColor) = when {
                                        daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                                        daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                                        daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                                        daysLeft <= 7L -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                                        else -> "$daysLeft hari lagi" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        Icon(Icons.Default.Schedule, null, tint = dueColor, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("Jatuh tempo ${formatDateID(p.dueDate)} • $dueLabel", "Due ${formatDateID(p.dueDate)} • $dueLabel"), style = MaterialTheme.typography.bodySmall, color = dueColor)
                                    }
                                }
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(SpaceM))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(SpaceM))
                                if (p.invoiceNo.isNotBlank()) {
                                    Text(tr("No. Faktur: ${p.invoiceNo}", "Invoice No.: ${p.invoiceNo}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(SpaceS))
                                }
                                p.items.forEach { pItem ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(pItem.medicineName, style = MaterialTheme.typography.bodyMedium)
                                            Text("${pItem.qty} ${pItem.unit} × ${formatIDR(pItem.buyPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(formatIDR(pItem.subtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (p.ppn > 0) {
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("PPN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Text(formatIDR(p.ppn), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.height(SpaceS))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (p.paymentType == "KREDIT") Arrangement.SpaceBetween else Arrangement.End) {
                                    // Tandai Lunas hanya untuk pembelian Kredit:
                                    // mengubahnya jadi Cash supaya masuk ringkasan.
                                    if (p.paymentType == "KREDIT") {
                                        TextButton(onClick = { paidTarget = p }) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("Tandai Lunas", "Mark as Paid"), color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    TextButton(onClick = { deleteTarget = p }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showForm) {
        PurchaseFormSheet(
            onDismiss = { showForm = false },
            onSaved = {
                showForm = false
                resumeKey++
                scope.launch { snackbarHostState.showSnackbar(tr("Pembelian tercatat.", "Purchase recorded.")) }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Hapus Catatan Pembelian?", "Delete Purchase Record?")) },
            text = { Text(tr("Catatan belanja dari ${target.pbfName} (${formatDateID(target.date)}) sebesar ${formatIDR(target.total)} akan dihapus. Ini hanya menghapus catatan — stok dan kas tidak berubah.", "The purchase record from ${target.pbfName} (${formatDateID(target.date)}) of ${formatIDR(target.total)} will be deleted. This only deletes the record — stock and cash are unchanged.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val res = repo.deletePurchase(target.id)
                        if (res is ApiResult.Success) {
                            if (expanded == target.id) expanded = null
                            resumeKey++
                            snackbarHostState.showSnackbar(tr("Catatan pembelian dihapus.", "Purchase record deleted."))
                        } else {
                            snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menghapus", "Failed to delete"))
                        }
                    }
                }) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }

    paidTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { paidTarget = null },
            title = { Text(tr("Tandai Lunas?", "Mark as Paid?")) },
            text = { Text(tr("Pembelian dari ${target.pbfName} (${formatDateID(target.date)}) sebesar ${formatIDR(target.total)} akan diubah jadi Cash. Nominalnya masuk ke ringkasan Belanja Hari Ini/Bulan Ini mengikuti tanggal belinya.", "The purchase from ${target.pbfName} (${formatDateID(target.date)}) of ${formatIDR(target.total)} will be changed to Cash. Its amount joins the Today/This Month Purchases summary following its purchase date.")) },
            confirmButton = {
                TextButton(onClick = {
                    paidTarget = null
                    scope.launch {
                        val res = repo.markPurchasePaid(target.id)
                        if (res is ApiResult.Success) {
                            resumeKey++
                            snackbarHostState.showSnackbar(tr("Pembelian ditandai lunas.", "Purchase marked as paid."))
                        } else {
                            snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menandai lunas", "Failed to mark as paid"))
                        }
                    }
                }) { Text(tr("Tandai Lunas", "Mark as Paid"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { paidTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

@Composable
private fun PurchaseFormSheet(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var pbfName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var ppn by remember { mutableStateOf(TextFieldValue("")) }
    var paymentType by remember { mutableStateOf("CASH") }
    var dueDate by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(dayKeyID(Date())) }
    var rows by remember { mutableStateOf(listOf(PurchaseItemRow())) }
    var activeRow by remember { mutableStateOf<PurchaseItemRow?>(null) }
    var unitPickerRow by remember { mutableStateOf<PurchaseItemRow?>(null) }
    var meds by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Saran nama obat dari data Stok (hanya saran ketik — memilihnya TIDAK
    // mengubah stok obat yang bersangkutan). Memilih saran juga mengisi
    // satuan baris ini dengan satuan obat itu di Stok.
    LaunchedEffect(Unit) {
        (repo.listMedicines() as? ApiResult.Success)?.let { res -> meds = res.data.medicines }
    }

    // Field tanggal read-only: ketukan ditangkap lewat interactionSource
    // (pola yang sama seperti field Kadaluwarsa di formulir obat).
    val dateInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dateInteraction) {
        dateInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    // Field Tanggal Jatuh Tempo: pola read-only + interactionSource yang sama.
    val dueInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dueInteraction) {
        dueInteraction.interactions.collect { if (it is PressInteraction.Press) showDuePicker = true }
    }

    // Total Belanja = subtotal semua obat + PPN (PPN diketik sebagai nominal
    // rupiah; kosong = 0).
    val itemsTotal = rows.sumOf { (it.qty.toIntOrNull() ?: 0) * parseDecimal(it.price.text) }
    val total = itemsTotal + parseDecimal(ppn.text)
    val valid = pbfName.isNotBlank() && rows.all {
        it.name.isNotBlank() && (it.qty.toIntOrNull() ?: 0) > 0 && parseDecimal(it.price.text) > 0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = adaptiveSheetMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Catat Pembelian PBF", "Record PBF Purchase"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedTextField(pbfName, { pbfName = it }, label = { Text(tr("Faktur PBF", "PBF / Distributor")) }, placeholder = { Text(tr("mis. nama distributornya", "e.g. the distributor's name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(invoiceNo, { invoiceNo = it }, label = { Text(tr("Nomor Faktur (boleh kosong)", "Invoice Number (optional)")) }, placeholder = { Text(tr("mis. nomor faktur dari PBF", "e.g. the invoice number from the PBF")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = formatDateID(date), onValueChange = {}, label = { Text(tr("Tanggal Beli", "Purchase Date")) },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                interactionSource = dateInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Pembayaran", "Payment"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PriceModeChip("Cash", selected = paymentType == "CASH") { paymentType = "CASH" }
                Spacer(Modifier.width(6.dp))
                PriceModeChip(tr("Kredit", "Credit"), selected = paymentType == "KREDIT") { paymentType = "KREDIT" }
            }
            // Tanggal jatuh tempo hanya untuk pembelian Kredit (permintaan
            // Achmad 2026-10-30); boleh kosong — tanpa tanggal, kartu tidak
            // menampilkan hitung mundur. Untuk Cash field ini tidak tampil.
            if (paymentType == "KREDIT") {
                OutlinedTextField(
                    value = if (dueDate.isBlank()) "" else formatDateID(dueDate), onValueChange = {}, label = { Text(tr("Tanggal Jatuh Tempo (boleh kosong)", "Due Date (optional)")) },
                    placeholder = { Text(tr("Pilih tanggal", "Pick a date")) },
                    readOnly = true, singleLine = true,
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal jatuh tempo", "Pick the due date")) },
                    interactionSource = dueInteraction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(SpaceXS))
            Text(tr("Obat yang Dibeli", "Purchased Medicines"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            rows.forEachIndexed { index, row ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Obat ${index + 1}", "Medicine ${index + 1}"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (rows.size > 1) {
                            IconButton(
                                onClick = { rows = rows - row; if (activeRow == row) activeRow = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, tr("Hapus obat ini", "Delete this item"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Pemilih satuan per baris: ketukan ditangkap lewat
                    // interactionSource baris ini (pola field read-only yang
                    // sama seperti Tanggal Beli), membuka OptionPickerSheet
                    // untuk baris yang bersangkutan. Sumber + efek DIKUNCI ke
                    // objek baris (audit N3, 2026-10-30): tanpa kunci, slot
                    // remember bergeser setelah baris lain dihapus dan
                    // ketukan bisa membuka pemilih untuk baris yang salah.
                    val unitInteraction = remember(row) { MutableInteractionSource() }
                    LaunchedEffect(row, unitInteraction) {
                        unitInteraction.interactions.collect { if (it is PressInteraction.Press) unitPickerRow = row }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { row.name = it; activeRow = row },
                            label = { Text(tr("Nama Obat", "Medicine Name")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = row.unit, onValueChange = {}, label = { Text(tr("Satuan", "Unit")) },
                            readOnly = true, singleLine = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = tr("Pilih satuan", "Pick a unit")) },
                            interactionSource = unitInteraction,
                            modifier = Modifier.width(118.dp)
                        )
                    }
                    if (activeRow == row && row.name.isNotBlank()) {
                        val suggestions = meds.filter {
                            it.name.contains(row.name.trim(), ignoreCase = true) && !it.name.equals(row.name.trim(), ignoreCase = true)
                        }.take(5)
                        if (suggestions.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                                suggestions.forEach { s ->
                                    AssistChip(onClick = { row.name = s.name; row.unit = s.unit }, label = { Text(s.name) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                        OutlinedTextField(
                            value = row.qty,
                            onValueChange = { row.qty = it.filter { c -> c.isDigit() } },
                            label = { Text(tr("Jumlah", "Quantity")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = row.price,
                            onValueChange = { row.price = formatDecimalFieldValue(it) },
                            label = { Text(tr("Harga Satuan", "Unit Price")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    val rowSubtotal = (row.qty.toIntOrNull() ?: 0) * parseDecimal(row.price.text)
                    if (rowSubtotal > 0) {
                        Text("Subtotal: ${formatIDR(rowSubtotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
                    }
                }
            }
            OutlinedButton(
                onClick = { rows = rows + PurchaseItemRow(); activeRow = null },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Tambah Obat", "Add Item"))
            }
            Spacer(Modifier.height(SpaceXS))
            OutlinedTextField(ppn, { ppn = formatDecimalFieldValue(it) }, label = { Text(tr("Jumlah PPN (Rp)", "PPN/VAT Amount (Rp)")) }, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Total Belanja", "Total Purchases"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatIDR(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            if (formError.isNotEmpty()) Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    scope.launch {
                        formError = ""
                        val res = repo.createPurchase(
                            CreatePurchaseArgs(
                                pbfName = pbfName,
                                date = date,
                                items = rows.map { CreatePurchaseItemArgs(it.name, it.qty.toIntOrNull() ?: 0, parseDecimal(it.price.text), it.unit) },
                                invoiceNo = invoiceNo,
                                ppn = parseDecimal(ppn.text),
                                paymentType = paymentType,
                                dueDate = if (paymentType == "KREDIT") dueDate else ""
                            )
                        )
                        if (res is ApiResult.Success) onSaved()
                        else formError = (res as? ApiResult.Error)?.message ?: tr("Gagal menyimpan", "Failed to save")
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Simpan", "Save")) }
        }
    }
    if (showDatePicker) {
        // Sama seperti pemilih tanggal kadaluwarsa: normalisasi ke tengah
        // malam UTC supaya tanggal tidak bergeser 1 hari.
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(date), selectableDates = NoFutureDates)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = dayKeyID(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) }
            }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
    if (showDuePicker) {
        // Pemilih tanggal jatuh tempo: pola yang sama seperti Tanggal Beli.
        // "Hapus" mengosongkan tanggal (jatuh tempo memang boleh kosong).
        val duePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(dueDate.ifBlank { date }))
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    duePickerState.selectedDateMillis?.let { dueDate = dayKeyID(Date(it)) }
                    showDuePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dueDate = ""; showDuePicker = false }) { Text(tr("Hapus", "Clear")) }
                    TextButton(onClick = { showDuePicker = false }) { Text(tr("Batal", "Cancel")) }
                }
            }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = duePickerState)
            }
        }
    }
    // Pemilih satuan untuk baris yang diketuk — daftar UNITS yang sama
    // persis dengan formulir Stok (keputusan Achmad 2026-10-29: satuan PBF
    // disamakan dengan Stok; sebelumnya sempat hanya Botol/Box).
    unitPickerRow?.let { target ->
        val options = if (target.unit in UNITS) UNITS else listOf(target.unit) + UNITS
        OptionPickerSheet(title = tr("Satuan", "Unit"), options = options, selected = target.unit, onSelect = { target.unit = it; unitPickerRow = null }, onDismiss = { unitPickerRow = null })
    }
}
