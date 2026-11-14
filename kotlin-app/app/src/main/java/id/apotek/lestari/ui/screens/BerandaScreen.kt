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
import id.apotek.lestari.StatusSync
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

// Layar Beranda.

@Composable
fun BerandaScreen(onGoStok: () -> Unit, onGoPengingat: () -> Unit, onGoLaba: () -> Unit, onGoRiwayat: () -> Unit, statusSync: StatusSync, onSyncTap: () -> Unit) {
    val gelap = LocalIsDarkTheme.current
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    // Periode panel ringkasan BR5 (pilihan Achmad 2026-11-04):
    // 0 = Hari Ini, 1 = Bulan Ini.
    var summaryPeriod by remember { mutableIntStateOf(0) }
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
            .mapNotNull { med -> med.expiryDate?.let { exp -> med.name to exp } }
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
            
            Column {
            // Header N2 (pilihan Achmad 2026-11-10, menggantikan K3):
            // MODE GELAP tetap batang navy nyaris hitam, logo dalam kotak
            // putih, nama satu baris putih. MODE LIGHT = H6 (pilihan
            // Achmad 2026-11-12): kartu biru muda #D3E3F3, nama navy,
            // tombol awan berbingkai biru gelap #0284C7 (merah #B3261E
            // saat GAGAL) supaya tetap terbaca di atas kartu terang.
            // Kanan = tombol awan saja (tanpa tulisan status - permintaan
            // Achmad) yang tetap menjadi tombol sinkron. Tanggal tidak
            // di header; kartu ini adalah header Beranda.
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
                    val logo = rememberLogoBitmap()
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(46.dp)) {
                        if (logo != null) {
                            Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp).fillMaxSize())
                        } else {
                            Icon(Icons.Default.LocalPharmacy, null, tint = Color(0xFF16324F), modifier = Modifier.padding(11.dp).fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Apotek Lestari", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (gelap) Color.White else WarnaHeaderN2)
                    Spacer(Modifier.weight(1f))
                    if (statusSync != StatusSync.NONAKTIF) {
                        Surface(
                            color = Color.Transparent,
                            shape = CircleShape,
                            border = BorderStroke(
                                1.5.dp,
                                if (statusSync == StatusSync.GAGAL) (if (gelap) Color(0xFFFFB4AB) else Color(0xFFB3261E)) else (if (gelap) Color(0xFF40C4FF) else Color(0xFF0284C7))
                            ),
                            modifier = Modifier.size(38.dp).clickable { onSyncTap() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (statusSync) {
                                    StatusSync.PROSES -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = if (gelap) Color(0xFF40C4FF) else Color(0xFF0284C7))
                                    StatusSync.BERHASIL -> Icon(Icons.Default.CloudDone, tr("Tersinkron", "Synced"), tint = if (gelap) Color(0xFF40C4FF) else Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                                    StatusSync.GAGAL -> Icon(Icons.Default.CloudOff, tr("Gagal", "Failed"), tint = if (gelap) Color(0xFFFFB4AB) else Color(0xFFB3261E), modifier = Modifier.size(20.dp))
                                    else -> Icon(Icons.Default.CloudOff, tr("Mode Tamu", "Guest mode"), tint = if (gelap) Color.White else WarnaHeaderN2, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(SpaceS))
            DemoBanner()
            }
            
        }
        dashboard?.let { d ->
            item {
                
                // Panel ringkasan BR5 (pilihan Achmad 2026-11-04): hero
                // Pemasukan dan kartu uang lama digabung menjadi SATU
                // panel bertab periode Hari Ini / Bulan Ini, supaya angka
                // harian dan bulanan tidak tampil berulang dalam dua kartu
                // terpisah. Pemilih periode memakai dua FilterChip
                // terpisah (T1, pilihan Achmad 2026-11-14), bukan segmen
                // gabung lagi.
                val monthMode = summaryPeriod == 1
                val diff = d.todayIn - d.yesterdayIn
                val panelIn = if (monthMode) d.monthIn else d.todayIn
                val panelOut = if (monthMode) d.monthOut else d.todayOut
                val panelProfit = if (monthMode) d.monthProfit else d.todayProfit
                val panelSales = if (monthMode) d.totalSalesMonth else d.totalSalesToday
                Column {
                    // T1 bentuk pil terpisah (pilihan Achmad 2026-11-14),
                    // dengan ukuran besar + warna navy solid dari papan
                    // T7–T12 (14 Nov): pil 64dp, teks 19sp; pil terpilih
                    // navy #1B385C + teks putih + garis cyan — menggantikan
                    // primary biru muda yang terlihat pucat di mode gelap.
                    // Bentuk diubah 14 Nov (permintaan Achmad): kotak
                    // bersudut membulat RoundedCornerShape(20.dp), SAMA
                    // seperti kartu pintasan Laba/Pengingat/Riwayat
                    // (ShortcutCard) — bukan lagi pill penuh.
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                        listOf(tr("Hari Ini", "Today"), tr("Bulan Ini", "This Month")).forEachIndexed { index, label ->
                            val selected = summaryPeriod == index
                            FilterChip(
                                selected = selected,
                                onClick = { summaryPeriod = index },
                                label = {
                                    Text(
                                        label, maxLines = 1,
                                        fontSize = 19.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1B385C),
                                    selectedLabelColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF40C4FF) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.weight(1f).height(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(SpaceM))
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth()
                            
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (monthMode) tr("PEMASUKAN BULAN INI", "THIS MONTH'S INCOME") else tr("PEMASUKAN HARI INI", "TODAY'S INCOME"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                    Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp).size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(SpaceS))
                            AnimatedAmount(panelIn, MaterialTheme.typography.headlineLarge, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(SpaceXS))
                            if (!monthMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when {
                                        diff > 0 -> {
                                            Icon(Icons.Default.TrendingUp, null, tint = profitGreen(), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("Naik ${formatIDR(diff)} dari kemarin", "Up ${formatIDR(diff)} from yesterday"), color = profitGreen(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                        diff < 0 -> {
                                            Icon(Icons.Default.TrendingDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("Turun ${formatIDR(-diff)} dari kemarin", "Down ${formatIDR(-diff)} from yesterday"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                        else -> {
                                            Text(tr("Sama seperti kemarin", "Same as yesterday"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            } else {
                                Text(tr("Penjualan pada bulan berjalan", "Sales in the current month"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(SpaceM))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(SpaceM))
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Pengeluaran", "Expenses"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text(formatIDR(panelOut), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Selisih", "Difference"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text(formatIDR(panelProfit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (panelProfit >= 0) profitGreen() else MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Transaksi", "Transactions"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text("$panelSales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                
            }
            item {
                
                // Shortcut ke menu yang tidak lagi ada di bar bawah (Laba, Pengingat, Riwayat).
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard(label = tr("Laba", "Profit"), icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f), onClick = onGoLaba)
                    ShortcutCard(label = tr("Pengingat", "Reminders"), icon = Icons.Default.NotificationsActive, modifier = Modifier.weight(1f), onClick = onGoPengingat)
                    ShortcutCard(label = tr("Riwayat", "History"), icon = Icons.Default.ReceiptLong, modifier = Modifier.weight(1f), onClick = onGoRiwayat)
                }
                
            }
            item {
                
                // BR1 (pilihan Achmad 2026-11-14): kartu Pengingat
                // dipadatkan jadi spanduk ringkas — garis aksen kuning di
                // kiri, ringkasan jumlah per kelompok + obat/tagihan
                // terdekat, dan tautan "Lihat Pengingat >" ke layar penuh.
                // Isi lengkap tetap ada di layar Pengingat.
                val reminderParts = buildList {
                    if (outOfStockNames.isNotEmpty()) add(tr("${outOfStockNames.size} obat habis", "${outOfStockNames.size} medicines out of stock"))
                    if (lowStockMeds.isNotEmpty()) add(tr("${lowStockMeds.size} obat stok menipis", "${lowStockMeds.size} medicines low on stock"))
                    if (d.expiringCount > 0) add(tr("${d.expiringCount} obat dekat kadaluarsa", "${d.expiringCount} medicines expiring soon"))
                    if (dueBills.isNotEmpty()) add(tr("${dueBills.size} tagihan PBF jatuh tempo", "${dueBills.size} PBF bills due"))
                }
                val hasReminders = reminderParts.isNotEmpty()
                val reminderSummary = if (hasReminders) reminderParts.joinToString(" • ") else tr("Semua stok dan kadaluarsa aman.", "All stock and expiry dates are safe.")
                val reminderDetail: String? = when {
                    expiringMeds.isNotEmpty() -> {
                        val (nm, exp) = expiringMeds.first()
                        "$nm • ${formatDateID(exp)}"
                    }
                    lowStockMeds.isNotEmpty() -> {
                        val (nm, st) = lowStockMeds.first()
                        "$nm • ${tr("Sisa: $st", "Left: $st")}"
                    }
                    outOfStockNames.isNotEmpty() -> "${outOfStockNames.first()} • ${tr("Habis", "Out of stock")}"
                    dueBills.isNotEmpty() -> {
                        val p = dueBills.first()
                        val daysLeft = dueDaysLeft(p.dueDate) ?: 0L
                        val dueLabel = when {
                            daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue")
                            daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today")
                            daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow")
                            else -> tr("$daysLeft hari lagi", "Due in $daysLeft days")
                        }
                        "${p.pbfName} • $dueLabel"
                    }
                    else -> null
                }
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(warningAmber()))
                        Column(Modifier.weight(1f).padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = warningAmber(), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(reminderSummary, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
                            }
                            if (reminderDetail != null) {
                                Spacer(Modifier.height(SpaceXS))
                                Text(reminderDetail, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
                            }
                            if (hasReminders) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onGoPengingat, contentPadding = PaddingValues(0.dp)) {
                                        Text(tr("Lihat Pengingat >", "View Reminders >"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
                                    }
                                }
                            }
                        }
                    }
                }
                
            }
            item {
                
                // BR1 (pilihan Achmad 2026-11-14): kartu Obat Terjual
                // tanpa tab — tab Transaksi dihapus dari Beranda (riwayat
                // lengkap tetap ada lewat pintasan Riwayat).
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Obat Terjual", "Items Sold"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(SpaceS))
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
