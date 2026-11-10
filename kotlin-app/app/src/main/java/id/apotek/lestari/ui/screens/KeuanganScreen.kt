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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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

// Layar Keuangan dan formulir Catat Kas sebaris (CS4).

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
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
            // Kartu Saldo Kas — warna gradasi biru di-revert ke kartu datar seperti semula;
            // bar proporsi + compact dipertahankan. Redesain Varian 1 (pilihan
            // Achmad 2026-11-04): tombol Catat Kas yang dulu berdiri sendiri di
            // atas kartu kini menyatu sebagai baris bawah kartu ini.
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
                    // Baris bawah kartu: tombol Catat Kas (menyatu, Varian 1).
                    Spacer(Modifier.height(SpaceM))
                    Button(
                        onClick = { if (showForm) showForm = false else { formType = "in"; showForm = true } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (showForm) {
                            Icon(Icons.Default.ExpandLess, null); Spacer(Modifier.width(8.dp)); Text(tr("Sembunyikan", "Hide"), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Catat Kas", "Record Cash"), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            // Formulir Catat Kas sebaris (CS4, pilihan Achmad 2026-11-04):
            // mengembang tepat di bawah kartu Saldo Kas — bukan bottom sheet
            // lagi. Setelah tersimpan, formulir terlipat dan daftar di bawah
            // memuat entri baru lewat reload() yang sama seperti sebelumnya.
            if (showForm) {
                Spacer(Modifier.height(SpaceL))
                CashEntryFormCard(type = formType, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
            }
            Spacer(Modifier.height(SpaceL))
            // Grafik arus kas — judul + isi mengikuti periode yang dipilih.
            // Varian 1: batang dipadatkan (maks 56dp, dulu 110dp) + label nilai per hari.
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
                                    // Label nilai total hari di atas batang (grafik
                                    // ringkas Varian 1); ruang label selalu disediakan
                                    // supaya dasar batang tetap sejajar antarhari.
                                    Box(Modifier.height(16.dp), contentAlignment = Alignment.BottomCenter) {
                                        if (dayIn + dayOut > 0) {
                                            Text(
                                                formatCompactIDR(dayIn + dayOut),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                                        key(period, periodIn, periodOut) {
                                            val grow = remember { Animatable(0f) }
                                            // Jepit jeda animasi supaya grafik panjang
                                            // tidak butuh waktu lama untuk selesai tumbuh.
                                            LaunchedEffect(period, periodIn, periodOut) { grow.animateTo(1f, tween(durationMillis = 600, delayMillis = (barIndex * 70).coerceAtMost(500), easing = FastOutSlowInEasing)) }
                                            val inH = ((56f * dayIn / maxFlow * grow.value).coerceIn(0f, 56f)).let { if (dayIn > 0) it.coerceAtLeast(4f) else 0f }
                                            val outH = ((56f * dayOut / maxFlow * grow.value).coerceIn(0f, 56f)).let { if (dayOut > 0) it.coerceAtLeast(4f) else 0f }
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


@Composable
fun CashEntryFormCard(type: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
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
    // CS4 (pilihan Achmad 2026-11-04): bukan bottom sheet lagi — formulir
    // ini kartu yang mengembang di halaman Keuangan, tepat di bawah kartu
    // Saldo Kas. Judul + tombol X lembar lama dibuang; tombol di kartu Saldo
    // menjadi pengalih buka/tutup dan tombol Batal menutup formulir.
    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
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
