@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package id.apotek.lestari.ui.screens

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.printReceiptBitmap
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.shareReceiptBitmap
import id.apotek.lestari.ui.components.BarcodeScanner
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val repo = Repository()
val CATEGORIES = listOf("Umum","Antibiotik","Vitamin","Pereda Nyeri","Obat Batuk & Flu","Pencernaan","Kulit","Mata","Lainnya")
val UNITS = listOf("Strip","Botol","Box","Tablet","Kapsul","Tube","Sachet","Pcs")

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

// ---------- Animasi Material 3 Expressive ----------

/** Jeda kemunculan bertahap per item daftar (maks. 400ms). */
private fun entranceDelay(index: Int) = (index * 40).coerceAtMost(400)

/** Angka berhitung naik dari 0 (dipakai Pemasukan Hari Ini & Laba Kotor). */
@Composable
private fun AnimatedAmount(value: Int, style: TextStyle, color: Color) {
    var shown by remember { mutableStateOf(0) }
    val animated by animateIntAsState(
        targetValue = shown,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "amount"
    )
    LaunchedEffect(value) { shown = value }
    Text(formatIDR(animated), color = color, style = style, fontWeight = FontWeight.Bold)
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
private fun StepperButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressBounceScale(interactionSource)
    IconButton(onClick = onClick, interactionSource = interactionSource, modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
        Icon(icon, description)
    }
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
        color = MaterialTheme.colorScheme.primaryContainer,
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

// Warna aksen mengikuti tampilan web app (mode gelap)
private val LightBlueCard = Color(0xFFD2E4FF)
private val HeroGradientTop = Color(0xFFA9C9FF)
private val OnLightBlue = Color(0xFF001D36)
private val BlueButton = Color(0xFF0061A4)
private val KasKeluarRed = Color(0xFF800020)
private val GreenButton = Color(0xFF2E7D32)
private val RedBadgeBg = Color(0xFFBA1A1A)
private val AmanBg = Color(0xFF00497C)
private val AmanFg = Color(0xFFA1C9FF)
private val SedangBg = Color(0xFF4A3F00)
private val SedangFg = Color(0xFFFFD97A)
private val BurukBg = Color(0xFF601410)
private val BurukFg = Color(0xFFFFB4AB)

@Composable
fun DemoBanner() {
    // Mode offline: semua data tersimpan di HP (Room), tidak ada banner demo.
}

@Composable
private fun StockBadge(stock: Int, showCount: Boolean = true) {
    val label = stockLabel(stock)
    val bg = when (label) {
        "Aman" -> AmanBg
        "Sedang" -> SedangBg
        else -> BurukBg
    }
    val fg = when (label) {
        "Aman" -> AmanFg
        "Sedang" -> SedangFg
        else -> BurukFg
    }
    Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = if (stock <= 0) Modifier.pulseKritis() else Modifier) {
        Text(
            if (showCount) "Stok $stock • $label" else label,
            color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ---------- Beranda ----------

@Composable
fun BerandaScreen(onGoStok: () -> Unit, onGoPengingat: () -> Unit, onGoLaba: () -> Unit, onGoRiwayat: () -> Unit) {
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var salesTab by remember { mutableIntStateOf(0) }
    var outOfStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var expiringMeds by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        (repo.getDashboard() as? ApiResult.Success)?.let { dashboard = it.data }
        val meds = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
        outOfStockNames = meds.filter { it.stock <= 0 }.map { it.name }.sorted()
        val todayKey = dayKeyID(Date())
        val in30Key = dayKeyID(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        expiringMeds = meds.filter { it.expiryDate != null && it.expiryDate >= todayKey && it.expiryDate <= in30Key }
            .sortedBy { it.expiryDate }
            .map { it.name to it.expiryDate!! }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = rememberLogoBitmap()
                if (logo != null) {
                    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                        Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp).fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text("Apotek Lestari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(todayLabelID(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Penjualan Hari Ini", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            DemoBanner()
        }
        dashboard?.let { d ->
            item {
                // Kartu Pemasukan Hari Ini — hero premium: gradasi biru + insight vs kemarin
                val diff = d.todayIn - d.yesterdayIn
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(HeroGradientTop, LightBlueCard)),
                            RoundedCornerShape(24.dp)
                        )
                        .animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("PEMASUKAN HARI INI", color = OnLightBlue.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = CircleShape) {
                                Icon(Icons.Default.TrendingUp, null, tint = OnLightBlue, modifier = Modifier.padding(6.dp).size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AnimatedAmount(d.todayIn, MaterialTheme.typography.displaySmall, OnLightBlue)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null, tint = OnLightBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${d.totalSalesToday} transaksi penjualan", color = OnLightBlue, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                diff > 0 -> {
                                    Icon(Icons.Default.TrendingUp, null, tint = GreenButton, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Naik ${formatIDR(diff)} dari kemarin", color = GreenButton, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                diff < 0 -> {
                                    Icon(Icons.Default.TrendingDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Turun ${formatIDR(-diff)} dari kemarin", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                                else -> {
                                    Text("Sama seperti kemarin", color = OnLightBlue.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                // Shortcut ke menu yang tidak lagi ada di bar bawah (Laba, Pengingat, Riwayat).
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard(label = "Laba", icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f), onClick = onGoLaba)
                    ShortcutCard(label = "Pengingat", icon = Icons.Default.NotificationsActive, modifier = Modifier.weight(1f), onClick = onGoPengingat)
                    ShortcutCard(label = "Riwayat", icon = Icons.Default.ReceiptLong, modifier = Modifier.weight(1f), onClick = onGoRiwayat)
                }
            }
            item {
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Pengingat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (d.lowStockCount > 0) Text("• ${d.lowStockCount} obat stok menipis", style = MaterialTheme.typography.bodyMedium)
                        if (outOfStockNames.isNotEmpty()) {
                            val shown = outOfStockNames.take(5).joinToString(", ")
                            val extra = if (outOfStockNames.size > 5) " +${outOfStockNames.size - 5} lainnya" else ""
                            Text("Habis: $shown$extra", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                        if (d.expiringCount > 0) Text("• ${d.expiringCount} obat dekat kadaluarsa", style = MaterialTheme.typography.bodyMedium)
                        if (expiringMeds.isNotEmpty()) {
                            val shown = expiringMeds.take(5).joinToString(", ") { (name, exp) -> "$name (${formatDateID(exp)})" }
                            val extra = if (expiringMeds.size > 5) " +${expiringMeds.size - 5} lainnya" else ""
                            Text("Kadaluarsa: $shown$extra", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (d.lowStockCount == 0 && d.expiringCount == 0) Text("Semua stok dan kadaluarsa aman.", style = MaterialTheme.typography.bodyMedium)
                        if (d.lowStockCount > 0 || d.expiringCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onGoPengingat) { Text("Lihat Pengingat") }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("${d.totalMedicines} jenis obat terdaftar di Stok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                // Kartu uang: Pengeluaran & Untung/Rugi Hari Ini sejajar, Bulan Ini di bawah.
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Pengeluaran Hari Ini", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(formatIDR(d.todayOut), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(if (d.todayProfit >= 0) "Untung Hari Ini" else "Rugi Hari Ini", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(formatIDR(d.todayProfit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (d.todayProfit >= 0) ExpressiveGreen else MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (d.monthProfit >= 0) "Untung Bulan Ini" else "Rugi Bulan Ini", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(formatIDR(d.monthProfit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (d.monthProfit >= 0) ExpressiveGreen else MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Masuk ${formatIDR(d.monthIn)} • Keluar ${formatIDR(d.monthOut)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                // Penjualan Hari Ini: Obat Terjual dan Transaksi dalam satu kartu bertab (compact).
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Obat Terjual" to 0, "Transaksi" to 1).forEach { (label, idx) ->
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
                        Spacer(Modifier.height(6.dp))
                        if (salesTab == 0) {
                        if (d.bestSellers.isEmpty()) {
                            Text("Belum ada obat terjual hari ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Belum ada penjualan hari ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
fun ShortcutCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

class CartItem(val medicine: Medicine, qty: Int) {
    var qty by mutableStateOf(qty)
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
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(
                    1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
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
                                "Cari obat…",
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
    var search by remember { mutableStateOf("") }
    var sortAz by remember { mutableStateOf(true) }
    var showScanner by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Tunai") }
    var uangDiterima by remember { mutableIntStateOf(0) }
    var payMsg by remember { mutableStateOf("") }
    var payCount by remember { mutableIntStateOf(0) }
    var lastSale by remember { mutableStateOf<Sale?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val receiptLayer = rememberGraphicsLayer()
    val cart = remember { mutableStateListOf<CartItem>() }
    val scope = rememberCoroutineScope()
    // Supaya field Uang Diterima otomatis tergulir ke atas keyboard saat diketuk.
    val amountBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    if (showScanner) BarcodeScanner(onDetected = { code ->
        showScanner = false
        scope.launch {
            val r = repo.findByBarcode(code)
            val med = (r as? ApiResult.Success)?.data?.medicine
            if (med != null) {
                val ex = cart.firstOrNull { it.medicine.id == med.id }
                if (med.stock <= 0) {
                    scanMsg = "${med.name} stok habis"
                } else if (ex != null && ex.qty >= med.stock) {
                    scanMsg = "Stok ${med.name} hanya ${med.stock} — tidak bisa ditambah lagi"
                } else {
                    lastSale = null
                    payMsg = ""
                    if (ex != null) ex.qty++ else cart.add(CartItem(med, 1))
                    scanMsg = "${med.name} ditambahkan"
                }
            } else scanMsg = "Barcode $code tidak ditemukan"
        }
    }, onClose = { showScanner = false })
    val filtered = medicines.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) || (it.barcode?.contains(search) == true) }
    val sorted = if (sortAz) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
    val total = cart.sumOf { it.medicine.sellPrice * it.qty }
    val cartQty = cart.sumOf { it.qty }
    val paid = uangDiterima
    val change = if (method == "Tunai") (paid - total).coerceAtLeast(0) else 0
    val canPay = cart.isNotEmpty() && (method != "Tunai" || paid >= total)
    Box(Modifier.fillMaxSize()) {
        // Daftar obat memenuhi layar; keranjang & pembayaran pindah ke bottom sheet.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {

        Text("Kasir", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Ketuk obat untuk menambahkan ke keranjang, lalu selesaikan pembayaran.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        DemoBanner()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SearchField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showScanner = true },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, "Scan barcode")
                Spacer(Modifier.width(8.dp))
                Text("Scan")
            }
        }
        if (scanMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(scanMsg, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} obat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SortToggle(sortAz = sortAz, onToggle = { sortAz = !sortAz })
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sorted.forEach { m ->
                val out = m.stock <= 0
                ExpressiveCard(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !out) {
                        val ex = cart.firstOrNull { it.medicine.id == m.id }
                        if (ex != null && ex.qty >= m.stock) {
                            scanMsg = "Stok ${m.name} hanya ${m.stock} — tidak bisa ditambah lagi"
                        } else {
                            lastSale = null
                            payMsg = ""
                            if (ex != null) ex.qty++ else cart.add(CartItem(m, 1))
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
                            Spacer(Modifier.height(4.dp))
                            Text("${m.category} • ${m.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(formatIDR(m.sellPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        StockBadge(m.stock)
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
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable { showSheet = true }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(cartQty) {
                        val cartPop = remember { Animatable(1.4f) }
                        LaunchedEffect(cartQty) { cartPop.animateTo(1f, expressiveSpring()) }
                        Surface(color = LightBlueCard, shape = CircleShape, modifier = Modifier.graphicsLayer(scaleX = cartPop.value, scaleY = cartPop.value)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, null, tint = OnLightBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$cartQty", color = OnLightBlue, fontWeight = FontWeight.Bold)
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
                        colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(48.dp)
                    ) { Text("Bayar", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }

    // Bottom sheet: keranjang + pembayaran (dan struk setelah bayar berhasil).
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (cart.isNotEmpty()) "Keranjang (${cart.sumOf { it.qty }})" else "Keranjang", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSheet = false }) { Icon(Icons.Default.Close, "Tutup") }
                }
                if (cart.isNotEmpty()) {
                    cart.forEachIndexed { index, ci ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(ci.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(formatIDR(ci.medicine.sellPrice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StepperButton(Icons.Default.Remove, "Kurangi") { if (ci.qty > 1) ci.qty-- else cart.remove(ci) }
                            key(ci.qty) {
                                val qtyPop = remember { Animatable(1.35f) }
                                LaunchedEffect(ci.qty) { qtyPop.animateTo(1f, expressiveSpring()) }
                                Text("${ci.qty}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp).graphicsLayer(scaleX = qtyPop.value, scaleY = qtyPop.value), textAlign = TextAlign.Center)
                            }
                            StepperButton(Icons.Default.Add, "Tambah") { if (ci.qty < ci.medicine.stock) ci.qty++ }
                            Text(formatIDR(ci.medicine.sellPrice * ci.qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                        }
                        if (index < cart.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total bayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(formatIDR(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("METODE BAYAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "Tunai" to Icons.Default.Money,
                        "QRIS" to Icons.Default.QrCode,
                        "Transfer" to Icons.Default.AccountBalance
                    ).forEach { (m, icon) ->
                        val selected = method == m
                        if (selected) {
                            Button(onClick = { method = m }, colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White), shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(m, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = { method = m }, shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(m, maxLines = 1)
                            }
                        }
                    }
                }
                if (method == "Tunai") {
                    Spacer(Modifier.height(16.dp))
                    Text("UANG DITERIMA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(20000 to "Rp20.000", 50000 to "Rp50.000", 100000 to "Rp100.000").forEach { (nominal, label) ->
                            OutlinedButton(
                                onClick = { uangDiterima += nominal },
                                shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) { Text(label, maxLines = 1) }
                        }
                        Button(
                            onClick = { uangDiterima = total },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                            shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) { Text("Uang Pas", maxLines = 1) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Ketuk nominal untuk menambah, atau Uang Pas untuk jumlah persis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = if (uangDiterima == 0) "" else formatDecimal(uangDiterima.toString()),
                        onValueChange = { uangDiterima = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                        label = { Text("cth: 50.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .bringIntoViewRequester(amountBringIntoView)
                            .onFocusEvent { if (it.isFocused) scope.launch { amountBringIntoView.bringIntoView() } }
                    )
                    AnimatedVisibility(visible = change > 0) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kembalian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(formatIDR(change), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Dibayar penuh via $method sebesar ${formatIDR(total)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                val bayarInteraction = remember { MutableInteractionSource() }
                val bayarScale = pressBounceScale(bayarInteraction)
                Button(
                    onClick = {
                        scope.launch {
                            val amt = if (method == "Tunai") paid else total
                            val result = repo.createSale(CreateSaleArgs(cart.map { CreateSaleItemArgs(it.medicine.id, it.qty) }, method, amt))
                            payMsg = "Pembayaran $method berhasil. Total ${formatIDR(total)}" + if (method == "Tunai") " • Kembalian ${formatIDR(change)}" else ""
                            payCount++
                            cart.clear()
                            uangDiterima = 0
                            val after = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
                            medicines = after
                            if (result is ApiResult.Success) {
                                val fresh = (repo.listSales() as? ApiResult.Success)?.data?.sales.orEmpty()
                                lastSale = fresh.firstOrNull { it.id == result.data.id }
                            }
                        }
                    },
                    enabled = canPay,
                    interactionSource = bayarInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).graphicsLayer(scaleX = bayarScale, scaleY = bayarScale)
                ) { Text("Bayar (${formatIDR(total)})", style = MaterialTheme.typography.titleMedium) }
                }
                AnimatedVisibility(visible = payMsg.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            key(payCount) { AnimatedCheckmark() }
                            Spacer(Modifier.width(10.dp))
                            Text(payMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        }
                    }
                }
                AnimatedVisibility(visible = lastSale != null) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        lastSale?.let {
                            InvoiceDetailCard(sale = it, graphicsLayer = receiptLayer)
                            Spacer(Modifier.height(12.dp))
                            ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = it.invoiceNo)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showSheet = false },
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Tutup") }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Stok Obat ----------

@Composable
fun StokScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var sortAz by remember { mutableStateOf(true) }
    var showScanner by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var prefillBarcode by remember { mutableStateOf("") }
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
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelHelper.templateBytes()) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }.isSuccess
            }
            msg = if (ok) "Template Excel berhasil disimpan." else "Gagal menyimpan template Excel."
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
            msg = if (ok) "Export Excel berhasil: ${current.size} obat." else "Gagal menyimpan export Excel."
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
                        append("${r.inserted} obat baru ditambahkan, ${r.updated} diperbarui")
                        if (r.skipped > 0) append(", ${r.skipped} baris dilewati (nama kosong)")
                        if (parsed.skippedRows.isNotEmpty()) append("\nBaris dilewati: ${parsed.skippedRows.joinToString(", ")}")
                        append("\nTotal baris diproses: ${r.total}")
                    }
                    showImportDialog = true
                    reload()
                } else {
                    msg = "Import gagal: ${(response as? ApiResult.Error)?.message ?: "tidak diketahui"}"
                }
            } catch (e: Exception) {
                msg = "Import gagal: ${e.message}"
            }
        }
    }
    LaunchedEffect(Unit) { reload() }
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
    val sorted = if (sortAz) shown.sortedBy { it.name.lowercase() } else shown.sortedByDescending { it.name.lowercase() }
    if (showScanner) BarcodeScanner(onDetected = { code ->
        showScanner = false
        scope.launch {
            val med = (repo.findByBarcode(code) as? ApiResult.Success)?.data?.medicine
            msg = if (med != null) "Ditemukan: ${med.name} — stok ${med.stock}" else "Barcode $code belum terdaftar — gunakan Tambah Obat"
            if (med == null) { prefillBarcode = code; editing = null; showForm = true }
        }
    }, onClose = { showScanner = false })
    Box(Modifier.fillMaxSize()) {
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stok Obat", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("${medicines.size} jenis obat tercatat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { prefillBarcode = ""; editing = null; showForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LightBlueCard, contentColor = OnLightBlue),
                    shape = RoundedCornerShape(32.dp)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Tambah Obat") }
            }
            Spacer(Modifier.height(16.dp))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SearchField(
                    value = search, onValueChange = { search = it; reload() },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { showScanner = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scan barcode stok")
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            // Kartu Import / Export / Template Excel — isi banyak obat sekaligus.
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(Icons.Default.TableChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import / Export Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Isi banyak obat sekaligus lewat file .xlsx", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
            Spacer(Modifier.height(16.dp))
            // Ringkasan stok: total jenis, menipis (Rendah), habis.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${medicines.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(" jenis obat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countRendah", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                Text(" menipis", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countHabis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(" habis", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                SortToggle(sortAz = sortAz, onToggle = { sortAz = !sortAz })
            }
            Spacer(Modifier.height(8.dp))
            // Filter status stok (menggantikan legenda; jumlah per status terlihat di chip).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "all" to "Semua (${medicines.size})",
                    "aman" to "Aman ($countAman)",
                    "sedang" to "Sedang ($countSedang)",
                    "rendah" to "Rendah ($countRendah)",
                    "habis" to "Habis ($countHabis)"
                ).forEach { (k, l) ->
                    FilterChip(selected = statusFilter == k, onClick = { statusFilter = k }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(4.dp))
            }
        }
        if (sorted.isEmpty()) {
            item {
                Text(
                    when {
                        medicines.isEmpty() -> "Belum ada obat. Ketuk \"Tambah Obat\" atau Import Excel untuk mengisi stok."
                        statusFilter == "habis" -> "Tidak ada obat yang habis."
                        statusFilter == "rendah" -> "Tidak ada obat yang stoknya rendah."
                        statusFilter == "sedang" -> "Tidak ada obat yang stoknya sedang."
                        statusFilter == "aman" -> "Tidak ada obat yang stoknya aman."
                        else -> "Tidak ada obat yang cocok."
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
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            StockBadge(m.stock, showCount = false)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${m.category} • ${m.unit} • Jual ${formatIDR(m.sellPrice)} • Beli ${formatIDR(m.buyPrice)}" +
                                if (m.expiryDate != null) " • Exp ${formatDateID(m.expiryDate)}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${m.stock}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (m.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(m.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { prefillBarcode = ""; editing = m; showForm = true }) { Icon(Icons.Default.Edit, "Edit obat") }
                    IconButton(onClick = { deleteTarget = m }) {
                        Icon(Icons.Default.Delete, "Hapus obat", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            }
        }
    }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (showForm) MedicineFormDialog(editing = editing, initialBarcode = prefillBarcode, onDismiss = { showForm = false; prefillBarcode = "" }, onSaved = { showForm = false; prefillBarcode = ""; reload() })
    if (showImportDialog && importSummary != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Excel Selesai") },
            text = { Text(importSummary!!) },
            confirmButton = { TextButton(onClick = { showImportDialog = false }) { Text("OK") } }
        )
    }
    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus Obat?") },
            text = { Text("Hapus ${target.name} dari stok?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            repo.deleteMedicine(target.id)
                            reload()
                            val result = snackbarHostState.showSnackbar(
                                message = "Obat ${target.name} dihapus.",
                                actionLabel = "Urungkan",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                repo.createMedicine(
                                    MedicineArgs(
                                        target.name, target.category, target.unit,
                                        target.buyPrice, target.sellPrice, target.stock,
                                        target.minStock, target.expiryDate, target.barcode
                                    )
                                )
                                reload()
                                msg = "Obat ${target.name} dikembalikan."
                            }
                        }
                    }
                ) { Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }
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
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(options) { opt ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }.padding(horizontal = 24.dp, vertical = 14.dp)
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
private fun MedicineFormDialog(editing: Medicine?, initialBarcode: String = "", onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var barcode by remember(editing, initialBarcode) { mutableStateOf(editing?.barcode ?: initialBarcode) }
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
    var buy by remember(editing) { mutableStateOf(formatDecimal(editing?.buyPrice?.toString() ?: "")) }
    var sell by remember(editing) { mutableStateOf(formatDecimal(editing?.sellPrice?.toString() ?: "")) }
    var stock by remember(editing) { mutableStateOf(editing?.stock?.toString() ?: "") }
    var minStock by remember(editing) { mutableStateOf(editing?.minStock?.toString() ?: "10") }
    var expiry by remember(editing) { mutableStateOf(editing?.expiryDate ?: "") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) "Tambah Obat" else "Edit Obat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Nama Obat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (boleh kosong)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = category, onValueChange = {}, label = { Text("Kategori") },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih kategori") },
                interactionSource = categoryInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = unit, onValueChange = {}, label = { Text("Satuan") },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih satuan") },
                interactionSource = unitInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(buy, { buy = formatDecimal(it) }, label = { Text("Harga Beli") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sell, { sell = formatDecimal(it) }, label = { Text("Harga Jual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(stock, { stock = it.filter { c -> c.isDigit() } }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minStock, { minStock = it.filter { c -> c.isDigit() } }, label = { Text("Stok Minimum") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = if (expiry.isBlank()) "" else formatDateID(expiry),
                onValueChange = {},
                label = { Text("Tanggal Kadaluarsa (boleh kosong)") },
                readOnly = true, singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih tanggal") },
                interactionSource = expiryInteraction,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f).height(52.dp)) { Text("Batal") }
                Button(
                    onClick = {
                        scope.launch {
                            val cat = category.ifBlank { "Umum" }
                            val unt = unit.ifBlank { "Strip" }
                            val b = parseDecimal(buy)
                            val s = parseDecimal(sell)
                            val st = stock.toIntOrNull() ?: 0
                            val ms = minStock.toIntOrNull() ?: 10
                            val exp = expiry.ifBlank { null }
                            val bc = barcode.ifBlank { null }
                            if (editing == null) {
                                repo.createMedicine(MedicineArgs(name, cat, unt, b, s, st, ms, exp, bc))
                            } else {
                                repo.updateMedicine(UpdateMedicineArgs(editing.id, name, cat, unt, b, s, st, ms, exp, bc))
                            }
                            onSaved()
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Simpan") }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = parseISODate(expiry)?.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { expiry = dayKeyID(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { expiry = ""; showDatePicker = false }) { Text("Hapus") }
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showCategorySheet) {
        // Nilai lama yang tidak ada di daftar tetap ditampilkan di atas agar tidak hilang.
        val options = if (category in CATEGORIES) CATEGORIES else listOf(category) + CATEGORIES
        OptionPickerSheet(title = "Kategori", options = options, selected = category, onSelect = { category = it; showCategorySheet = false }, onDismiss = { showCategorySheet = false })
    }
    if (showUnitSheet) {
        val options = if (unit in UNITS) UNITS else listOf(unit) + UNITS
        OptionPickerSheet(title = "Satuan", options = options, selected = unit, onSelect = { unit = it; showUnitSheet = false }, onDismiss = { showUnitSheet = false })
    }
}

// ---------- Keuangan ----------

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    var period by remember { mutableStateOf("all") } // today | month | all
    var typeFilter by remember { mutableStateOf("all") } // all | in | out
    var showForm by remember { mutableStateOf(false) }
    var formType by remember { mutableStateOf("in") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { (repo.listCashEntries() as? ApiResult.Success)?.let { entries = it.data.entries } } }
    LaunchedEffect(Unit) { reload() }
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
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
            Text("Keuangan", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Kas masuk & keluar tercatat rinci, termasuk penjualan otomatis dari Kasir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("today" to "Hari Ini", "month" to "Bulan Ini", "all" to "Semua").forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(16.dp))
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    // Saldo Kas — angka besar (hero); merah kalau saldonya negatif.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("SALDO KAS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatIDR(masuk - keluar),
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        color = if (masuk - keluar < 0) KasKeluarRed else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SouthWest, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("MASUK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatIDR(masuk), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NorthEast, null, tint = KasKeluarRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("KELUAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatIDR(keluar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KasKeluarRed)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { formType = "in"; showForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Kas Masuk", style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { formType = "out"; showForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = KasKeluarRed, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Icon(Icons.Default.Remove, null); Spacer(Modifier.width(8.dp)); Text("Kas Keluar", style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Semua", "in" to "Masuk", "out" to "Keluar").forEach { (k, l) ->
                    FilterChip(selected = typeFilter == k, onClick = { typeFilter = k }, label = { Text(l) })
                }
            }
            }
        }
        items(shown) { e ->
            val isIn = e.type == "in"
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = if (isIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                        Icon(
                            if (isIn) Icons.Default.SouthWest else Icons.Default.NorthEast, null,
                            tint = if (isIn) MaterialTheme.colorScheme.primary else KasKeluarRed,
                            modifier = Modifier.padding(10.dp).size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.category} • ${formatDateTimeID(e.createdAt)} • ${if (e.source == "sale") "Otomatis" else "Manual"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        (if (isIn) "+ " else "- ") + formatIDR(e.amount),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (isIn) MaterialTheme.colorScheme.primary else KasKeluarRed
                    )
                }
            }
        }
    }
    if (showForm) CashEntryFormSheet(type = formType, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
}

@Composable
private fun CashEntryFormSheet(type: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val isIn = type == "in"
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isIn) "Catat Kas Masuk" else "Catat Kas Keluar",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Keterangan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = formatDecimal(it) }, label = { Text("Jumlah (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Batal") }
                Button(
                    onClick = {
                        scope.launch {
                            repo.createCashEntry(
                                CreateCashEntryArgs(
                                    type,
                                    category.ifBlank { "Lainnya" },
                                    description.ifBlank { if (isIn) "Kas masuk" else "Kas keluar" },
                                    parseDecimal(amount)
                                )
                            )
                            onSaved()
                        }
                    },
                    enabled = parseDecimal(amount) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIn) BlueButton else KasKeluarRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Simpan") }
            }
        }
    }
}

// ---------- Laporan Laba ----------

@Composable
fun LabaScreen() {
    var report by remember { mutableStateOf<ProfitReport?>(null) }
    var period by remember { mutableStateOf("month") }
    var infoExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(period) { (repo.getProfitReport(period) as? ApiResult.Success)?.let { report = it.data } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Laporan Laba", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Laba dihitung dari penjualan dikurangi harga beli (modal) obat yang terjual.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("today" to "Hari Ini", "month" to "Bulan Ini", "last30" to "30 Hari", "all" to "Semua").forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
        }
        report?.let { r ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LightBlueCard), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("LABA KOTOR", color = OnLightBlue.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    "Margin ${marginTextID(r.marginPct)}%", color = OnLightBlue,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AnimatedAmount(r.grossProfit, MaterialTheme.typography.headlineLarge, OnLightBlue)
                        Spacer(Modifier.height(4.dp))
                        Text("${r.transactionCount} transaksi • ${r.itemsSold} item terjual", color = OnLightBlue, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Grid statistik adaptif: HP portrait 2 kolom, tablet/landscape 3 kolom,
                        // tablet lebar 4 kolom (satu baris).
                        val statsCols = adaptiveColumns(compact = 2, medium = 3, expanded = 4)
                        val stats = listOf(
                            Triple(Icons.Default.Receipt, "PENJUALAN", formatIDR(r.revenue)),
                            Triple(Icons.Default.Inventory, "MODAL (HPP)", formatIDR(r.hpp)),
                            Triple(Icons.Default.NorthEast, "BIAYA OPERASIONAL", formatIDR(r.operationalExpense)),
                            Triple(Icons.Default.Savings, "ESTIMASI LABA BERSIH", formatIDR(r.netProfit))
                        )
                        stats.chunked(statsCols).forEach { rowStats ->
                            Row(Modifier.fillMaxWidth()) {
                                rowStats.forEach { (icon, label, value) ->
                                    LabaStat(icon, label, value, if (label == "ESTIMASI LABA BERSIH") MaterialTheme.colorScheme.primary else null, Modifier.weight(1f))
                                }
                                repeat(statsCols - rowStats.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item {
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { infoExpanded = !infoExpanded }
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Cara hitung laba", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(
                                if (infoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(visible = infoExpanded) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Laba kotor = penjualan − modal obat terjual (harga beli saat transaksi). Estimasi laba bersih = laba kotor − biaya operasional dari Keuangan. Kas keluar \"Beli Stok\" (${formatIDR(r.stockPurchaseExpense)}) tidak dikurangkan lagi karena modalnya sudah dihitung lewat HPP.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (r.costMissingQty > 0) {
                item {
                    ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Ada ${r.costMissingQty} item terjual yang harga belinya tidak diketahui (obat sudah dihapus dari stok), jadi laba untuk item tersebut terhitung lebih besar dari seharusnya.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            item {
                ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Laba Kotor 7 Hari Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        val daily = r.daily.takeLast(7)
                        if (daily.isEmpty()) {
                            Text("Belum ada data laba harian.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val maxProfit = daily.maxOf { it.profit }.coerceAtLeast(1)
                            val todayIndex = daily.lastIndex
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                    daily.forEachIndexed { barIndex, day ->
                                        val isMax = day.profit == maxProfit && day.profit > 0
                                        val isToday = barIndex == todayIndex
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
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
                                            Spacer(Modifier.height(4.dp))
                                            key(period) {
                                                val grow = remember { Animatable(0f) }
                                                LaunchedEffect(period) { grow.animateTo(1f, tween(durationMillis = 600, delayMillis = barIndex * 70, easing = FastOutSlowInEasing)) }
                                                val barH = ((110f * day.profit / maxProfit * grow.value).coerceIn(0f, 110f)).let { if (day.profit > 0) it.coerceAtLeast(4f) else 0f }
                                                Box(
                                                    Modifier.width(28.dp).height(barH.dp)
                                                        .background(
                                                            if (isMax) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                                        )
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
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
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Batang biru pekat = laba tertinggi • Label hari ini ditebalkan",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- Pengingat ----------

@Composable
fun PengingatScreen(onGoStok: () -> Unit) {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var window by remember { mutableStateOf(90) }
    var filter by remember { mutableStateOf("all") } // all | expiry | low | critical
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    val expired = medicines.filter { (daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE) < 0 }
    val withinWindow = medicines.filter { val d = daysUntilExpiry(it.expiryDate); d != null && d >= 0 && d <= window }
    val low = medicines.filter { it.stock <= it.minStock }
    val critical = medicines.filter { it.stock == 0 }
    val priority = medicines.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        m.stock <= m.minStock || (d != null && d <= window)
    }.sortedWith(
        compareBy<Medicine> { if (it.stock == 0) 0 else 1 }
            .thenBy { daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE }
            .thenBy { it.stock }
    )
    val shown = priority.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        when (filter) {
            "expiry" -> d != null && d <= window
            "low" -> m.stock <= m.minStock
            "critical" -> m.stock == 0
            else -> true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Pengingat", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Kadaluwarsa & stok menipis digabung jadi satu daftar prioritas, diurutkan dari yang paling mendesak. Daftar ini otomatis mengikuti data di Stok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            DemoBanner()
            Text("MASA KADALUARSA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { w ->
                    FilterChip(selected = window == w, onClick = { window = w }, label = { Text("$w Hari") })
                }
            }
        }
        item {
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp)) {
                    ReminderCount(Icons.Default.EventBusy, "EXPIRED", "${expired.size}", if (expired.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                    ReminderCount(Icons.Default.Schedule, "≤ $window HARI", "${withinWindow.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ReminderCount(Icons.Default.Inventory, "STOK NIPIS", "${low.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ReminderCount(Icons.Default.PriorityHigh, "KRITIS", "${critical.size}", if (critical.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("all" to "Semua Prioritas", "expiry" to "Kadaluwarsa", "low" to "Stok Menipis", "critical" to "Kritis Saja").forEach { (k, l) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                }
            }
        }
        itemsIndexed(shown) { index, m ->
            val d = daysUntilExpiry(m.expiryDate)
            val isCritical = m.stock == 0
            val suggest = (2 * m.minStock - m.stock).coerceAtLeast(0)
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isCritical) RedBadgeBg else MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(
                                if (isCritical) Icons.Default.ShoppingCart else Icons.Default.Inventory, null,
                                tint = if (isCritical) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = if (isCritical) RedBadgeBg else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = if (isCritical) Modifier.pulseKritis() else Modifier) {
                                    Text(
                                        if (isCritical) "KRITIS" else "PANTAU",
                                        color = if (isCritical) Color.White else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                "${m.category} • Stok ${m.stock} ${m.unit} (min. ${m.minStock})" +
                                    if (m.expiryDate != null) {
                                        " • Exp ${formatDateID(m.expiryDate)}" + if (d != null) {
                                            if (d < 0) " • Lewat ${-d} hari" else " • Sisa $d hari"
                                        } else ""
                                    } else " • Tanpa tanggal kadaluarsa",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onGoStok, shape = RoundedCornerShape(32.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Inventory, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stok")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isCritical) "Stok habis — restock segera, disarankan $suggest ${m.unit} (target 2× stok minimum)."
                            else if (m.stock <= m.minStock) "Stok menipis — disarankan restock $suggest ${m.unit} (target 2× stok minimum ${m.minStock})."
                            else if (d != null && d < 0) "Sudah kadaluarsa — jangan dijual, segera pisahkan dari stok."
                            else "Kedaluwarsa dalam ${d ?: 0} hari — prioritaskan penjualan.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (index < shown.size - 1) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            }
            }
        }
        if (shown.isEmpty()) {
            item { Text("Tidak ada pengingat — semua stok dan kadaluarsa aman.", style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Text(
                "Jumlah restock adalah saran otomatis (target stok 2× stok minimum). Untuk mengubah stok, tanggal kadaluarsa, atau stok minimum, buka obatnya di menu Stok.",
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
        Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(4.dp))
            Text(value, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "APOTEK LESTARI", color = InvoiceBlue,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Invoice Penjualan", color = InvoiceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = InvoicePaidBg, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = InvoicePaidFg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("LUNAS", color = InvoicePaidFg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DashedDivider()
            Spacer(Modifier.height(16.dp))
            InvoiceInfoBox("NO. INVOICE", sale.invoiceNo, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            InvoiceInfoBox("TANGGAL", formatFullDateID(sale.createdAt), Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InvoiceInfoBox("METODE BAYAR", sale.paymentMethod, Modifier.weight(1f))
                InvoiceInfoBox("ISI INVOICE", "${sale.items.size} item", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("RINCIAN OBAT", color = InvoiceMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
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
                        Text("${it.qty} × ${formatIDR(it.price)}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatIDR(it.subtotal), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal (${sale.items.size} item)", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIDR(sale.total), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (sale.paymentMethod == "Tunai") {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dibayar", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIDR(sale.amountPaid), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sale.changeAmount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(formatIDR(sale.changeAmount), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = InvoiceDash)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Bayar", color = InvoiceInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(sale.total), color = InvoiceBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Tombol di bawah struk: tangkap kartu invoice jadi gambar, lalu bagikan atau cetak.
// Tombolnya sengaja di luar kartu struk supaya tidak ikut masuk ke gambar struk.
@Composable
private fun ReceiptActionsRow(graphicsLayer: GraphicsLayer, invoiceNo: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
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
            shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f)
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
            shape = RoundedCornerShape(32.dp), modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cetak")
        }
    }
}

// ---------- Riwayat Penjualan ----------



@Composable
fun RiwayatScreen() {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    val receiptLayer = rememberGraphicsLayer()
    LaunchedEffect(Unit) { (repo.listSales() as? ApiResult.Success)?.let { sales = it.data.sales } }
    LaunchedEffect(sales) { if (sales.isNotEmpty() && !listEntered) listEntered = true }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Riwayat Penjualan", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Ketuk invoice untuk melihat detail lengkapnya.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        DemoBanner()
        if (sales.isEmpty()) Text("Belum ada penjualan tercatat.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(sales) { index, s ->
                val isOpen = expanded == s.id
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)).clickable { expanded = if (isOpen) null else s.id }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatDateTimeID(s.createdAt)} • ${s.paymentMethod} • ${s.items.size} item",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatIDR(s.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Icon(
                                if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isOpen) {
                            Spacer(Modifier.height(16.dp))
                            InvoiceDetailCard(sale = s, graphicsLayer = receiptLayer)
                            Spacer(Modifier.height(12.dp))
                            ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = s.invoiceNo)
                        }
                    }
                }
                }
            }
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
        Text("Backup & Restore", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Simpan dan pulihkan semua data apotek.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Kenapa backup itu penting?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Semua data (obat, penjualan, kas) hanya tersimpan di HP ini. Kalau HP hilang, rusak, atau aplikasi terhapus, data tidak bisa dikembalikan. Simpan file backup di tempat aman (mis. Google Drive atau laptop) secara berkala.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Backup Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Simpan salinan semua data ke file .json", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
                        backupLauncher.launch("backup-apotek-lestari-$stamp.json")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueButton, contentColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Backup Data") }
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpressiveCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Restore Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pulihkan data dari file backup .json", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Perhatian: restore akan MENGGANTI semua data saat ini dengan isi file backup.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Restore Data") }
            }
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
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
