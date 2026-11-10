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

// Layar Laporan Laba dan Obat Terlaris.

@Composable
fun LabaScreen() {
    var report by remember { mutableStateOf<ProfitReport?>(null) }
    // Pesan kegagalan muat (audit 2026-11-02, M2): tanpa ini, kegagalan
    // laporan membuat LoadingIndicator berputar selamanya.
    var loadError by remember { mutableStateOf<String?>(null) }
    var listEntered by remember { mutableStateOf(!klaimAnimasiMasuk("laba")) }
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
            // Loading tidak pernah dihasilkan repo (call() hanya Success/
            // Error); cabang ini hanya untuk memenuhi exhaustive 'when'.
            is ApiResult.Loading -> Unit
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
                    slideInVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) { it / 3 },
                exit = fadeOut()
            ) {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Laba", "Profit"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
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
                        slideInVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) { it / 3 },
                    exit = fadeOut()
                ) {
                // Desain LP4 (pilihan Achmad 2026-11-04): DUA kartu hero
                // sejajar, biru primer dan hijau. Sejak Harga Beli dihapus
                // total (2026-11-10) kartu biru berisi PENJUALAN (bukan lagi
                // Laba Kotor) dan kartu hijau LABA BERSIH = Penjualan -
                // Biaya Operasional. Angka formatIDR penuh lewat
                // AnimatedAmount (headlineSmall supaya muat).
                val netMarginPct = if (r.revenue > 0) r.netProfit * 100.0 / r.revenue else 0.0
                val bersihDark = LocalIsDarkTheme.current
                val bersihBg = if (bersihDark) Color(0xFF163428) else Color(0xFFDFF2E4)
                val bersihOn = if (bersihDark) Color(0xFFF0FAF3) else Color(0xFF1B4332)
                val bersihLabel = if (bersihDark) Color(0xFFA0DCC0) else Color(0xFF2E7D32)
                val bersihAmount = if (r.netProfit < 0) MaterialTheme.colorScheme.error else bersihOn
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f).animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(tr("PENJUALAN", "REVENUE"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceS))
                            AnimatedAmount(r.revenue, MaterialTheme.typography.headlineSmall, Color.White)
                            Spacer(Modifier.height(SpaceXS))
                            Text(tr("${r.itemsSold} item terjual", "${r.itemsSold} items sold"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = bersihBg, modifier = Modifier.weight(1f).animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(tr("LABA BERSIH", "NET PROFIT"), color = bersihLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceS))
                            AnimatedAmount(r.netProfit, MaterialTheme.typography.headlineSmall, bersihAmount)
                            Spacer(Modifier.height(SpaceXS))
                            Text("Margin ${marginTextID(netMarginPct)}%", color = bersihLabel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) { it / 3 },
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        // Rincian LP4: baris label-nilai (Penjualan,
                        // Biaya Operasional). Laba Bersih tidak diulang di
                        // sini, sudah naik ke kartu hero hijau.
                        val detailRows = listOf(
                            tr("Penjualan", "Revenue") to formatIDR(r.revenue),
                            tr("Biaya Operasional", "Operating Costs") to formatIDR(r.operationalExpense)
                        )
                        detailRows.forEachIndexed { index, (label, value) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (index < detailRows.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                            }
                        }
                        Spacer(Modifier.height(SpaceM))
                        Text(tr("${r.transactionCount} transaksi \u2022 ${r.itemsSold} item terjual", "${r.transactionCount} transactions \u2022 ${r.itemsSold} items sold"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(4), FastOutSlowInEasing)) { it / 3 },
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        // Judul grafik mengikuti periode; "Hari Ini" menampilkan
                        // 7 hari terakhir (judulnya pun demikian).
                        val chartTitle = when (period) {
                            "today" -> tr("Laba Bersih 7 Hari Terakhir", "Net Profit - Last 7 Days")
                            "month" -> tr("Laba Bersih Bulan Ini", "Net Profit - This Month")
                            "last30" -> tr("Laba Bersih 30 Hari Terakhir", "Net Profit - Last 30 Days")
                            else -> tr("Laba Bersih Semua", "Net Profit - All Time")
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


// ---------- Obat Terlaris ----------


@Composable
fun ObatTerlarisScreen() {
    var sellers by remember { mutableStateOf<List<BestSeller>?>(null) }
    // Pesan kegagalan muat (audit 2026-11-02, M2): tanpa ini, kegagalan
    // tampil sebagai daftar kosong tanpa penjelasan.
    var loadError by remember { mutableStateOf<String?>(null) }
    var listEntered by remember { mutableStateOf(!klaimAnimasiMasuk("terlaris")) }
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
            is ApiResult.Loading -> Unit
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
                Text(tr("Terlaris", "Best Sellers"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
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
                                        slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 },
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
